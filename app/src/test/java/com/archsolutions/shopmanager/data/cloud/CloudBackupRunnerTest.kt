package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import com.archsolutions.cloud.contract.ArchHeaders
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId

class FixedClock(var now: Long = 1_000L) : AppClock {
    override fun nowMillis() = now
    override fun todayDateString() = "2026-10-24"
    override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
}

private const val STATUS_JSON = """{"plan":"basic","state":"ACTIVE","periodEnd":99,"features":["backup"],"resync":false}"""

@RunWith(RobolectricTestRunner::class)
class CloudBackupRunnerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private lateinit var store: CloudStore
    private lateinit var repo: CloudRepo
    private lateinit var runner: CloudBackupRunner
    private lateinit var zip: File
    private val clock = FixedClock()
    private val job = Job()
    private var newest: File? = null

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val scope = CoroutineScope(Dispatchers.IO + job)
        store = CloudStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { cloudDataStoreFile(context) }),
            FakeTokenCipher(),
        )
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.filesDir, "datastore/s.preferences_pb") }),
        )
        val api = CloudApi(server.url("/"), OkHttpClient(), "1.0.0") { store.token() }
        repo = CloudRepo(store, api, settings, clock, context)
        zip = File(context.cacheDir, "arch-sme_backup_test.zip").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        newest = zip
        runner = CloudBackupRunner(repo, store, clock) { newest }
    }

    @After fun tearDown() {
        server.shutdown()
        runBlocking { job.cancelAndJoin() }
        zip.delete()
        cloudDataStoreFile(context).delete()
    }

    private fun link() = runBlocking { store.saveLink("tok-1", "shop-1", "0700111222") }
    private fun err(http: Int, code: String) =
        MockResponse().setResponseCode(http).setBody("""{"code":"$code","message":"m"}""")

    @Test fun `sent stores last cloud backup time`() {
        link()
        server.enqueue(MockResponse().setBody("""{"backupId":"b1"}"""))
        clock.now = 5_000L
        assertEquals(CloudBackupOutcome.SENT, runBlocking { runner.run() })
        val req = server.takeRequest()
        val expected = MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, req.getHeader(ArchHeaders.CONTENT_SHA256))
        assertEquals(5_000L, runBlocking { store.snapshot.first().lastCloudBackupAt })
    }

    @Test fun `same zip twice is fine`() {
        link()
        server.enqueue(MockResponse().setBody("""{"backupId":"b1"}"""))
        server.enqueue(MockResponse().setBody("""{"backupId":"b1"}"""))
        assertEquals(CloudBackupOutcome.SENT, runBlocking { runner.run() })
        assertEquals(CloudBackupOutcome.SENT, runBlocking { runner.run() })
    }

    @Test fun `connection drop is retry and keeps last time unchanged`() {
        link()
        runBlocking { store.setLastCloudBackup(777L) }
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(CloudBackupOutcome.RETRY, runBlocking { runner.run() })
        assertEquals(777L, runBlocking { store.snapshot.first().lastCloudBackupAt })
    }

    @Test fun `server errors 5xx and 429 are retry`() {
        link()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(err(429, "RATE_LIMITED"))
        assertEquals(CloudBackupOutcome.RETRY, runBlocking { runner.run() })
        assertEquals(CloudBackupOutcome.RETRY, runBlocking { runner.run() })
    }

    @Test fun `402 is paused and refreshes status`() {
        link()
        server.enqueue(err(402, "PLAN_LAPSED"))
        server.enqueue(MockResponse().setBody(STATUS_JSON))
        assertEquals(CloudBackupOutcome.PAUSED, runBlocking { runner.run() })
        assertEquals("/v1/backups", server.takeRequest().path)
        assertEquals("/v1/shop/status", server.takeRequest().path)
        assertEquals("basic", runBlocking { store.snapshot.first().status?.plan })
    }

    @Test fun `401 DEVICE_REVOKED is replaced`() {
        link()
        server.enqueue(err(401, "DEVICE_REVOKED"))
        assertEquals(CloudBackupOutcome.REPLACED, runBlocking { runner.run() })
        val snap = runBlocking { store.snapshot.first() }
        assertTrue(snap.replaced)
        assertFalse(snap.linked)
    }

    @Test fun `401 UNAUTHORIZED signs out but not replaced`() {
        link()
        server.enqueue(err(401, "UNAUTHORIZED"))
        assertEquals(CloudBackupOutcome.SIGNED_OUT, runBlocking { runner.run() })
        val snap = runBlocking { store.snapshot.first() }
        assertFalse(snap.replaced)
        assertFalse(snap.linked)
    }

    @Test fun `invalid or too large or other 4xx is rejected`() {
        link()
        server.enqueue(err(400, "BACKUP_INVALID"))
        server.enqueue(err(413, "TOO_LARGE"))
        server.enqueue(err(426, "UPGRADE_APP"))
        server.enqueue(err(404, "NOT_FOUND"))
        repeat(4) { assertEquals(CloudBackupOutcome.REJECTED, runBlocking { runner.run() }) }
    }

    @Test fun `not linked makes no request`() {
        assertEquals(CloudBackupOutcome.NOT_LINKED, runBlocking { runner.run() })
        assertEquals(0, server.requestCount)
    }

    @Test fun `no zip is nothing to send and makes no request`() {
        link()
        newest = null
        assertEquals(CloudBackupOutcome.NOTHING_TO_SEND, runBlocking { runner.run() })
        assertEquals(0, server.requestCount)
        assertNull(newest)
    }

    @Test fun `only RETRY maps to retry`() {
        CloudBackupOutcome.entries.forEach {
            val expected = if (it == CloudBackupOutcome.RETRY) ListenableWorker.Result.retry() else ListenableWorker.Result.success()
            assertEquals(it.name, expected, it.toWorkResult())
        }
    }
}
