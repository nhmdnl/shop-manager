package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyEntryDao {
    @Query("SELECT * FROM money_entry WHERE dayId = :dayId ORDER BY createdAt DESC")
    fun getEntriesForDay(dayId: Long): Flow<List<MoneyEntryEntity>>

    @Query("SELECT * FROM money_entry WHERE type = 'CREDIT_REPAYMENT' ORDER BY createdAt ASC, id ASC")
    fun getRepayments(): Flow<List<MoneyEntryEntity>>

    @Query("SELECT * FROM money_entry WHERE dayId = :dayId ORDER BY createdAt DESC")
    suspend fun getEntriesForDayDirect(dayId: Long): List<MoneyEntryEntity>

    @Query("SELECT * FROM money_entry WHERE createdAt >= :startMillis AND createdAt <= :endMillis ORDER BY createdAt DESC")
    fun getEntriesBetween(startMillis: Long, endMillis: Long): Flow<List<MoneyEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MoneyEntryEntity): Long

    @Delete
    suspend fun deleteEntry(entry: MoneyEntryEntity)
}
