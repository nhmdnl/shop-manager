package com.archsolutions.shopmanager.data.repo

import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.SupplierLink
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

    suspend fun saveSupplier(supplier: SupplierEntity): Long = supplierDao.insert(supplier)

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
