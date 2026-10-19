package com.archsolutions.shopmanager.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DayGateFlowTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private val clock = FakeClock("2026-09-30")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `gate follows the day from closed shop to open and past midnight`() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("gate.preferences_pb") },
            )
        )
        val dayRepo = DayRepo(db, clock)
        val gate = DayGate(dayRepo, settingsStore, clock)

        gate.state.test {
            assertEquals(DayGateState.OpenDayRequired, awaitItem())

            dayRepo.openDay("2026-09-30", openingCash = 150_000L, openingMobile = 40_000L)
            assertTrue(awaitItem() is DayGateState.Ready)

            // Back in the foreground the next morning, with yesterday never closed.
            clock.date = "2026-10-01"
            gate.refreshDate()
            val next = awaitItem()
            assertTrue(next is DayGateState.PreviousDayStillOpen)
            assertEquals("2026-09-30", (next as DayGateState.PreviousDayStillOpen).day.date)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `turning openDayOnStart off switches the gate to locked Home`() = runTest {
        val settingsStore = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tmpFolder.newFile("gate_setting.preferences_pb") },
            )
        )
        val gate = DayGate(DayRepo(db, clock), settingsStore, clock)

        gate.state.test {
            assertEquals(DayGateState.OpenDayRequired, awaitItem())

            settingsStore.setOpenDayOnStart(false)
            assertEquals(DayGateState.DayNotOpen, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
