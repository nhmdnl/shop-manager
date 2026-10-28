package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.cloud.contract.CONSENT_VERSION
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
import org.json.JSONObject
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

@RunWith(RobolectricTestRunner::class)
class CloudRepoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private lateinit var store: CloudStore
    private lateinit var settings: SettingsStore
    private val job = Job()

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val scope = CoroutineScope(Dispatchers.IO + job)
        store = CloudStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { cloudDataStoreFile(context) }),
            FakeTokenCipher(),
        )
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.filesDir, "datastore/s.preferences_pb") }),
        )
    }

    @After fun tearDown() {
        server.shutdown()
        runBlocking { job.cancelAndJoin() }
        cloudDataStoreFile(context).delete()
    }

    private fun repo() = CloudRepo(
        store, CloudApi(server.url("/"), OkHttpClient(), "1.0.0") { store.token() }, settings, FixedClock(), context,
    )

    @Test fun `blank base url means Off and no requests`() = runBlocking {
        val repo = CloudRepo(store, null, settings, FixedClock(), context)
        assertEquals(CloudState.Off, repo.state.first())
        assertNull(repo.refreshStatus())
        repo.unlink()
        assertEquals(CloudState.Off, repo.state.first())
        assertEquals(0, server.requestCount)
    }

    @Test fun `state follows the store`() = runBlocking {
        val repo = repo()
        assertEquals(CloudState.SignedOut, repo.state.first())
        store.saveLink("t", "s", "0700")
        assertTrue(repo.state.first() is CloudState.Linked)
        store.markReplaced()
        assertEquals(CloudState.Replaced, repo.state.first())
    }

    @Test fun `link 409 is TabletExists and stores nothing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"TABLET_EXISTS","message":"m"}"""))
        val out = repo().link("tk", "0700111222", replace = false, ownerName = null, consent = true)
        assertEquals(LinkOutcome.TabletExists, out)
        assertFalse(store.snapshot.first().linked)
        assertNull(store.token())
    }

    @Test fun `link sends shop name language and consent version`() = runBlocking {
        settings.setShopName("Mary's Shop")
        settings.setLanguage("lg")
        server.enqueue(MockResponse().setBody("""{"deviceToken":"dev-1","shopId":"shop-9"}"""))
        val out = repo().link("tk", "0700111222", replace = true, ownerName = "Mary", consent = true)
        assertEquals(LinkOutcome.Linked, out)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("tk", body.getString("ownerTicket"))
        assertEquals("Mary's Shop", body.getString("shopName"))
        assertEquals("lg", body.getString("language"))
        assertEquals(CONSENT_VERSION, body.getString("consentVersion"))
        assertEquals("Mary", body.getString("ownerName"))
        assertTrue(body.getBoolean("replace"))
        assertEquals("dev-1", store.token())
        assertEquals("shop-9", store.snapshot.first().shopId)
    }

    @Test fun `link without consent sends no consent version`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"deviceToken":"dev-1","shopId":"shop-9"}"""))
        repo().link("tk", "0700111222", replace = false, ownerName = null, consent = false)
        assertFalse(JSONObject(server.takeRequest().body.readUtf8()).has("consentVersion"))
    }

    @Test fun `refreshStatus caches status for the card`() = runBlocking {
        store.saveLink("t", "s", "0700")
        server.enqueue(MockResponse().setBody("""{"plan":"basic","state":"GRACE","graceEnd":42,"features":["backup"],"resync":false}"""))
        val repo = repo()
        val fresh = repo.refreshStatus()
        assertEquals("GRACE", fresh?.state?.name)
        val state = repo.state.first() as CloudState.Linked
        assertEquals(fresh, state.status)
    }

    @Test fun `unlink clears even when the server is unreachable`() = runBlocking {
        store.saveLink("t", "s", "0700")
        server.shutdown()
        repo().unlink()
        assertFalse(store.snapshot.first().linked)
    }
}
