package com.archsolutions.shopmanager.data.staff

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class StaffGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeClock(var now: Long = 1_000_000L) : AppClock {
        override fun nowMillis() = now
        override fun todayDateString() = "2026-01-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: StaffRepo
    private lateinit var session: StaffSession
    private lateinit var guard: StaffGuard
    private val clock = FakeClock()
    private var code = ""
    private var ownerId = 0L
    private var cashierA = 0L
    private var cashierB = 0L

    private suspend fun TestScope.setUpAll() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.createInMemory(context)
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("s.preferences_pb") }),
        )
        repo = StaffRepo(db, db.staffDao(), settings, clock, pinIterations = 1_000)
        code = repo.enableStaff("Owner", "1111")
        ownerId = repo.staff.first().single().id
        cashierA = repo.addStaff("A", Role.CASHIER, "2222")
        cashierB = repo.addStaff("B", Role.CASHIER, "3333")
        session = StaffSession(clock)
        guard = StaffGuard(repo, session, clock)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `signIn with the right pin signs the session in`() = runTest {
        setUpAll()
        assertEquals(PinResult.Ok, guard.signIn(cashierA, "2222"))
        assertEquals(cashierA, session.currentId)
    }

    @Test
    fun `5 wrong pins lock that person for 30 s and another person is unaffected`() = runTest {
        setUpAll()
        repeat(4) { assertEquals(PinResult.Wrong(4 - it), guard.signIn(cashierA, "0000")) }
        assertEquals(PinResult.Locked(Pin.LOCKOUT_MILLIS), guard.signIn(cashierA, "0000"))
        assertTrue(guard.signIn(cashierA, "2222") is PinResult.Locked)
        assertNull(session.currentId)
        assertEquals(PinResult.Ok, guard.signIn(cashierB, "3333"))
    }

    @Test
    fun `lock expires after 30 s`() = runTest {
        setUpAll()
        repeat(5) { guard.signIn(cashierA, "0000") }
        clock.now += Pin.LOCKOUT_MILLIS - 1
        assertTrue(guard.signIn(cashierA, "2222") is PinResult.Locked)
        clock.now += 1
        assertEquals(PinResult.Ok, guard.signIn(cashierA, "2222"))
    }

    @Test
    fun `approveAsOwner accepts any active owner's pin and rejects a cashier's pin`() = runTest {
        setUpAll()
        val second = repo.addStaff("Owner2", Role.OWNER, "4444")
        assertEquals(PinResult.Wrong(4), guard.approveAsOwner("2222"))
        assertNull(guard.lastApproverId)
        assertEquals(PinResult.Ok, guard.approveAsOwner("4444"))
        assertEquals(second, guard.lastApproverId)
        assertEquals(PinResult.Ok, guard.approveAsOwner("1111"))
        assertEquals(ownerId, guard.lastApproverId)
    }

    @Test
    fun `recover has its own counter`() = runTest {
        setUpAll()
        repeat(4) { assertEquals(RecoveryOutcome.WrongCode, guard.recover(ownerId, "WRONGCODE", "5555")) }
        assertEquals(RecoveryOutcome.Locked(Pin.LOCKOUT_MILLIS), guard.recover(ownerId, "WRONGCODE", "5555"))
        assertTrue(guard.recover(ownerId, code, "5555") is RecoveryOutcome.Locked)
        // Other counters are untouched.
        assertEquals(PinResult.Ok, guard.signIn(ownerId, "1111"))
        assertEquals(PinResult.Wrong(4), guard.approveAsOwner("0000"))
        clock.now += Pin.LOCKOUT_MILLIS
        assertTrue(guard.recover(ownerId, code, "5555") is RecoveryOutcome.Ok)
    }
}
