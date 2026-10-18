package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.PurchaseWithItems
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.ProductSupplierEntity
import com.archsolutions.shopmanager.data.db.entities.PurchaseEntity
import com.archsolutions.shopmanager.data.db.entities.PurchaseItemEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.StockMovementEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class PurchaseLineItem(
    val productId: Long,
    val qty: Double,
    val unitCost: Long,
)

@Singleton
class PurchaseRepo @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    private val purchaseDao = db.purchaseDao()
    private val productDao = db.productDao()
    private val supplierDao = db.supplierDao()
    private val stockMovementDao = db.stockMovementDao()

    fun getPurchaseWithItems(purchaseId: Long): Flow<PurchaseWithItems?> =
        purchaseDao.getPurchaseWithItems(purchaseId)

    fun getPurchasesForDay(dayId: Long): Flow<List<PurchaseEntity>> =
        purchaseDao.getPurchasesForDay(dayId)

    fun getPurchasesWithItemsForDay(dayId: Long): Flow<List<PurchaseWithItems>> =
        purchaseDao.getPurchasesWithItemsForDay(dayId)

    suspend fun savePurchase(
        dayId: Long,
        supplierId: Long?,
        paid: PurchasePaid,
        items: List<PurchaseLineItem>,
        note: String? = null,
    ): Long = db.withTransaction {
        require(items.isNotEmpty()) { "Purchase must contain at least one item" }
        db.requireOpenDay(dayId)

        val now = clock.nowMillis()
        val total = items.sumOf { (it.qty * it.unitCost).toLong() }

        val purchaseId = purchaseDao.insertPurchase(
            PurchaseEntity(
                dayId = dayId,
                supplierId = supplierId,
                createdAt = now,
                total = total,
                paid = paid,
                note = note,
            )
        )

        val purchaseItems = items.map { item ->
            PurchaseItemEntity(
                purchaseId = purchaseId,
                productId = item.productId,
                qty = item.qty,
                unitCost = item.unitCost,
            )
        }
        purchaseDao.insertPurchaseItems(purchaseItems)

        for (item in items) {
            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = item.productId,
                    type = MovementType.PURCHASE,
                    qtyChange = item.qty,
                    refId = purchaseId,
                    reason = null,
                    createdAt = now,
                )
            )

            val currentProduct = productDao.getByIdDirect(item.productId)
            if (currentProduct != null) {
                val newStock = currentProduct.stockQty + item.qty
                productDao.updateStockAndCost(item.productId, newStock, item.unitCost, now)
            }

            if (supplierId != null) {
                // 5.3: refresh this supplier's cost, keeping whether it is the preferred one.
                val existing = supplierDao.getProductSupplier(item.productId, supplierId)
                supplierDao.upsertProductSupplier(
                    ProductSupplierEntity(
                        productId = item.productId,
                        supplierId = supplierId,
                        supplierCost = item.unitCost,
                        isPreferred = existing?.isPreferred ?: false,
                    )
                )
            }
        }

        purchaseId
    }
}
