package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.cloud.contract.PlanState
import com.archsolutions.cloud.contract.ShopStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Reversible stand-in for the Keystore: XOR, with a switch to simulate a lost key. */
class FakeTokenCipher : TokenCipher {
    var failNext = false
    override fun encrypt(plain: ByteArray): ByteArray = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun decrypt(blob: ByteArray): ByteArray? {
        if (failNext) {
            failNext = false
            return null
        }
        return blob.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

@RunWith(RobolectricTestRunner::class)
class CloudStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val cipher = FakeTokenCipher()
    private val scopes = mutableListOf<Job>()

    private fun newStore(): CloudStore {
        val job = Job().also(scopes::add)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { cloudDataStoreFile(context) },
        )
        return CloudStore(dataStore, cipher)
    }

    @After
    fun tearDown() = runBlocking { scopes.forEach { it.cancelAndJoin() } }

    @Test
    fun `token round trips and snapshot says linked`() = runBlocking {
        val store = newStore()
        assertFalse(store.snapshot.first().linked)

        store.saveLink("tok-123", "shop-1", "0700111222")

        assertEquals("tok-123", store.token())
        val snap = store.snapshot.first()
        assertTrue(snap.linked)
        assertFalse(snap.replaced)
        assertEquals("shop-1", snap.shopId)
        assertEquals("0700111222", snap.phone)
        // Not stored in clear text.
        assertFalse(cloudDataStoreFile(context).readBytes().toString(Charsets.ISO_8859_1).contains("tok-123"))
    }

    @Test
    fun `undecryptable token reads as null and signs out`() = runBlocking {
        val store = newStore()
        store.saveLink("tok-123", "shop-1", "0700111222")
        store.saveStatus(status())

        cipher.failNext = true
        assertNull(store.token())

        val snap = store.snapshot.first()
        assertFalse(snap.linked)
        assertFalse(snap.replaced)
        assertNull(snap.shopId)
        assertNull(snap.phone)
        assertNull(snap.status)
    }

    @Test
    fun `markReplaced clears token and sets replaced`() = runBlocking {
        val store = newStore()
        store.saveLink("tok-123", "shop-1", "0700111222")

        store.markReplaced()

        assertNull(store.token())
        val snap = store.snapshot.first()
        assertFalse(snap.linked)
        assertTrue(snap.replaced)

        store.saveLink("tok-456", "shop-1", "0700111222")
        assertFalse(store.snapshot.first().replaced)
        assertEquals("tok-456", store.token())
    }

    @Test
    fun `status survives restart as json`() = runBlocking {
        val first = newStore()
        first.saveLink("tok-123", "shop-1", "0700111222")
        first.saveStatus(status())
        first.setLastCloudBackup(1_700_000_000_000L)
        scopes.forEach { it.cancelAndJoin() }

        val snap = newStore().snapshot.first()
        assertEquals(status(), snap.status)
        assertEquals(1_700_000_000_000L, snap.lastCloudBackupAt)
        assertTrue(snap.linked)
    }

    private fun status() = ShopStatus(plan = "basic", state = PlanState.TRIAL, trialEnd = 99L, features = listOf("backup"))
}

internal fun cloudDataStoreFile(context: Context): File = File(context.filesDir, "datastore/cloud.preferences_pb")
