package com.archsolutions.shopmanager.data.cloud

import com.archsolutions.cloud.contract.ArchHeaders
import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.cloud.contract.PlanState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class CloudApiTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private var token: String? = null
    private lateinit var api: CloudApi

    private val statusJson =
        """{"plan":"basic","state":"ACTIVE","periodEnd":99,"features":["backup"],"resync":false}"""

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        api = CloudApi(server.url("/"), OkHttpClient(), "1.0.0") { token }
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `every call sends contract and app version headers`() {
        server.enqueue(MockResponse().setBody(statusJson))
        val status = runBlocking { api.status() }
        val req = server.takeRequest()
        assertEquals(PlanState.ACTIVE, status.state)
        assertEquals("/v1/shop/status", req.path)
        assertEquals("1", req.getHeader(ArchHeaders.CONTRACT))
        assertEquals("1.0.0", req.getHeader(ArchHeaders.APP_VERSION))
    }

    @Test fun `bearer token sent when present and absent when null`() {
        server.enqueue(MockResponse().setBody(statusJson))
        server.enqueue(MockResponse().setBody(statusJson))
        token = "tok-1"
        runBlocking { api.status() }
        token = null
        runBlocking { api.status() }
        assertEquals("Bearer tok-1", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `error body maps to CloudException code`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"DEVICE_REVOKED","message":"replaced"}"""))
        val e = assertThrows(CloudException::class.java) { runBlocking { api.status() } }
        assertEquals(ErrorCode.DEVICE_REVOKED, e.code)
        assertEquals(401, e.http)
        assertEquals("replaced", e.message)
    }

    @Test fun `429 carries Retry-After seconds`() {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "42")
                .setBody("""{"code":"RATE_LIMITED","message":"slow down"}"""),
        )
        val e = assertThrows(CloudException::class.java) { runBlocking { api.requestCode("+256700000000") } }
        assertEquals(ErrorCode.RATE_LIMITED, e.code)
        assertEquals(42, e.retryAfterSeconds)
    }

    @Test fun `non json 502 maps to CloudException with null code`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        val e = assertThrows(CloudException::class.java) { runBlocking { api.status() } }
        assertNull(e.code)
        assertEquals(502, e.http)
        assertNull(e.retryAfterSeconds)
    }

    @Test fun `200 with html body maps to CloudException with null code`() {
        server.enqueue(MockResponse().setBody("<html>Please sign in to the WiFi</html>"))
        val e = assertThrows(CloudException::class.java) { runBlocking { api.status() } }
        assertNull(e.code)
        assertEquals(200, e.http)
    }

    @Test fun `upload sends multipart file and sha header`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"backupId":"b1"}"""))
        val zip = tmp.newFile("b.zip").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val created = runBlocking { api.uploadBackup(zip, "abc123") }
        val req = server.takeRequest()
        assertEquals("b1", created.backupId)
        assertEquals("POST", req.method)
        assertEquals("abc123", req.getHeader(ArchHeaders.CONTENT_SHA256))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("Content-Type: application/zip"))
    }

    @Test fun `download writes the exact bytes`() {
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val dest = tmp.newFile("out.zip")
        runBlocking { api.downloadBackup("b1", dest) }
        assertEquals("/v1/backups/b1/file", server.takeRequest().path)
        assertArrayEquals(bytes, dest.readBytes())
    }

    @Test fun `connection drop surfaces as IOException`() {
        server.enqueue(
            MockResponse().setBody("x".repeat(10_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        assertThrows(IOException::class.java) { runBlocking { api.status() } }
    }
}
