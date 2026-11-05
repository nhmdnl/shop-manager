package com.archsolutions.shopmanager.feature.staff

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinGuard
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StaffViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: StaffRepo
    private lateinit var session: StaffSession
    private lateinit var vm: StaffViewModel

    private val clock = object : AppClock {
        override fun nowMillis() = 1_000L
        override fun todayDateString() = "2026-01-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::db.isInitialized) db.close()
    }

    private fun TestScope.build() {
        db = AppDatabase.createInMemory(context)
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("s.preferences_pb") }),
        )
        repo = StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000)
        session = StaffSession(clock)
        vm = StaffViewModel(repo, session, settings, PinGuard(settings, clock))
    }

    @Test
    fun `setUp with an existing shared PIN verifies it first (wrong PIN stops setup)`() = runTest {
        build()
        settings.setPin(enabled = true, hash = Pin.hash("4321"))

        try {
            vm.setUp("Grace", "1111")
            fail("expected the wrong shared PIN to stop setup")
        } catch (_: WrongSharedPinException) {
        }
        assertFalse(settings.settings.first().staffEnabled)
        assertTrue(repo.staff.first().isEmpty())
        assertNull(vm.shownCode.value)

        val code = vm.setUp("Grace", "4321")

        assertTrue(settings.settings.first().staffEnabled)
        assertEquals("Grace", repo.staff.first().single().name)
        assertNotNull(code)
    }

    @Test
    fun `setUp without a shared PIN uses the chosen one and signs the Owner in`() = runTest {
        build()

        vm.setUp("  Grace  ", "1111")

        val owner = repo.staff.first().single()
        assertEquals("Grace", owner.name)
        assertEquals(Role.OWNER.key, owner.role)
        assertTrue(repo.verify(owner.id, "1111"))
        assertEquals(owner.id, session.currentId)
    }

    @Test
    fun `setUp shows the recovery code exactly once (a second read is not possible)`() = runTest {
        build()

        val code = vm.setUp("Grace", "1111")

        assertEquals(code, vm.shownCode.value)
        vm.codeWritten()
        assertNull(vm.shownCode.value)
        // The store holds only a hash; the plain code is nowhere to be read back.
        assertNotEquals(code, settings.settings.first().recoveryHash)
        assertFalse(settings.settings.first().recoveryHash.contains(code))
        // Setting up again is refused and shows nothing.
        try {
            vm.setUp("Grace", "1111")
            fail("expected a second setUp to be refused")
        } catch (_: IllegalStateException) {
        }
        assertNull(vm.shownCode.value)
    }

    @Test
    fun `setUp rejects a PIN that is not exactly 4 digits and a blank name`() = runTest {
        build()
        listOf("", "123", "12345", "12a4", " 123", "١٢٣٤").forEach { bad ->
            try {
                vm.setUp("Grace", bad)
                fail("expected '$bad' to be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
        try {
            vm.setUp("   ", "1111")
            fail("expected a blank name to be rejected")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(settings.settings.first().staffEnabled)
        assertTrue(repo.staff.first().isEmpty())
    }

    @Test
    fun `toggleActive refuses the last owner and reports it`() = runTest {
        build()
        vm.setUp("Grace", "1111")
        val owner = repo.staff.first().single()

        assertFalse(vm.toggleActive(owner.id))
        assertTrue(repo.staff.first().single().active)

        val second = repo.addStaff("Joan", Role.OWNER, "2222")
        assertTrue(vm.toggleActive(second))
        assertFalse(repo.staff.first().first { it.id == second }.active)
        // Joan is the only other Owner and is off again, so Grace is the last one once more.
        assertFalse(vm.toggleActive(owner.id))
        assertTrue(vm.toggleActive(second))
        assertTrue(repo.staff.first().first { it.id == second }.active)
    }

    @Test
    fun `deactivating the signed-in person signs them out at once`() = runTest {
        build()
        vm.setUp("Grace", "1111")
        val amina = repo.addStaff("Amina", Role.CASHIER, "2222")
        session.signIn(repo.staff.first().first { it.id == amina })
        assertEquals(amina, session.currentId)

        assertTrue(vm.toggleActive(amina))

        assertNull(session.currentId)
    }

    @Test
    fun `deactivating someone else leaves the session alone`() = runTest {
        build()
        vm.setUp("Grace", "1111")
        val owner = session.currentId
        val amina = repo.addStaff("Amina", Role.CASHIER, "2222")

        assertTrue(vm.toggleActive(amina))

        assertEquals(owner, session.currentId)
    }

    @Test
    fun `idle minutes accepts 1 to 60 and ignores others`() = runTest {
        build()
        assertEquals(10, settings.settings.first().idleTimeoutMinutes)

        vm.setIdleMinutes(1)?.join()
        assertEquals(1, settings.settings.first().idleTimeoutMinutes)
        vm.setIdleMinutes(60)?.join()
        assertEquals(60, settings.settings.first().idleTimeoutMinutes)
        listOf(0, -5, 61, 1000).forEach {
            assertNull(vm.setIdleMinutes(it))
            assertEquals(60, settings.settings.first().idleTimeoutMinutes)
        }
    }

    @Test
    fun `add rename and resetPin reject bad names and PINs without touching the repo`() = runTest {
        build()
        vm.setUp("Grace", "1111")

        assertEquals(StaffInputProblem.BLANK_NAME, vm.add("   ", Role.CASHIER, "2222"))
        listOf("", "222", "22222", "22x2").forEach {
            assertEquals(StaffInputProblem.BAD_PIN, vm.add("Amina", Role.CASHIER, it))
        }
        assertEquals(1, repo.staff.first().size)

        assertNull(vm.add("  Amina ", Role.CASHIER, "2222"))
        val amina = repo.staff.first().first { it.name == "Amina" }

        assertEquals(StaffInputProblem.BLANK_NAME, vm.rename(amina.id, " "))
        assertNull(vm.rename(amina.id, " Amina K "))
        assertEquals("Amina K", repo.staff.first().first { it.id == amina.id }.name)

        assertEquals(StaffInputProblem.BAD_PIN, vm.resetPin(amina.id, "12"))
        assertTrue(repo.verify(amina.id, "2222"))
        assertNull(vm.resetPin(amina.id, "3333"))
        assertTrue(repo.verify(amina.id, "3333"))
    }

    @Test
    fun `turnOff switches staff off, keeps the rows and signs everyone out`() = runTest {
        build()
        vm.setUp("Grace", "1111")

        vm.turnOff().join()

        assertFalse(settings.settings.first().staffEnabled)
        assertEquals(1, repo.staff.first().size)
        assertNull(session.currentId)
    }

    @Test
    fun `checkSharedPin uses the shared PIN guard`() = runTest {
        build()
        settings.setPin(enabled = true, hash = Pin.hash("4321"))

        assertTrue(vm.checkSharedPin("0000") is PinResult.Wrong)
        assertEquals(PinResult.Ok, vm.checkSharedPin("4321"))
    }

    @Test
    fun `regenerate shows the code once, then it is gone after confirm and on sign-out`() = runTest {
        build()
        vm.setUp("Grace", "1111")
        vm.codeWritten()

        val code = vm.newRecoveryCode()!!

        assertEquals(code, vm.shownCode.value)
        vm.codeWritten()
        assertNull(vm.shownCode.value)

        vm.newRecoveryCode()
        assertNotNull(vm.shownCode.value)
        session.signOut()
        vm.discardCode()
        assertNull(vm.shownCode.value)
        // Nobody signed in: nothing to regenerate for, and nothing shown.
        assertNull(vm.newRecoveryCode())
        assertNull(vm.shownCode.value)
    }

    @Test
    fun `regenerate is refused for a signed-in cashier`() = runTest {
        build()
        vm.setUp("Grace", "1111")
        vm.codeWritten()
        val amina = repo.addStaff("Amina", Role.CASHIER, "2222")
        session.signIn(repo.staff.first().first { it.id == amina })

        assertNull(vm.newRecoveryCode())
        assertNull(vm.shownCode.value)
    }

    @Test
    fun `a signed-in cashier cannot unlock the staff screen even with an owner approval, an owner can, unknown role cannot`() = runTest {
        build()
        vm.setUp("Grace", "1111")
        val owner = session.current.value!!
        assertTrue(vm.signedInOwner.value)
        assertTrue(canOpenStaffScreen(Role.OWNER, true))

        val amina = repo.addStaff("Amina", Role.CASHIER, "2222")
        session.signIn(repo.staff.first().first { it.id == amina })
        session.approvedBy = owner.id

        assertFalse(vm.signedInOwner.value)
        assertFalse(canOpenStaffScreen(Role.CASHIER, true))
        assertFalse(canOpenStaffScreen(null, true))
        assertFalse(canOpenStaffScreen(Role.OWNER, null))
        assertFalse(canOpenStaffScreen(Role.OWNER, false))
    }
}
