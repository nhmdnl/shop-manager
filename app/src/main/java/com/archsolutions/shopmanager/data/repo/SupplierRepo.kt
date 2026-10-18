package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.SupplierLink
import com.archsolutions.shopmanager.data.db.dao.SupplierProductRow
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplierRepo @Inject constructor(
    private val db: AppDatabase,
) {
    private val supplierDao = db.supplierDao()

    fun getAllActive(): Flow<List<SupplierEntity>> = supplierDao.getAllActive()

    fun getById(id: Long): Flow<SupplierEntity?> = supplierDao.getById(id)

    suspend fun getByIdDirect(id: Long): SupplierEntity? = supplierDao.getByIdDirect(id)

    /**
     * A new or edited supplier and the products they sell, in one transaction. Links that stay keep
     * their supplier cost and preferred flag.
     */
    suspend fun saveSupplier(supplier: SupplierEntity, productIds: Set<Long>): Long = db.withTransaction {
        // insert() replaces on conflict, which would re-create the row; edits go through update().
        val id = if (supplier.id == 0L) supplierDao.insert(supplier) else supplier.id.also { supplierDao.update(supplier) }
        val linked = supplierDao.getSupplierProductsDirect(id).map { it.productId }.toSet()
        (linked - productIds).forEach { supplierDao.deleteProductSupplier(it, id) }
        (productIds - linked).forEach {
            supplierDao.upsertProductSupplier(ProductSupplierEntity(productId = it, supplierId = id, supplierCost = null))
        }
        id
    }

    fun getSupplierProductRows(): Flow<List<SupplierProductRow>> = supplierDao.getSupplierProductRows()

    suspend fun getSupplierProductsDirect(supplierId: Long): List<ProductSupplierEntity> =
        supplierDao.getSupplierProductsDirect(supplierId)

    fun getSupplierLinks(productId: Long): Flow<List<SupplierLink>> = supplierDao.getSupplierLinks(productId)

    fun getProductSuppliers(productId: Long): Flow<List<ProductSupplierEntity>> =
        supplierDao.getProductSuppliers(productId)

    fun getSupplierProducts(supplierId: Long): Flow<List<ProductSupplierEntity>> =
        supplierDao.getSupplierProducts(supplierId)

    suspend fun linkProductToSupplier(
        productId: Long,
        supplierId: Long,
        supplierCost: Long?,
        isPreferred: Boolean = false,
    ) {
        supplierDao.upsertProductSupplier(
            ProductSupplierEntity(
                productId = productId,
                supplierId = supplierId,
                supplierCost = supplierCost,
                isPreferred = isPreferred,
            )
        )
    }

    suspend fun unlinkProductFromSupplier(productId: Long, supplierId: Long) {
        supplierDao.deleteProductSupplier(productId, supplierId)
    }
}
