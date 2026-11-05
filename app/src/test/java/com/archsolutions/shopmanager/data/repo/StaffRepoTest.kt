package com.archsolutions.shopmanager.data.repo

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class StaffRepoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: StaffRepo

    private val clock = object : AppClock {
        override fun nowMillis() = 1_000L
        override fun todayDateString() = "2026-01-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    private fun TestScope.setUpRepo() {
        db = AppDatabase.createInMemory(context)
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("s.preferences_pb") }),
        )
        repo = StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    private suspend fun assertRejects(block: suspend () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `enableStaff creates one active owner and returns an 8-char code`() = runTest {
        setUpRepo()
        val code = repo.enableStaff("  Mary ", "1234")
        val all = repo.staff.first()
        assertEquals(1, all.size)
        assertEquals("Mary", all[0].name)
        assertEquals("OWNER", all[0].role)
        assertTrue(all[0].active)
        assertEquals(9, code.length)
        assertEquals(8, code.replace("-", "").length)
        assertTrue(settings.settings.first().staffEnabled)
    }

    @Test
    fun `enableStaff stores a pbkdf2 hash, never the plain pin`() = runTest {
        setUpRepo()
        val code = repo.enableStaff("Mary", "1234")
        val owner = repo.staff.first().single()
        assertTrue(owner.pinHash.startsWith("pbkdf2$"))
        assertFalse(owner.pinHash.contains("1234"))
        assertTrue(repo.verify(owner.id, "1234"))
        assertFalse(repo.verify(owner.id, "9999"))
        val s = settings.settings.first()
        assertTrue(s.recoveryHash.startsWith("pbkdf2$"))
        assertFalse(s.recoveryHash.contains(code.replace("-", "")))
        // The legacy shared PIN is untouched.
        assertFalse(s.pinEnabled)
        assertEquals("", s.pinHash)
    }

    @Test
    fun `staff on and off leave the legacy shared pin alone`() = runTest {
        setUpRepo()
        val legacy = Pin.hash("2580")
        settings.setPin(true, legacy)
        repo.enableStaff("Mary", "1234")
        repo.disableStaff()
        val s = settings.settings.first()
        assertTrue(s.pinEnabled)
        assertEquals(legacy, s.pinHash)
    }

    @Test
    fun `last active owner cannot be deactivated`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        val owner = repo.staff.first().single()
        assertFalse(repo.setActive(owner.id, false))
        assertTrue(repo.staff.first().single().active)
    }

    @Test
    fun `a second owner can be deactivated`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        val second = repo.addStaff("Joe", Role.OWNER, "5678")
        assertTrue(repo.setActive(second, false))
        assertFalse(repo.staff.first().first { it.id == second }.active)
    }

    @Test
    fun `blank name is rejected`() = runTest {
        setUpRepo()
        assertRejects { repo.enableStaff("  ", "1234") }
        assertEquals(0, repo.staff.first().size)
        repo.enableStaff("Mary", "1234")
        assertRejects { repo.addStaff(" ", Role.CASHIER, "1111") }
        val id = repo.staff.first().single().id
        assertRejects { repo.rename(id, "") }
    }

    @Test
    fun `duplicate names are allowed`() = runTest {
        setUpRepo()
        repo.enableStaff("Sam", "1234")
        repo.addStaff("Sam", Role.CASHIER, "1111")
        assertEquals(2, repo.staff.first().count { it.name == "Sam" })
    }

    @Test
    fun `inactive person fails verify`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        val id = repo.addStaff("Joe", Role.CASHIER, "1111")
        assertTrue(repo.verify(id, "1111"))
        repo.setActive(id, false)
        assertFalse(repo.verify(id, "1111"))
    }

    @Test
    fun `recoverOwner with the right code sets the new pin and returns a fresh code (old code stops working)`() = runTest {
        setUpRepo()
        val code = repo.enableStaff("Mary", "1234")
        val id = repo.staff.first().single().id
        val fresh = repo.recoverOwner(id, code.lowercase().replace("-", " "), "4321")
        assertTrue(fresh != null && fresh.length == 9)
        assertTrue(repo.verify(id, "4321"))
        assertFalse(repo.verify(id, "1234"))
        assertNull(repo.recoverOwner(id, code, "0000"))
        assertTrue(repo.recoverOwner(id, fresh!!, "5555") != null)
    }

    @Test
    fun `recoverOwner with a wrong code returns null and changes nothing`() = runTest {
        setUpRepo()
        val code = repo.enableStaff("Mary", "1234")
        val id = repo.staff.first().single().id
        val before = settings.settings.first().recoveryHash
        assertNull(repo.recoverOwner(id, "ZZZZ-ZZZZ", "4321"))
        assertTrue(repo.verify(id, "1234"))
        assertEquals(before, settings.settings.first().recoveryHash)
        assertTrue(repo.recoverOwner(id, code, "4321") != null)
    }

    @Test
    fun `disableStaff clears the flag and keeps the staff rows`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        repo.addStaff("Joe", Role.CASHIER, "1111")
        repo.disableStaff()
        assertFalse(settings.settings.first().staffEnabled)
        assertEquals(2, repo.staff.first().size)
    }

    @Test
    fun `enableStaff twice throws and leaves one owner`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        try {
            repo.enableStaff("Mary2", "5678")
            fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
        val all = repo.staff.first()
        assertEquals(1, all.size)
        assertTrue(repo.verify(all[0].id, "1234"))
    }

    @Test
    fun `enable, disable, enable again needs the Owner PIN and keeps name, PIN and row`() = runTest {
        setUpRepo()
        val oldCode = repo.enableStaff("Mary", "1234")
        val before = repo.staff.first().single()
        repo.disableStaff()

        val newCode = repo.enableStaff("Ignored", "1234")

        val after = repo.staff.first().single()
        assertEquals(before, after)
        assertEquals("Mary", after.name)
        assertTrue(repo.verify(after.id, "1234"))
        assertTrue(settings.settings.first().staffEnabled)
        assertNull(repo.recoverOwner(after.id, oldCode, "0000"))
        assertTrue(repo.recoverOwner(after.id, newCode, "5555") != null)
    }

    @Test
    fun `re-enabling with a wrong Owner PIN refuses and changes nothing`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        val before = repo.staff.first()
        val hashBefore = settings.settings.first().recoveryHash
        repo.disableStaff()

        try {
            repo.enableStaff("Eve", "9999")
            fail("expected WrongOwnerPinException")
        } catch (_: WrongOwnerPinException) {
        }

        assertEquals(before, repo.staff.first())
        assertFalse(settings.settings.first().staffEnabled)
        assertEquals(hashBefore, settings.settings.first().recoveryHash)
    }

    @Test
    fun `other staff survive off-then-on`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        val joe = repo.addStaff("Joe", Role.CASHIER, "1111")
        repo.disableStaff()
        repo.enableStaff("Mary", "1234")
        assertEquals(2, repo.staff.first().size)
        assertTrue(repo.verify(joe, "1111"))
    }

    @Test
    fun `off-then-on uses the first active Owner when the first one is switched off`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        val second = repo.addStaff("Joe", Role.OWNER, "5678")
        val first = repo.staff.first().first { it.id != second }.id
        repo.setActive(first, false)
        repo.disableStaff()
        assertEquals(second, repo.existingOwner()!!.id)
        try {
            repo.enableStaff("Mary", "1234")
            fail("the switched-off Owner's PIN must not work")
        } catch (_: WrongOwnerPinException) {
        }
        repo.enableStaff("Mary", "5678")
        assertFalse(repo.staff.first().first { it.id == first }.active)
        assertEquals(2, repo.staff.first().size)
    }

    @Test
    fun `a failed database step leaves staff off with no new recovery hash`() = runTest {
        setUpRepo()
        db.close()
        try {
            repo.enableStaff("Mary", "1234")
            fail("expected the closed database to fail")
        } catch (_: Exception) {
        }
        assertFalse(settings.settings.first().staffEnabled)
        assertEquals("", settings.settings.first().recoveryHash)
    }

    @Test
    fun `the on flag is the last write (recovery hash is already stored when beforeEnable runs)`() = runTest {
        setUpRepo()
        var enabledWhenCalled: Boolean? = null
        var hashWhenCalled: String? = null
        repo.enableStaff("Mary", "1234") {
            enabledWhenCalled = settings.settings.first().staffEnabled
            hashWhenCalled = settings.settings.first().recoveryHash
        }
        assertEquals(false, enabledWhenCalled)
        assertTrue(hashWhenCalled!!.isNotEmpty())
        assertTrue(settings.settings.first().staffEnabled)
    }

    @Test
    fun `staffActive needs the setting and an active Owner`() = runTest {
        setUpRepo()
        // Setting on with no Owner row (a failed or restored state): behaves as staff off.
        settings.setStaffEnabled(true)
        assertFalse(repo.staffActive.first())

        settings.setStaffEnabled(false)
        repo.enableStaff("Mary", "1234")
        assertTrue(repo.staffActive.first())
        val mary = repo.staff.first().single().id
        val joe = repo.addStaff("Joe", Role.OWNER, "5678")
        repo.setActive(mary, false)
        assertTrue(repo.staffActive.first())
        repo.setActive(joe, false) // refused: last active Owner
        assertTrue(repo.staffActive.first())
        repo.disableStaff()
        assertFalse(repo.staffActive.first())
    }

    @Test
    fun `hashing and verifying run on the injected dispatcher, not the caller`() = runTest {
        setUpRepo()
        var dispatches = 0
        val counting = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                dispatches++
                kotlinx.coroutines.Dispatchers.Default.dispatch(context, block)
            }
        }
        val r = StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000, hashDispatcher = counting)
        r.enableStaff("Mary", "1234")
        val afterEnable = dispatches
        assertTrue(afterEnable >= 2)
        val id = r.staff.first().single().id
        val joe = r.addStaff("Joe", Role.CASHIER, "1111")
        assertTrue(r.verify(joe, "1111"))
        r.resetPin(joe, "2222")
        assertTrue(dispatches >= afterEnable + 3)
        assertNotNull(r.regenerateRecoveryCode(id))
    }

    @Test
    fun `recoverOwner with a cashier id returns null`() = runTest {
        setUpRepo()
        val code = repo.enableStaff("Mary", "1234")
        val cashier = repo.addStaff("Joe", Role.CASHIER, "1111")
        assertNull(repo.recoverOwner(cashier, code, "0000"))
        assertTrue(repo.verify(cashier, "1111"))
    }

    @Test
    fun `recoverOwner with an inactive owner id returns null`() = runTest {
        setUpRepo()
        val code = repo.enableStaff("Mary", "1234")
        val second = repo.addStaff("Joe", Role.OWNER, "5678")
        assertTrue(repo.setActive(second, false))
        assertNull(repo.recoverOwner(second, code, "0000"))
    }

    @Test
    fun `setActive can reactivate a deactivated person`() = runTest {
        setUpRepo()
        repo.enableStaff("Mary", "1234")
        val id = repo.addStaff("Joe", Role.CASHIER, "1111")
        repo.setActive(id, false)
        assertTrue(repo.setActive(id, true))
        assertTrue(repo.verify(id, "1111"))
    }

    @Test
    fun `verify on a missing id is false`() = runTest {
        setUpRepo()
        assertFalse(repo.verify(999L, "1234"))
    }

    @Test
    fun `regenerate returns a new code, old code stops working, new code works, cashier or inactive id returns null, unknown id returns null`() = kotlinx.coroutines.test.runTest {
        setUpRepo()
        val old = repo.enableStaff("Grace", "1111")
        val grace = repo.staff.first().single().id
        val cashier = repo.addStaff("Amina", Role.CASHIER, "2222")
        val joan = repo.addStaff("Joan", Role.OWNER, "3333")

        val fresh = repo.regenerateRecoveryCode(grace)!!

        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(fresh))
        assertTrue(fresh != old)
        assertNull(repo.recoverOwner(grace, old, "4444"))
        assertNotNull(repo.recoverOwner(grace, fresh, "4444"))

        assertNull(repo.regenerateRecoveryCode(cashier))
        repo.setActive(joan, false)
        assertNull(repo.regenerateRecoveryCode(joan))
        assertNull(repo.regenerateRecoveryCode(9_999L))
    }
}
