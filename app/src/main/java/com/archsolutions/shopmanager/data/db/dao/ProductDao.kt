package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

/** A low-stock product with its preferred supplier, for the Home card (7.1). */
data class LowStockItem(
    val productId: Long,
    val name: String,
    val stockQty: Double,
    val supplierName: String?,
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM product WHERE active = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product WHERE id = :id LIMIT 1")
    fun getById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM product WHERE id = :id LIMIT 1")
    suspend fun getByIdDirect(id: Long): ProductEntity?

    @Query("SELECT * FROM product WHERE active = 1 AND stockQty <= lowStockLevel ORDER BY stockQty ASC, name ASC")
    fun getLowStock(): Flow<List<ProductEntity>>

    @Query("""
        SELECT p.id AS productId, p.name AS name, p.stockQty AS stockQty, MIN(s.name) AS supplierName
        FROM product p
        LEFT JOIN product_supplier ps ON ps.productId = p.id AND ps.isPreferred = 1
        LEFT JOIN supplier s ON s.id = ps.supplierId
        WHERE p.active = 1 AND p.stockQty <= p.lowStockLevel
        GROUP BY p.id
        ORDER BY p.stockQty ASC, p.name ASC
    """)
    fun getLowStockWithSupplier(): Flow<List<LowStockItem>>

    @Query("SELECT * FROM product WHERE active = 1 AND categoryId = :categoryId ORDER BY name ASC")
    fun getByCategory(categoryId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product WHERE active = 1 AND (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') ORDER BY name ASC")
    fun search(query: String): Flow<List<ProductEntity>>

    @Query("SELECT CAST(COALESCE(SUM(stockQty * costPrice), 0) AS INTEGER) FROM product WHERE active = 1")
    fun getTotalStockValue(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE product SET stockQty = :newStock, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateStockQty(productId: Long, newStock: Double, updatedAt: Long)

    @Query("UPDATE product SET costPrice = :newCost, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateCostPrice(productId: Long, newCost: Long, updatedAt: Long)

    @Query("UPDATE product SET stockQty = :newStock, costPrice = :newCost, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateStockAndCost(productId: Long, newStock: Double, newCost: Long, updatedAt: Long)
}
