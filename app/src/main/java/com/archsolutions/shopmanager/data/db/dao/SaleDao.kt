package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.shopmanager.data.db.entities.SaleItemEntity
import kotlinx.coroutines.flow.Flow

data class SaleWithItems(
    @Embedded val sale: SaleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "saleId",
    )
    val items: List<SaleItemEntity>,
)

@Dao
interface SaleDao {
    @Transaction
    @Query("SELECT * FROM sale WHERE id = :saleId LIMIT 1")
    fun getSaleWithItems(saleId: Long): Flow<SaleWithItems?>

    @Query("SELECT * FROM sale WHERE dayId = :dayId ORDER BY createdAt DESC")
    fun getSalesForDay(dayId: Long): Flow<List<SaleEntity>>

    // Every DONE credit sale, oldest first: the input for what each customer owes and since when.
    @Query("SELECT * FROM sale WHERE status = 'DONE' AND method = 'CREDIT' ORDER BY createdAt ASC, id ASC")
    fun getCreditSales(): Flow<List<SaleEntity>>

    @Transaction
    @Query("SELECT * FROM sale WHERE dayId = :dayId ORDER BY createdAt DESC, id DESC")
    fun getSalesWithItemsForDay(dayId: Long): Flow<List<SaleWithItems>>

    @Query("SELECT * FROM sale WHERE dayId = :dayId ORDER BY createdAt DESC")
    suspend fun getSalesForDayDirect(dayId: Long): List<SaleEntity>

    @Query("SELECT * FROM sale WHERE status = 'DONE' ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentSales(limit: Int = 5): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sale WHERE createdAt >= :startMillis AND createdAt <= :endMillis AND status = 'DONE' ORDER BY createdAt DESC")
    fun getSalesBetween(startMillis: Long, endMillis: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sale WHERE createdAt >= :startMillis AND createdAt <= :endMillis AND status = 'DONE' ORDER BY createdAt DESC")
    suspend fun getSalesBetweenDirect(startMillis: Long, endMillis: Long): List<SaleEntity>

    @Query("SELECT * FROM sale_item WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM sale WHERE id = :id LIMIT 1")
    suspend fun getSaleByIdDirect(id: Long): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("UPDATE sale SET status = 'VOIDED', voidReason = :reason, voidedAt = :voidedAt WHERE id = :saleId")
    suspend fun voidSale(saleId: Long, reason: String, voidedAt: Long)
}
