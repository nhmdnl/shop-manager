package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sortOrder ASC, name ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM category WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
