package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import kotlin.math.roundToLong

/** One product in the current sale. Prices come from the product when the sale is saved. */
data class CartLine(val product: ProductEntity, val qty: Double) {
    val lineTotal: Long get() = (qty * product.sellPrice).roundToLong()
}

/** Sale math shared by the Sell screens (spec 5.1). */
object CartMath {
    /** Σ qty × price rounded once, the same way SaleRepo.createSale stores it. */
    fun total(lines: List<CartLine>): Long = lines.sumOf { it.qty * it.product.sellPrice }.roundToLong()

    /** "6 items": whole quantities count as units; a fractional kg or litre line counts once. */
    fun itemCount(lines: List<CartLine>): Int =
        lines.sumOf { line -> if (line.qty % 1.0 == 0.0) line.qty.toInt() else 1 }

    /** Change to give, shown only when the cash received covers the total (5.1.7). */
    fun change(total: Long, cashReceived: Long?): Long? =
        cashReceived?.minus(total)?.takeIf { it >= 0 }
}

/** Quick "cash received" amounts (5.1.7): Exact, next 5,000, next 10,000 and 50,000. */
object CashQuickAmounts {
    fun forTotal(total: Long): List<Long> {
        if (total <= 0) return emptyList()
        return listOf(
            total,
            roundUp(total, 5_000),
            roundUp(total, 10_000),
            maxOf(50_000, roundUp(total, 50_000)),
        ).distinct().sorted()
    }

    private fun roundUp(amount: Long, step: Long): Long = (amount + step - 1) / step * step
}

/** Same rule as ProductDao.getLowStock: low at or below the alert level. */
val ProductEntity.isLowStock: Boolean get() = stockQty <= lowStockLevel
