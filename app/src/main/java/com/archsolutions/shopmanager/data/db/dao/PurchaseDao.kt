package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.archsolutions.shopmanager.data.db.entities.PurchaseEntity
import com.archsolutions.shopmanager.data.db.entities.PurchaseItemEntity
import kotlinx.coroutines.flow.Flow

data class PurchaseWithItems(
    @Embedded val purchase: PurchaseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "purchaseId",
    )
    val items: List<PurchaseItemEntity>,
)

@Dao
interface PurchaseDao {
    @Transaction
    @Query("SELECT * FROM purchase WHERE id = :purchaseId LIMIT 1")
    fun getPurchaseWithItems(purchaseId: Long): Flow<PurchaseWithItems?>

    @Query("SELECT * FROM purchase WHERE dayId = :dayId ORDER BY createdAt DESC")
    fun getPurchasesForDay(dayId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase WHERE dayId = :dayId ORDER BY createdAt DESC")
    suspend fun getPurchasesForDayDirect(dayId: Long): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_item WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchase WHERE id = :id LIMIT 1")
    suspend fun getPurchaseByIdDirect(id: Long): PurchaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)
}
