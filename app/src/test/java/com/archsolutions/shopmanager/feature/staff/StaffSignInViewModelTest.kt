package com.archsolutions.shopmanager.feature.staff

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffGuard
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StaffSignInViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repo: StaffRepo
    private lateinit var session: StaffSession
    private lateinit var guard: StaffGuard

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
        guard = StaffGuard(repo, session, clock)
    }

    /** A new sign-in screen on the same shop and settings; its `picked` is collected so its flows run. */
    private fun TestScope.newVm(): StaffSignInViewModel {
        val vm = StaffSignInViewModel(repo, guard, session, settings)
        backgroundScope.launch { vm.picked.collect {} }
        return vm
    }

    /** Owner Grace (1111) and cashier Amina (2222); returns Amina's id. */
    private suspend fun seedShop(): Long {
        repo.enableStaff("Grace", "1111")
        return repo.addStaff("Amina", Role.CASHIER, "2222")
    }

    private suspend fun graceId(): Long = repo.staff.first().first { it.name == "Grace" }.id

    /** Picks [id] and waits until `picked` shows it: the shop and settings load asynchronously behind it. */
    private suspend fun StaffSignInViewModel.pickAndWait(id: Long) {
        pick(id)
        picked.first { it == id }
    }

    @Test
    fun `nobody is picked before anyone has signed in on this device`() = runTest {
        build()
        seedShop()

        val vm = newVm()
        vm.people.first { it.isNotEmpty() }

        assertNull(vm.picked.value)
    }

    @Test
    fun `the last person to sign in is picked when the screen opens`() = runTest {
        build()
        val amina = seedShop()
        val vm = newVm()
        vm.pickAndWait(amina)

        assertEquals(PinResult.Ok, vm.enter("2222"))

        assertEquals(amina, settings.settings.first().lastStaffId)
        val next = newVm()
        assertEquals(amina, next.picked.first { it != null })
    }

    @Test
    fun `a wrong PIN does not change the remembered person`() = runTest {
        build()
        val amina = seedShop()
        val grace = graceId()
        val vm = newVm()
        vm.pickAndWait(amina)
        assertEquals(PinResult.Ok, vm.enter("2222"))
        session.signOut()

        vm.pickAndWait(grace)
        assertTrue(vm.enter("9999") is PinResult.Wrong)

        assertEquals(amina, settings.settings.first().lastStaffId)
    }

    @Test
    fun `a deactivated last person is not picked`() = runTest {
        build()
        val amina = seedShop()
        val vm = newVm()
        vm.pickAndWait(amina)
        assertEquals(PinResult.Ok, vm.enter("2222"))
        session.signOut()

        // Picked while active, then not once switched off: the second wait proves the change reached `picked`.
        val next = newVm()
        assertEquals(amina, next.picked.first { it != null })
        repo.setActive(amina, false)
        assertNull(next.picked.first { it == null })
    }

    @Test
    fun `a sign-out returns the pick to the last person`() = runTest {
        build()
        val amina = seedShop()
        val grace = graceId()
        val vm = newVm()
        vm.pickAndWait(amina)
        assertEquals(PinResult.Ok, vm.enter("2222"))

        vm.pickAndWait(grace)
        session.signOut()

        assertEquals(amina, vm.picked.first { it == amina })
    }
}
