package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY active DESC, name COLLATE NOCASE")
    fun getAll(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getByIdDirect(id: Long): StaffEntity?

    @Insert
    suspend fun insert(s: StaffEntity): Long

    @Update
    suspend fun update(s: StaffEntity)

    @Query("SELECT COUNT(*) FROM staff WHERE role = 'OWNER' AND active = 1")
    suspend fun activeOwnerCount(): Int

    @Query("SELECT * FROM staff WHERE active = 1 ORDER BY name COLLATE NOCASE")
    suspend fun activeDirect(): List<StaffEntity>
}
