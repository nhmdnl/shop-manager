package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.StockMovementEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepo @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    private val productDao = db.productDao()
    private val categoryDao = db.categoryDao()
    private val supplierDao = db.supplierDao()
    private val stockMovementDao = db.stockMovementDao()

    fun getAllActive(): Flow<List<ProductEntity>> = productDao.getAllActive()

    fun getById(id: Long): Flow<ProductEntity?> = productDao.getById(id)

    suspend fun getByIdDirect(id: Long): ProductEntity? = productDao.getByIdDirect(id)

    fun getLowStock(): Flow<List<ProductEntity>> = productDao.getLowStock()

    fun getByCategory(categoryId: Long): Flow<List<ProductEntity>> = productDao.getByCategory(categoryId)

    fun search(query: String): Flow<List<ProductEntity>> = productDao.search(query)

    fun getTotalStockValue(): Flow<Long> = productDao.getTotalStockValue()

    fun getCategories(): Flow<List<CategoryEntity>> = categoryDao.getAll()

    suspend fun saveCategory(name: String, sortOrder: Int = 0): Long {
        val existing = categoryDao.getByName(name)
        return existing?.id ?: categoryDao.insert(CategoryEntity(name = name, sortOrder = sortOrder))
    }

    suspend fun saveProduct(
        product: ProductEntity,
        supplierId: Long? = null,
        supplierCost: Long? = null,
    ): Long = db.withTransaction {
        val now = clock.nowMillis()
        val toSave = if (product.id == 0L) {
            product.copy(createdAt = now, updatedAt = now)
        } else {
            product.copy(updatedAt = now)
        }
        val productId = productDao.insert(toSave)

        if (supplierId != null) {
            supplierDao.upsertProductSupplier(
                ProductSupplierEntity(
                    productId = productId,
                    supplierId = supplierId,
                    supplierCost = supplierCost ?: product.costPrice,
                    isPreferred = true,
                )
            )
        }
        productId
    }

    suspend fun adjustStock(
        productId: Long,
        newStock: Double,
        reason: String,
    ) = db.withTransaction {
        val product = productDao.getByIdDirect(productId) ?: return@withTransaction
        val qtyChange = newStock - product.stockQty
        val now = clock.nowMillis()

        stockMovementDao.insertMovement(
            StockMovementEntity(
                productId = productId,
                type = MovementType.ADJUSTMENT,
                qtyChange = qtyChange,
                refId = null,
                reason = reason,
                createdAt = now,
            )
        )
        productDao.updateStockQty(productId, newStock, now)
    }
}
