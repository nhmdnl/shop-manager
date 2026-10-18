package com.archsolutions.shopmanager.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.db.entities.CountPhase
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.domain.NoteCounter
import com.archsolutions.shopmanager.testutil.FakeClock
import com.archsolutions.shopmanager.testutil.testDay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DayRepoTest {

    private lateinit var db: AppDatabase
    private lateinit var dayRepo: DayRepo
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = AppDatabase.createInMemory(context)
        dayRepo = DayRepo(db, FakeClock("2026-09-30"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `opening the same date twice is refused and keeps the first day`() = runTest {
        val dayId = dayRepo.openDay("2026-09-30", openingCash = 150_000L, openingMobile = 40_000L)

        val error = runCatching { dayRepo.openDay("2026-09-30", openingCash = 1L, openingMobile = 1L) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        val day = dayRepo.getDayByDateDirect("2026-09-30")!!
        assertEquals(dayId, day.id)
        assertEquals(150_000L, day.openingCash)
    }

    @Test
    fun `today cannot be opened while an earlier day is still open`() = runTest {
        db.dayDao().insertDay(testDay("2026-09-29", DayStatus.OPEN))

        val error = runCatching { dayRepo.openDay("2026-09-30", openingCash = 0L, openingMobile = 0L) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertNull(dayRepo.getDayByDateDirect("2026-09-30"))
    }

    @Test
    fun `note counts are saved with the opened day`() = runTest {
        val counts = NoteCounter.toCashCounts(mapOf(50_000L to 2, 1_000L to 3), coins = 700L, phase = CountPhase.OPEN)

        val dayId = dayRepo.openDay("2026-09-30", openingCash = 103_700L, openingMobile = 0L, counts = counts)

        val saved = db.dayDao().getCashCountsDirect(dayId, CountPhase.OPEN)
        assertEquals(listOf(50_000L to 2, 1_000L to 3, NoteCounter.COINS to 700), saved.map { it.denomination to it.count })
        assertTrue(saved.all { it.dayId == dayId })
    }

    @Test
    fun `previous open day is the oldest unclosed day before the date`() = runTest {
        val dayDao = db.dayDao()
        dayDao.insertDay(testDay("2026-09-27", DayStatus.CLOSED))
        dayDao.insertDay(testDay("2026-09-28", DayStatus.OPEN))
        dayDao.insertDay(testDay("2026-09-29", DayStatus.OPEN))
        dayDao.insertDay(testDay("2026-09-30", DayStatus.OPEN))

        assertEquals("2026-09-28", dayRepo.getOpenDayBeforeDirect("2026-09-30")?.date)
        assertNull(dayRepo.getOpenDayBeforeDirect("2026-09-28"))
    }

    @Test
    fun `inserting a second day for a date aborts instead of replacing the first`() = runTest {
        val dayDao = db.dayDao()
        val dayId = dayDao.insertDay(testDay("2026-09-30", DayStatus.OPEN))

        val error = runCatching {
            dayDao.insertDay(testDay("2026-09-30", DayStatus.OPEN).copy(openingCash = 1L))
        }.exceptionOrNull()

        assertTrue(error is SQLiteConstraintException)
        val day = dayDao.getDayByDateDirect("2026-09-30")!!
        assertEquals(dayId, day.id)
        assertEquals(0L, day.openingCash)
    }
}
