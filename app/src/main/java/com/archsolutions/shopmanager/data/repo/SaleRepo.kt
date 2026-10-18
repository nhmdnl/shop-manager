package com.archsolutions.shopmanager.data.repo

import androidx.room.withTransaction
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.dao.SaleWithItems
import com.archsolutions.shopmanager.data.db.entities.MovementType
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.shopmanager.data.db.entities.SaleItemEntity
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.db.entities.StockMovementEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

data class SaleCartItem(
    val productId: Long,
    val productName: String,
    val qty: Double,
    val unitPrice: Long,
    val unitCost: Long,
)

@Singleton
class SaleRepo @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    private val saleDao = db.saleDao()
    private val productDao = db.productDao()
    private val stockMovementDao = db.stockMovementDao()

    fun getSaleWithItems(saleId: Long): Flow<SaleWithItems?> = saleDao.getSaleWithItems(saleId)

    fun getSalesForDay(dayId: Long): Flow<List<SaleEntity>> = saleDao.getSalesForDay(dayId)

    fun getSalesWithItemsForDay(dayId: Long): Flow<List<SaleWithItems>> = saleDao.getSalesWithItemsForDay(dayId)

    fun getRecentSales(limit: Int = 5): Flow<List<SaleEntity>> = saleDao.getRecentSales(limit)

    fun getSalesBetween(startMillis: Long, endMillis: Long): Flow<List<SaleEntity>> =
        saleDao.getSalesBetween(startMillis, endMillis)

    suspend fun createSale(
        dayId: Long,
        method: PayMethod,
        items: List<SaleCartItem>,
        customerId: Long? = null,
        cashReceived: Long? = null,
    ): Long = db.withTransaction {
        require(items.isNotEmpty()) { "Sale must contain at least one item" }
        if (method == PayMethod.CREDIT) {
            requireNotNull(customerId) { "Credit sales require a customer" }
        }
        db.requireOpenDay(dayId)

        val now = clock.nowMillis()
        // Spec 5.1.2: Σ qty × price, rounded to whole UGX once (kg/litre quantities can be fractional).
        val total = items.sumOf { it.qty * it.unitPrice }.roundToLong()
        val costTotal = items.sumOf { it.qty * it.unitCost }.roundToLong()

        val saleId = saleDao.insertSale(
            SaleEntity(
                dayId = dayId,
                createdAt = now,
                total = total,
                costTotal = costTotal,
                method = method,
                customerId = customerId,
                cashReceived = cashReceived,
                status = SaleStatus.DONE,
            )
        )

        val saleItems = items.map { item ->
            SaleItemEntity(
                saleId = saleId,
                productId = item.productId,
                productName = item.productName,
                qty = item.qty,
                unitPrice = item.unitPrice,
                unitCost = item.unitCost,
            )
        }
        saleDao.insertSaleItems(saleItems)

        for (item in items) {
            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = item.productId,
                    type = MovementType.SALE,
                    qtyChange = -item.qty,
                    refId = saleId,
                    reason = null,
                    createdAt = now,
                )
            )

            val currentProduct = productDao.getByIdDirect(item.productId)
            if (currentProduct != null) {
                val newStock = currentProduct.stockQty - item.qty
                productDao.updateStockQty(item.productId, newStock, now)
            }
        }

        saleId
    }

    suspend fun voidSale(saleId: Long, reason: String) = db.withTransaction {
        val sale = saleDao.getSaleByIdDirect(saleId) ?: return@withTransaction
        if (sale.status == SaleStatus.VOIDED) return@withTransaction
        db.requireOpenDay(sale.dayId)

        val now = clock.nowMillis()
        saleDao.voidSale(saleId, reason, now)

        val items = saleDao.getSaleItems(saleId)
        for (item in items) {
            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = item.productId,
                    type = MovementType.VOID_RETURN,
                    qtyChange = item.qty,
                    refId = saleId,
                    reason = reason,
                    createdAt = now,
                )
            )

            val currentProduct = productDao.getByIdDirect(item.productId)
            if (currentProduct != null) {
                val newStock = currentProduct.stockQty + item.qty
                productDao.updateStockQty(item.productId, newStock, now)
            }
        }
    }
}
