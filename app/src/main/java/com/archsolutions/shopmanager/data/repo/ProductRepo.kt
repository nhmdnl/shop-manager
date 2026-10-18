package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.LowStockItem
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

    fun getLowStockWithSupplier(): Flow<List<LowStockItem>> = productDao.getLowStockWithSupplier()

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

    /** A new product; its starting stock is one ADJUSTMENT "Opening stock" movement (addendum C1). */
    suspend fun createProduct(
        product: ProductEntity,
        startingStock: Double,
        suppliers: List<ProductSupplierEntity> = emptyList(),
    ): Long = db.withTransaction {
        val now = clock.nowMillis()
        val productId = productDao.insert(
            product.copy(id = 0, stockQty = startingStock, createdAt = now, updatedAt = now)
        )
        if (startingStock != 0.0) {
            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    type = MovementType.ADJUSTMENT,
                    qtyChange = startingStock,
                    refId = null,
                    reason = OPENING_STOCK_REASON,
                    createdAt = now,
                )
            )
        }
        suppliers.forEach { supplierDao.upsertProductSupplier(it.copy(productId = productId)) }
        productId
    }

    /** Saves the editable fields; stock only ever changes through movements, so stockQty is kept. */
    suspend fun updateProduct(
        product: ProductEntity,
        suppliers: List<ProductSupplierEntity> = emptyList(),
    ) = db.withTransaction {
        val current = productDao.getByIdDirect(product.id) ?: return@withTransaction
        productDao.update(
            product.copy(stockQty = current.stockQty, createdAt = current.createdAt, updatedAt = clock.nowMillis())
        )
        suppliers.forEach { supplierDao.upsertProductSupplier(it.copy(productId = product.id)) }
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

    companion object {
        /** Reason on the ADJUSTMENT that records a new product's starting stock (addendum C1). */
        const val OPENING_STOCK_REASON = "Opening stock"
    }
}
