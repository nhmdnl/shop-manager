package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import kotlinx.coroutines.flow.Flow

/** A product's supplier with its name, for Edit product. */
data class SupplierLink(
    val supplierId: Long,
    val name: String,
    val supplierCost: Long?,
    val isPreferred: Boolean,
)

@Dao
interface SupplierDao {
    @Query("SELECT * FROM supplier WHERE active = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM supplier WHERE id = :id LIMIT 1")
    fun getById(id: Long): Flow<SupplierEntity?>

    @Query("SELECT * FROM supplier WHERE id = :id LIMIT 1")
    suspend fun getByIdDirect(id: Long): SupplierEntity?

    @Query("SELECT * FROM supplier WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(supplier: SupplierEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(suppliers: List<SupplierEntity>)

    @Update
    suspend fun update(supplier: SupplierEntity)

    @Query("SELECT * FROM product_supplier WHERE productId = :productId")
    fun getProductSuppliers(productId: Long): Flow<List<ProductSupplierEntity>>

    @Query("""
        SELECT ps.supplierId AS supplierId, s.name AS name, ps.supplierCost AS supplierCost, ps.isPreferred AS isPreferred
        FROM product_supplier ps
        JOIN supplier s ON s.id = ps.supplierId
        WHERE ps.productId = :productId
        ORDER BY ps.isPreferred DESC, s.name COLLATE NOCASE ASC
    """)
    fun getSupplierLinks(productId: Long): Flow<List<SupplierLink>>

    @Query("SELECT * FROM product_supplier WHERE supplierId = :supplierId")
    fun getSupplierProducts(supplierId: Long): Flow<List<ProductSupplierEntity>>

    @Query("SELECT * FROM product_supplier WHERE productId = :productId AND supplierId = :supplierId LIMIT 1")
    suspend fun getProductSupplier(productId: Long, supplierId: Long): ProductSupplierEntity?

    @Upsert
    suspend fun upsertProductSupplier(link: ProductSupplierEntity)

    @Query("DELETE FROM product_supplier WHERE productId = :productId AND supplierId = :supplierId")
    suspend fun deleteProductSupplier(productId: Long, supplierId: Long)
}
