package com.archsolutions.shopmanager.feature.day

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.CountPhase
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.NoteCounter
import com.archsolutions.shopmanager.testutil.FakeClock
import com.archsolutions.shopmanager.testutil.testDay
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OpenDayViewModelTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dayRepo: DayRepo
    private val clock = FakeClock("2026-09-30")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = AppDatabase.createInMemory(context)
        dayRepo = DayRepo(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.newViewModel(carryClosingToOpening: Boolean = true): OpenDayViewModel {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("open_day.preferences_pb") },
            )
        )
        settingsStore.setCarryClosingToOpening(carryClosingToOpening)
        return OpenDayViewModel(dayRepo, DayGate(dayRepo, settingsStore, clock), settingsStore)
    }

    private suspend fun closeYesterdayWith(cash: Long, mobile: Long) {
        db.dayDao().insertDay(testDay("2026-09-29", DayStatus.CLOSED, countedCash = cash, countedMobile = mobile))
    }

    @Test
    fun `fields prefill with yesterday's closing counts`() = runTest {
        closeYesterdayWith(cash = 150_000L, mobile = 40_000L)

        val state = newViewModel().state.first { it.loaded }

        assertEquals(150_000L, state.cash)
        assertEquals(40_000L, state.mobile)
        assertEquals(150_000L, state.yesterdayCash)
        assertEquals(40_000L, state.yesterdayMobile)
    }

    @Test
    fun `without carry-over the fields start empty but yesterday stays visible`() = runTest {
        closeYesterdayWith(cash = 150_000L, mobile = 40_000L)

        val state = newViewModel(carryClosingToOpening = false).state.first { it.loaded }

        assertNull(state.cash)
        assertNull(state.mobile)
        assertEquals(150_000L, state.yesterdayCash)
    }

    @Test
    fun `counting notes fills cash and saves the counts with the day`() = runTest {
        val viewModel = newViewModel()
        viewModel.state.first { it.loaded }

        viewModel.setNoteCount(50_000L, 2)
        viewModel.setNoteCount(1_000L, 3)
        viewModel.setCoins(500L)
        viewModel.setMobile(40_000L)
        assertEquals(103_500L, viewModel.state.value.cash)

        viewModel.startDay()
        viewModel.state.first { it.started }

        val day = dayRepo.getDayByDateDirect("2026-09-30")!!
        assertEquals(DayStatus.OPEN, day.status)
        assertEquals(103_500L, day.openingCash)
        assertEquals(40_000L, day.openingMobile)
        assertEquals(
            listOf(50_000L to 2, 1_000L to 3, NoteCounter.COINS to 500),
            db.dayDao().getCashCountsDirect(day.id, CountPhase.OPEN).map { it.denomination to it.count },
        )
    }

    @Test
    fun `typing cash by hand drops the note counts`() = runTest {
        val viewModel = newViewModel()
        viewModel.state.first { it.loaded }

        viewModel.setNoteCount(50_000L, 2)
        viewModel.setCash(90_000L)
        viewModel.startDay()
        viewModel.state.first { it.started }

        val day = dayRepo.getDayByDateDirect("2026-09-30")!!
        assertEquals(90_000L, day.openingCash)
        assertTrue(db.dayDao().getCashCountsDirect(day.id, CountPhase.OPEN).isEmpty())
    }

    @Test
    fun `start the day fails while yesterday is still open`() = runTest {
        db.dayDao().insertDay(testDay("2026-09-29", DayStatus.OPEN))
        val viewModel = newViewModel()
        viewModel.state.first { it.loaded }

        viewModel.startDay()
        viewModel.state.first { it.saveFailed }

        assertNull(dayRepo.getDayByDateDirect("2026-09-30"))
    }
}
