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
import com.archsolutions.shopmanager.data.cloud.CloudRepo
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
import kotlinx.coroutines.flow.first
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
    private lateinit var settings: SettingsStore
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
        this.settings = settings
        marker = FillMarker(File(context.filesDir, "marker-test").apply { mkdirs() }).also { it.clear() }
        scheduler = scheduler(CloudApi("http://localhost/".toHttpUrl(), OkHttpClient(), "1.0.0") { store.token() })
    }

    private fun scheduler(api: CloudApi?): SyncScheduler {
        val clock = FixedClock()
        val runner = SyncRunner(CloudRepo(store, api, settings, clock, context), store, repo, marker, clock)
        return SyncScheduler(context, db.syncDao(), store, repo, runner, marker, api)
    }

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

    /** Starts the watchers and waits until each has handled its first value: a real barrier, not a flag set before collection. */
    private fun startAndSettle(s: SyncScheduler = scheduler) {
        s.start(scope)
        runBlocking {
            withTimeout(10_000) {
                s.outboxValuesHandled.first { it >= 1 }
                s.liveValuesHandled.first { it >= 1 }
            }
        }
    }

    private fun link() = runBlocking { store.saveLink("tok-1", "shop-1", "0700111222") }
    private fun status(vararg features: String) = runBlocking {
        store.saveStatus(ShopStatus(plan = "basic", state = PlanState.ACTIVE, features = features.toList()))
    }

    private fun waitingEntry() {
        sql.execSQL("INSERT INTO sync_outbox (tbl, uid, op) VALUES ('category', 'c-1', 'UPSERT')")
        db.invalidationTracker.refreshVersionsAsync() // a raw insert doesn't tell Room's flows
    }

    @Test fun `runSoon twice keeps the first work (KEEP) with a 2 minute delay`() {
        scheduler.runSoon()
        val first = infos(SyncScheduler.SOON).single()
        assertEquals(120_000L, first.initialDelayMillis)
        scheduler.runSoon()
        val second = infos(SyncScheduler.SOON).single()
        assertEquals(first.id, second.id)
    }

    private val allWorks = listOf(SyncScheduler.PERIODIC, SyncScheduler.SOON, SyncScheduler.NOW, SyncScheduler.LATER)

    private fun live(name: String) = infos(name).filter { !it.state.isFinished }

    @Test fun `start twice launches the watchers once and enqueues the periodic work while linked`() {
        link()
        val scope = scope
        scheduler.start(scope)
        scheduler.start(scope)
        assertEquals(3, scheduler.collectorsLaunched.get())
        await("cloud-sync") { infos(SyncScheduler.PERIODIC).isNotEmpty() }
        val periodic = infos(SyncScheduler.PERIODIC)
        assertEquals(1, periodic.size)
        assertEquals(15 * 60_000L, periodic.single().periodicityInfo!!.repeatIntervalMillis)
    }

    @Test fun `an unlinked tablet gets no periodic work`() {
        startAndSettle()
        assertTrue(infos(SyncScheduler.PERIODIC).isEmpty())
    }

    @Test fun `linking later enqueues the periodic work, and unlinking cancels all four works`() {
        startAndSettle()
        assertTrue(infos(SyncScheduler.PERIODIC).isEmpty())
        link()
        await("cloud-sync") { live(SyncScheduler.PERIODIC).size == 1 }
        scheduler.runSoon(); scheduler.runNow(); scheduler.runAfter(60)
        for (name in allWorks) assertEquals(name, 1, live(name).size)

        runBlocking { store.clearLink() }
        await("all four cancelled") { allWorks.all { live(it).isEmpty() } }
    }

    @Test fun `outbox going from empty to waiting enqueues soon`() {
        startAndSettle() // the first value (empty) is handled, so what follows is a real false to true
        assertTrue(infos(SyncScheduler.SOON).isEmpty())
        waitingEntry()
        await("cloud-sync-soon") { infos(SyncScheduler.SOON).size == 1 }
        assertEquals(120_000L, infos(SyncScheduler.SOON).single().initialDelayMillis)
    }

    @Test fun `a start with entries already waiting enqueues soon from the first value`() {
        link()
        waitingEntry()
        scheduler.start(scope)
        await("cloud-sync-soon") { infos(SyncScheduler.SOON).size == 1 }
    }

    @Test fun `the link going away while enabled turns sync off and empties the outbox`() {
        link()
        runBlocking { repo.startFill() }
        waitingEntry()
        startAndSettle()
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
        startAndSettle()
        assertEquals(1, infos(SyncScheduler.NOW).size)
    }

    @Test fun `live on the first value with sync already on and no marker does not run now`() {
        link(); status("backup", "live")
        runBlocking { repo.startFill() }
        startAndSettle()
        assertTrue(infos(SyncScheduler.NOW).isEmpty())
    }

    @Test fun `live on the first value with a restore marker runs now`() {
        link(); status("backup", "live")
        runBlocking { repo.startFill() }
        marker.set()
        startAndSettle()
        assertEquals(1, infos(SyncScheduler.NOW).size)
    }

    @Test fun `live on the first value on a replaced or unlinked tablet runs nothing`() {
        link(); status("backup", "live")
        runBlocking { store.markReplaced() } // keeps the cached status, drops the token
        startAndSettle()
        assertTrue(infos(SyncScheduler.NOW).isEmpty())
    }

    @Test fun `live turning on later runs now`() {
        link(); status("backup")
        runBlocking { repo.startFill() } // sync on, so the first-value rule can't be what fires
        startAndSettle()
        assertTrue(infos(SyncScheduler.NOW).isEmpty())
        status("backup", "live")
        await("cloud-sync-now") { infos(SyncScheduler.NOW).size == 1 }
    }

    @Test fun `shouldRunNow table`() {
        // previous, live, linked, enabled, marker -> expected
        assertTrue(shouldRunNow(null, true, true, false, false))
        assertTrue(shouldRunNow(null, true, true, true, true))
        assertFalse(shouldRunNow(null, true, true, true, false))
        assertFalse(shouldRunNow(null, true, false, false, false))
        assertFalse(shouldRunNow(null, false, true, false, true))
        assertTrue(shouldRunNow(false, true, true, true, false))
        assertFalse(shouldRunNow(true, true, true, false, false))
        assertFalse(shouldRunNow(false, true, false, false, false))
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

    @Test fun `with no CloudApi an enabled sync is turned off once and nothing is scheduled`() {
        runBlocking { repo.startFill() } // a restore from a synced tablet
        val off = scheduler(null)
        off.start(scope)
        off.start(scope)
        await("sync off") { !runBlocking { repo.isEnabled() } }
        assertEquals(0, off.collectorsLaunched.get())
        assertTrue(infos(SyncScheduler.PERIODIC).isEmpty())
    }

    @Test fun `with no CloudApi the works an earlier build left are cancelled`() {
        scheduler.runSoon(); scheduler.runNow(); scheduler.runAfter(60)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncScheduler.PERIODIC, androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build(),
        ).result.get()
        for (name in allWorks) assertEquals(name, 1, live(name).size)

        scheduler(null).start(scope)
        await("all four cancelled") { allWorks.all { live(it).isEmpty() } }
    }
}
