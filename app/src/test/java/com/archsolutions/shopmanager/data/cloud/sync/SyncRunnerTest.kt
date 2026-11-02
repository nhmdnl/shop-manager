package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.cloud.contract.ArchJson
import com.archsolutions.cloud.contract.shop.SyncBatch
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.ArrayDeque

@RunWith(RobolectricTestRunner::class)
class SyncRunnerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private lateinit var store: CloudStore
    private lateinit var db: AppDatabase
    private lateinit var cloud: CloudRepo
    private lateinit var repo: SyncRepo
    private lateinit var marker: FillMarker
    private lateinit var runner: SyncRunner
    private val clock = FixedClock(9_000L)
    private val job = Job()
    private val lock = Any()

    // What the fake server does; tests change these.
    private var features = """"backup","live""""
    private var resync = false
    private val statusOverrides = ArrayDeque<MockResponse>()
    private val batchOverrides = ArrayDeque<MockResponse>()
    private val posted = mutableListOf<SyncBatch>()
    private var statusCalls = 0

    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    @Before fun setUp() {
        val scope = CoroutineScope(Dispatchers.IO + job)
        store = CloudStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { cloudDataStoreFile(context) }),
            FakeTokenCipher(),
        )
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.filesDir, "datastore/s.preferences_pb") }),
        )
        db = AppDatabase.createInMemory(context)
        repo = SyncRepo(db, settings, clock)
        marker = FillMarker(File(context.filesDir, "marker-test").apply { mkdirs() })
        marker.clear()
        server = MockWebServer().also {
            it.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = synchronized(lock) {
                    when (request.path) {
                        "/v1/shop/status" -> {
                            statusCalls++
                            statusOverrides.poll() ?: MockResponse().setBody(
                                """{"plan":"basic","state":"ACTIVE","periodEnd":99,"features":[$features],"resync":$resync}""",
                            )
                        }
                        "/v1/sync/batch" -> {
                            val batch = ArchJson.decodeFromString(SyncBatch.serializer(), request.body.readUtf8())
                            posted += batch
                            batchOverrides.poll() ?: MockResponse().setBody("""{"ackSeq":${batch.toSeq},"rejected":[]}""")
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            it.start()
        }
        cloud = CloudRepo(store, api(), settings, clock, context)
        runner = SyncRunner(cloud, store, repo, marker, clock)
    }

    private fun api() = CloudApi(server.url("/"), OkHttpClient.Builder().retryOnConnectionFailure(false).build(), "1.0.0") { store.token() }

    @After fun tearDown() {
        server.shutdown()
        db.close()
        marker.clear()
        runBlocking { job.cancelAndJoin() }
        cloudDataStoreFile(context).delete()
    }

    private fun link() = runBlocking { store.saveLink("tok-1", "shop-1", "0700111222") }
    private fun run() = runBlocking { runner.run() }
    private fun err(http: Int, code: String, retryAfter: Int? = null) =
        MockResponse().setResponseCode(http).setBody("""{"code":"$code","message":"m"}""").also {
            if (retryAfter != null) it.setHeader("Retry-After", retryAfter.toString())
        }

    private fun count(q: String): Long = sql.query(q).use { it.moveToFirst(); it.getLong(0) }
    private fun outboxCount() = count("SELECT COUNT(*) FROM sync_outbox")
    private fun state() = runBlocking { db.syncDao().state() }

    /** A day plus [n] money entries: [n] + 1 rows for a fill (sync is off until a fill starts, so no triggers fire). */
    private fun rows(n: Int) {
        sql.execSQL("INSERT INTO business_day (id, date, status, openedAt, openingCash, openingMobile, reopenedCount, uid) VALUES (1, '2026-09-01', 'OPEN', 0, 0, 0, 0, 'day-1')")
        sql.beginTransaction()
        try {
            for (i in 1..n) sql.execSQL("INSERT INTO money_entry (dayId, createdAt, type, method, amount, uid) VALUES (1, $i, 'EXPENSE', 'CASH', 1, 'm-$i')")
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
    }

    @Test fun `sends a fill and acknowledges it`() {
        link(); rows(1_200)
        assertEquals(SyncOutcome.SENT, run())
        assertTrue(posted.size >= 3)
        assertTrue(posted.first().fillStart)
        assertFalse(posted.drop(1).any { it.fillStart })
        assertTrue(posted.last().fillEnd)
        assertFalse(posted.dropLast(1).any { it.fillEnd })
        assertEquals(1, posted.map { it.fillId }.distinct().size)
        assertEquals(0L, outboxCount())
        assertEquals(9_000L, runBlocking { store.lastSyncAt.first() })
    }

    @Test fun `no live turns sync off`() {
        link(); rows(2)
        runBlocking { repo.startFill() }
        assertEquals(4L, outboxCount())
        features = """"backup""""
        assertEquals(SyncOutcome.OFF, run())
        assertFalse(runBlocking { repo.isEnabled() })
        assertEquals(0L, outboxCount())
        assertTrue(posted.isEmpty())
    }

    @Test fun `revoked during status`() {
        link(); rows(2)
        runBlocking { repo.startFill() }
        statusOverrides += err(401, "DEVICE_REVOKED")
        assertEquals(SyncOutcome.REPLACED, run())
        assertFalse(runBlocking { repo.isEnabled() })
        assertTrue(runBlocking { store.snapshot.first().replaced })
        assertEquals(0L, outboxCount())
    }

    @Test fun `revoked during a batch`() {
        link(); rows(2)
        batchOverrides += err(401, "DEVICE_REVOKED")
        assertEquals(SyncOutcome.REPLACED, run())
        assertFalse(runBlocking { repo.isEnabled() })
        assertTrue(runBlocking { store.snapshot.first().replaced })
        assertEquals(0L, outboxCount())
    }

    @Test fun `unauthorized on a batch clears the link and turns sync off`() {
        link(); rows(2)
        batchOverrides += err(401, "UNAUTHORIZED")
        assertEquals(SyncOutcome.NOT_LINKED, run())
        assertNull(runBlocking { store.token() })
        assertFalse(runBlocking { repo.isEnabled() })
        assertEquals(0L, outboxCount())
    }

    @Test fun `lapsed on a batch keeps the outbox`() {
        link(); rows(2)
        batchOverrides += err(402, "PLAN_LAPSED")
        assertEquals(SyncOutcome.PAUSED, run())
        assertEquals(4L, outboxCount())
        assertTrue(runBlocking { repo.isEnabled() })
    }

    @Test fun `network error keeps the outbox`() {
        link(); rows(2)
        batchOverrides += MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        assertEquals(SyncOutcome.RETRY, run())
        assertEquals(4L, outboxCount())
    }

    @Test fun `reply lost after the server applied keeps the entries`() {
        link(); rows(2)
        batchOverrides += MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        assertEquals(SyncOutcome.RETRY, run())
        assertEquals(4L, outboxCount())
        assertEquals(SyncOutcome.SENT, run())
        assertEquals(0L, outboxCount())
        assertEquals(2, posted.size)
        val (a, b) = posted
        assertEquals(a.batchId, b.batchId)
        assertEquals(a.fromSeq, b.fromSeq)
        assertEquals(a.toSeq, b.toSeq)
        assertEquals(a.fillStart, b.fillStart)
        assertTrue(a.fillStart)
    }

    @Test fun `429 waits for Retry-After and keeps the outbox`() {
        link(); rows(2)
        batchOverrides += err(429, "RATE_LIMITED", retryAfter = 3600)
        assertEquals(SyncOutcome.WAIT, run())
        assertEquals(3600, runner.lastWaitSeconds)
        assertEquals(4L, outboxCount())
        val fillId = state().fillId
        assertEquals(SyncOutcome.SENT, run())
        assertEquals(fillId, posted.last().fillId)
        assertEquals(2, posted.size)
        assertEquals(2, posted.count { it.fillStart }) // the same batch, resent
        assertEquals(posted[0].batchId, posted[1].batchId)
        assertEquals(0L, outboxCount())
    }

    @Test fun `429 without Retry-After waits 60 seconds`() {
        link(); rows(1)
        batchOverrides += err(429, "RATE_LIMITED")
        assertEquals(SyncOutcome.WAIT, run())
        assertEquals(60, runner.lastWaitSeconds)
    }

    @Test fun `a fill spread over two runs keeps its place`() {
        link(); rows(1_199) // 1 day + 1,199 entries + FILL_END = 1,201 outbox entries: batches of 500, 500, 201
        // Scripted by call number: pass the first batch, refuse the second.
        server.dispatcher = object : Dispatcher() {
            private var n = 0
            override fun dispatch(request: RecordedRequest): MockResponse = synchronized(lock) {
                if (request.path == "/v1/shop/status") {
                    statusCalls++
                    return MockResponse().setBody("""{"plan":"basic","state":"ACTIVE","periodEnd":99,"features":[$features],"resync":true}""")
                }
                val batch = ArchJson.decodeFromString(SyncBatch.serializer(), request.body.readUtf8())
                posted += batch
                n++
                if (n == 2) err(429, "RATE_LIMITED", 60) else MockResponse().setBody("""{"ackSeq":${batch.toSeq},"rejected":[]}""")
            }
        }
        assertEquals(SyncOutcome.WAIT, run())
        val fillId = state().fillId
        assertEquals(2, posted.size)
        val firstAck = posted[0].toSeq
        assertEquals(SyncOutcome.SENT, run())
        assertEquals(2, statusCalls)
        assertEquals(fillId, state().fillId)
        // Run 2 sends the refused batch again, then the last one.
        assertEquals(4, posted.size)
        assertEquals(firstAck + 1, posted[2].fromSeq)
        assertEquals(posted[1].fromSeq, posted[2].fromSeq)
        assertTrue(posted[3].fillEnd)
        assertEquals(1, posted.count { it.fillStart })
        assertEquals(0L, outboxCount())
    }

    @Test fun `two runs at once send each entry once`() {
        link(); rows(29) // 30 entries with the day... plus FILL_END
        val outcomes = runBlocking {
            listOf(
                async(Dispatchers.Default) { runner.run() },
                async(Dispatchers.Default) { runner.run() },
            ).awaitAll()
        }
        assertEquals(setOf(SyncOutcome.SENT, SyncOutcome.NOTHING_TO_SEND), outcomes.toSet())
        assertEquals(1, posted.size)
        assertEquals(1, posted.count { it.fillStart })
        assertEquals(1, posted.count { it.fillEnd })
        assertEquals(0L, outboxCount())
    }

    @Test fun `resync true starts a new fill when none is in flight`() {
        link(); rows(2)
        assertEquals(SyncOutcome.SENT, run())
        val old = state().fillId
        resync = true
        assertEquals(SyncOutcome.SENT, run())
        assertNotEquals(old, state().fillId)
        assertEquals(0L, outboxCount())
    }

    @Test fun `resync true with a fill in flight keeps the fill`() {
        link(); rows(2)
        batchOverrides += err(429, "RATE_LIMITED", 60)
        assertEquals(SyncOutcome.WAIT, run())
        val fillId = state().fillId
        resync = true
        assertEquals(SyncOutcome.SENT, run())
        assertEquals(fillId, state().fillId)
        assertEquals(fillId, posted.last().fillId)
    }

    @Test fun `restore marker starts a fill and clears the marker`() {
        link(); rows(2)
        assertEquals(SyncOutcome.SENT, run())
        val old = state().fillId
        marker.set()
        assertEquals(SyncOutcome.SENT, run())
        assertNotEquals(old, state().fillId)
        assertFalse(marker.isSet())
        assertTrue(posted.last().fillEnd)
    }

    @Test fun `a restored database with a stale fill gets a new fill`() {
        link(); rows(2)
        sql.execSQL("UPDATE sync_state SET enabled = 1, fillId = 'old' WHERE id = 1")
        sql.execSQL("INSERT INTO sync_outbox (tbl, uid, op) VALUES ('', 'old', 'FILL_END')")
        marker.set()
        assertEquals(SyncOutcome.SENT, run())
        val newId = state().fillId
        assertNotEquals("old", newId)
        assertTrue(posted.all { it.fillId == newId })
        assertTrue(posted.first().fillStart)
        assertTrue(posted.last().fillEnd)
        assertFalse(marker.isSet())
    }

    @Test fun `413 drops to 100-row batches`() {
        link(); rows(250)
        batchOverrides += err(413, "TOO_LARGE")
        assertEquals(SyncOutcome.SENT, run())
        assertEquals(0L, outboxCount())
        assertEquals(252L, posted.first().toSeq - posted.first().fromSeq + 1) // the refused one covered all entries
        val resent = posted.drop(1)
        assertTrue(resent.size >= 3)
        assertTrue(resent.all { it.toSeq - it.fromSeq + 1 <= 100 })
        assertTrue(resent.last().fillEnd)
    }

    @Test fun `413 at 100 rows retries`() {
        link(); rows(250)
        batchOverrides += err(413, "TOO_LARGE")
        batchOverrides += err(413, "TOO_LARGE")
        assertEquals(SyncOutcome.RETRY, run())
        assertEquals(252L, outboxCount())
    }

    @Test fun `400 on a batch keeps the outbox`() {
        link(); rows(2)
        batchOverrides += err(400, "BAD_REQUEST")
        assertEquals(SyncOutcome.REJECTED, run())
        assertEquals(4L, outboxCount())
    }

    @Test fun `a reply acknowledging more than was sent never deletes unsent entries`() {
        link(); rows(600)
        batchOverrides += MockResponse().setBody("""{"ackSeq":999999,"rejected":[]}""")
        assertEquals(SyncOutcome.SENT, run())
        assertTrue(posted.size >= 2) // the clamp left the rest to be sent
        assertTrue(posted.last().fillEnd)
    }

    @Test fun `lastSyncAt moves only when the outbox ends empty`() {
        link(); rows(2)
        clock.now = 100L
        batchOverrides += MockResponse().setResponseCode(503)
        assertEquals(SyncOutcome.RETRY, run())
        assertNull(runBlocking { store.lastSyncAt.first() })
        clock.now = 200L
        assertEquals(SyncOutcome.SENT, run())
        assertEquals(200L, runBlocking { store.lastSyncAt.first() })
        clock.now = 300L
        assertEquals(SyncOutcome.NOTHING_TO_SEND, run())
        assertEquals(300L, runBlocking { store.lastSyncAt.first() })
    }

    @Test fun `a status failure is retry and a 429 on status waits`() {
        link()
        statusOverrides += MockResponse().setResponseCode(503)
        assertEquals(SyncOutcome.RETRY, run())
        statusOverrides += err(429, "RATE_LIMITED", 120)
        assertEquals(SyncOutcome.WAIT, run())
        assertEquals(120, runner.lastWaitSeconds)
    }

    @Test fun `no token clears an enabled outbox`() {
        rows(2)
        runBlocking { repo.startFill() }
        assertEquals(SyncOutcome.NOT_LINKED, run())
        assertEquals(0, statusCalls)
        assertFalse(runBlocking { repo.isEnabled() })
        assertEquals(0L, outboxCount())
    }

    @Test fun `no CloudApi is off`() {
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { File(context.filesDir, "datastore/s2.preferences_pb") }),
        )
        val offRunner = SyncRunner(CloudRepo(store, null, settings, clock, context), store, repo, marker, clock)
        assertEquals(SyncOutcome.OFF, runBlocking { offRunner.run() })
        assertEquals(0, statusCalls)
    }
}
