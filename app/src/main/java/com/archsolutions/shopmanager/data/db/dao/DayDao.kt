package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.CashCountEntity
import com.archsolutions.shopmanager.data.db.entities.CountPhase
import kotlinx.coroutines.flow.Flow

@Dao
interface DayDao {
    @Query("SELECT * FROM business_day WHERE date = :date LIMIT 1")
    fun getDayByDate(date: String): Flow<BusinessDayEntity?>

    @Query("SELECT * FROM business_day WHERE date = :date LIMIT 1")
    suspend fun getDayByDateDirect(date: String): BusinessDayEntity?

    @Query("SELECT * FROM business_day WHERE id = :id LIMIT 1")
    fun getDayById(id: Long): Flow<BusinessDayEntity?>

    @Query("SELECT * FROM business_day WHERE id = :id LIMIT 1")
    suspend fun getDayByIdDirect(id: Long): BusinessDayEntity?

    @Query("SELECT * FROM business_day ORDER BY date DESC LIMIT 1")
    fun getLatestDay(): Flow<BusinessDayEntity?>

    @Query("SELECT * FROM business_day ORDER BY date DESC LIMIT 1")
    suspend fun getLatestDayDirect(): BusinessDayEntity?

    @Query("SELECT * FROM business_day WHERE status = 'CLOSED' ORDER BY date DESC LIMIT 1")
    suspend fun getLatestClosedDayDirect(): BusinessDayEntity?

    @Query("SELECT * FROM business_day ORDER BY date DESC")
    fun getAllDays(): Flow<List<BusinessDayEntity>>

    @Query("SELECT * FROM business_day WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getDaysBetween(startDate: String, endDate: String): Flow<List<BusinessDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(day: BusinessDayEntity): Long

    @Update
    suspend fun updateDay(day: BusinessDayEntity)

    @Query("SELECT * FROM cash_count WHERE dayId = :dayId AND phase = :phase ORDER BY denomination DESC")
    fun getCashCounts(dayId: Long, phase: CountPhase): Flow<List<CashCountEntity>>

    @Query("SELECT * FROM cash_count WHERE dayId = :dayId AND phase = :phase ORDER BY denomination DESC")
    suspend fun getCashCountsDirect(dayId: Long, phase: CountPhase): List<CashCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashCounts(counts: List<CashCountEntity>)

    @Query("DELETE FROM cash_count WHERE dayId = :dayId AND phase = :phase")
    suspend fun deleteCashCounts(dayId: Long, phase: CountPhase)
}
