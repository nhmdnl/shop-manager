package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.archsolutions.cloud.contract.PlanState
import com.archsolutions.cloud.contract.ShopStatus
import com.archsolutions.shopmanager.data.cloud.CloudApi
import com.archsolutions.shopmanager.data.cloud.CloudStore
import com.archsolutions.shopmanager.data.cloud.FakeTokenCipher
import com.archsolutions.shopmanager.data.cloud.FixedClock
import com.archsolutions.shopmanager.data.cloud.cloudDataStoreFile
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var store: CloudStore
    private lateinit var db: AppDatabase
    private lateinit var repo: SyncRepo
    private lateinit var marker: FillMarker
    private lateinit var scheduler: SyncScheduler
    private val job = Job()
    private val scope get() = CoroutineScope(Dispatchers.IO + job)
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        store = CloudStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { cloudDataStoreFile(context) }),
            FakeTokenCipher(),
        )
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.filesDir, "datastore/s.preferences_pb") }),
        )
        db = AppDatabase.createInMemory(context)
        repo = SyncRepo(db, settings, FixedClock())
        marker = FillMarker(File(context.filesDir, "marker-test").apply { mkdirs() }).also { it.clear() }
        scheduler = scheduler(CloudApi("http://localhost/".toHttpUrl(), OkHttpClient(), "1.0.0") { store.token() })
    }

    private fun scheduler(api: CloudApi?) = SyncScheduler(context, db.syncDao(), store, repo, marker, api)

    @After fun tearDown() {
        runBlocking { job.cancelAndJoin() }
        db.close()
        marker.clear()
        cloudDataStoreFile(context).delete()
    }

    private fun infos(name: String): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    /** The watchers run on their own threads; wait for the effect, bounded by a timeout (no fixed sleeps). */
    private fun await(what: String, cond: () -> Boolean) = runBlocking {
        try {
            withTimeout(10_000) { while (!cond()) delay(20) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for $what")
        }
    }

    private fun link() = runBlocking { store.saveLink("tok-1", "shop-1", "0700111222") }
    private fun status(vararg features: String) = runBlocking {
        store.saveStatus(ShopStatus(plan = "basic", state = PlanState.ACTIVE, features = features.toList()))
    }

    private fun waitingEntry() = sql.execSQL("INSERT INTO sync_outbox (tbl, uid, op) VALUES ('category', 'c-1', 'UPSERT')")

    @Test fun `runSoon twice keeps one work with a 2 minute delay`() {
        scheduler.runSoon()
        scheduler.runSoon()
        val soon = infos(SyncScheduler.SOON)
        assertEquals(1, soon.size)
        assertEquals(120_000L, soon.single().initialDelayMillis)
    }

    @Test fun `start twice enqueues the periodic work once`() {
        val scope = scope
        scheduler.start(scope)
        scheduler.start(scope)
        val periodic = infos(SyncScheduler.PERIODIC)
        assertEquals(1, periodic.size)
        assertEquals(15 * 60_000L, periodic.single().periodicityInfo!!.repeatIntervalMillis)
    }

    @Test fun `outbox going from empty to waiting enqueues soon`() {
        scheduler.start(scope)
        assertTrue(infos(SyncScheduler.SOON).isEmpty())
        waitingEntry()
        await("cloud-sync-soon") { infos(SyncScheduler.SOON).size == 1 }
        assertEquals(120_000L, infos(SyncScheduler.SOON).single().initialDelayMillis)
    }

    @Test fun `a start with entries already waiting enqueues soon`() {
        waitingEntry()
        scheduler.start(scope)
        await("cloud-sync-soon") { infos(SyncScheduler.SOON).size == 1 }
    }

    @Test fun `the link going away while enabled turns sync off and empties the outbox`() {
        link()
        runBlocking { repo.startFill() }
        waitingEntry()
        scheduler.start(scope)
        runBlocking { store.clearLink() }
        await("sync off") { !runBlocking { repo.isEnabled() } }
        await("outbox empty") { sql.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getLong(0) } == 0L }
    }

    @Test fun `a start without a token turns an enabled sync off`() {
        runBlocking { repo.startFill() }
        scheduler.start(scope)
        await("sync off") { !runBlocking { repo.isEnabled() } }
    }

    @Test fun `runAfter waits at least 30 seconds`() {
        scheduler.runAfter(3600)
        assertEquals(3_600_000L, infos(SyncScheduler.LATER).single().initialDelayMillis)
        scheduler.runAfter(5)
        assertTrue(30_000L in infos(SyncScheduler.LATER).map { it.initialDelayMillis })
    }

    @Test fun `runNow has no delay`() {
        scheduler.runNow()
        assertEquals(0L, infos(SyncScheduler.NOW).single().initialDelayMillis)
    }

    @Test fun `live on the first value with sync off runs now`() {
        link(); status("backup", "live")
        scheduler.start(scope)
        await("cloud-sync-now") { infos(SyncScheduler.NOW).size == 1 }
    }

    @Test fun `live on the first value with sync already on and no marker does not run now`() {
        link(); status("backup", "live")
        runBlocking { repo.startFill() }
        scheduler.start(scope)
        await("watchers up") { infos(SyncScheduler.PERIODIC).size == 1 }
        // Give the live watcher its first value: a later change is the proof it was listening.
        status("backup")
        status("backup", "live")
        await("cloud-sync-now") { infos(SyncScheduler.NOW).size == 1 }
    }

    @Test fun `live on the first value with a restore marker runs now`() {
        link(); status("backup", "live")
        runBlocking { repo.startFill() }
        marker.set()
        scheduler.start(scope)
        await("cloud-sync-now") { infos(SyncScheduler.NOW).size == 1 }
    }

    @Test fun `live turning on later runs now`() {
        link(); status("backup")
        scheduler.start(scope)
        await("watchers up") { infos(SyncScheduler.PERIODIC).size == 1 }
        status("backup", "live")
        await("cloud-sync-now") { infos(SyncScheduler.NOW).size == 1 }
    }

    @Test fun `with no CloudApi nothing is enqueued`() {
        val off = scheduler(null)
        waitingEntry()
        off.start(scope)
        off.runSoon(); off.runNow(); off.runAfter(60)
        for (name in listOf(SyncScheduler.PERIODIC, SyncScheduler.SOON, SyncScheduler.NOW, SyncScheduler.LATER)) {
            assertTrue(name, infos(name).isEmpty())
        }
        assertFalse(runBlocking { repo.isEnabled() })
    }
}
