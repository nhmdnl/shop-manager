package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.archsolutions.shopmanager.data.db.entities.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movement WHERE productId = :productId ORDER BY createdAt DESC, id DESC")
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movement WHERE productId = :productId ORDER BY createdAt DESC, id DESC")
    suspend fun getMovementsForProductDirect(productId: Long): List<StockMovementEntity>

    @Query("SELECT COALESCE(SUM(qtyChange), 0.0) FROM stock_movement WHERE productId = :productId")
    suspend fun getSumQuantityChanges(productId: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<StockMovementEntity>)
}
