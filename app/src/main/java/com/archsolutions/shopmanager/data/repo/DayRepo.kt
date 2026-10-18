package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.CashCountEntity
import com.archsolutions.shopmanager.data.db.entities.CountPhase
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayRepo @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    private val dayDao = db.dayDao()

    fun getDayByDate(date: String): Flow<BusinessDayEntity?> = dayDao.getDayByDate(date)

    suspend fun getDayByDateDirect(date: String): BusinessDayEntity? = dayDao.getDayByDateDirect(date)

    fun getDayById(id: Long): Flow<BusinessDayEntity?> = dayDao.getDayById(id)

    suspend fun getDayByIdDirect(id: Long): BusinessDayEntity? = dayDao.getDayByIdDirect(id)

    fun getLatestDay(): Flow<BusinessDayEntity?> = dayDao.getLatestDay()

    suspend fun getLatestDayDirect(): BusinessDayEntity? = dayDao.getLatestDayDirect()

    suspend fun getLatestClosedDayDirect(): BusinessDayEntity? = dayDao.getLatestClosedDayDirect()

    /** Oldest day before [date] that was never closed — gate case 6.3-1. */
    fun getOpenDayBefore(date: String): Flow<BusinessDayEntity?> = dayDao.getOpenDayBefore(date)

    suspend fun getOpenDayBeforeDirect(date: String): BusinessDayEntity? = dayDao.getOpenDayBeforeDirect(date)

    fun getAllDays(): Flow<List<BusinessDayEntity>> = dayDao.getAllDays()

    fun getDaysBetween(startDate: String, endDate: String): Flow<List<BusinessDayEntity>> =
        dayDao.getDaysBetween(startDate, endDate)

    fun getCashCounts(dayId: Long, phase: CountPhase): Flow<List<CashCountEntity>> =
        dayDao.getCashCounts(dayId, phase)

    suspend fun openDay(
        date: String,
        openingCash: Long,
        openingMobile: Long,
        counts: List<CashCountEntity> = emptyList(),
    ): Long = db.withTransaction {
        check(dayDao.getDayByDateDirect(date) == null) { "A business day already exists for $date" }
        // 6.3-1: today cannot be opened while an earlier day is still open.
        check(dayDao.getOpenDayBeforeDirect(date) == null) { "An earlier business day is still open" }

        val now = clock.nowMillis()
        val dayId = dayDao.insertDay(
            BusinessDayEntity(
                date = date,
                status = DayStatus.OPEN,
                openedAt = now,
                openingCash = openingCash,
                openingMobile = openingMobile,
            )
        )

        if (counts.isNotEmpty()) {
            val mappedCounts = counts.map { it.copy(dayId = dayId, phase = CountPhase.OPEN) }
            dayDao.insertCashCounts(mappedCounts)
        }

        dayId
    }

    suspend fun closeDay(
        dayId: Long,
        countedCash: Long,
        countedMobile: Long,
        expectedCash: Long,
        expectedMobile: Long,
        diffCash: Long,
        diffMobile: Long,
        diffReason: String? = null,
        closeNote: String? = null,
        counts: List<CashCountEntity> = emptyList(),
    ) = db.withTransaction {
        val day = dayDao.getDayByIdDirect(dayId) ?: return@withTransaction
        val now = clock.nowMillis()

        dayDao.updateDay(
            day.copy(
                status = DayStatus.CLOSED,
                closedAt = now,
                expectedCash = expectedCash,
                expectedMobile = expectedMobile,
                countedCash = countedCash,
                countedMobile = countedMobile,
                diffCash = diffCash,
                diffMobile = diffMobile,
                diffReason = diffReason,
                closeNote = closeNote,
            )
        )

        if (counts.isNotEmpty()) {
            val mappedCounts = counts.map { it.copy(dayId = dayId, phase = CountPhase.CLOSE) }
            dayDao.insertCashCounts(mappedCounts)
        }
    }

    suspend fun reopenDay(dayId: Long) = db.withTransaction {
        val day = dayDao.getDayByIdDirect(dayId) ?: return@withTransaction
        dayDao.updateDay(
            day.copy(
                status = DayStatus.OPEN,
                closedAt = null,
                expectedCash = null,
                expectedMobile = null,
                countedCash = null,
                countedMobile = null,
                diffCash = null,
                diffMobile = null,
                diffReason = null,
                closeNote = null,
                reopenedCount = day.reopenedCount + 1,
            )
        )
    }
}
