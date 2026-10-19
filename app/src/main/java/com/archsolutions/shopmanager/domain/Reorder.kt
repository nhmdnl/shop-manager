package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.dao.SupplierProductRow
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import kotlin.math.ceil
import kotlin.math.max

/** Same rule as `ProductEntity.isLowStock`. */
val SupplierProductRow.isLow: Boolean get() = stockQty <= lowStockLevel

data class SupplierSummary(val supplier: SupplierEntity, val productCount: Int, val lowCount: Int)

/** "6 low items across 3 suppliers": each low product counts once, however many suppliers sell it. */
data class LowAcrossSuppliers(val items: Int, val suppliers: Int)

data class ReorderLine(val productId: Long, val name: String, val stockQty: Double, val suggested: Long)

/** Templates from strings.xml, so the order goes out in the app language (5.6). */
data class OrderTemplates(
    val greeting: String, // "Hello %1$s, order from %2$s:"
    val line: String, // "- %1$s × %2$s"
    val thanks: String, // "Thank you."
)

object Reorder {
    /** 5.6: max(lowStockLevel × 2 − stockQty, 1), rounded up to whole units. */
    fun suggested(lowStockLevel: Double, stockQty: Double): Long =
        // The small nudge keeps float noise (2 × 1.1 − 0.2 = 2.0000000000000004) from adding a unit.
        max(ceil(lowStockLevel * 2 - stockQty - 1e-9), 1.0).toLong()

    fun summaries(suppliers: List<SupplierEntity>, rows: List<SupplierProductRow>): List<SupplierSummary> {
        val bySupplier = rows.groupBy { it.supplierId }
        return suppliers.map { supplier ->
            val products = bySupplier[supplier.id].orEmpty()
            SupplierSummary(supplier, productCount = products.size, lowCount = products.count { it.isLow })
        }
    }

    fun lowAcross(summaries: List<SupplierSummary>, rows: List<SupplierProductRow>): LowAcrossSuppliers {
        val active = summaries.mapTo(HashSet()) { it.supplier.id }
        val low = rows.filter { it.supplierId in active && it.isLow }
        return LowAcrossSuppliers(
            items = low.distinctBy { it.productId }.size,
            suppliers = low.distinctBy { it.supplierId }.size,
        )
    }

    fun lines(rows: List<SupplierProductRow>, supplierId: Long): List<ReorderLine> =
        rows.filter { it.supplierId == supplierId && it.isLow }
            .map { ReorderLine(it.productId, it.name, it.stockQty, suggested(it.lowStockLevel, it.stockQty)) }

    /** The WhatsApp order (5.6). [items] are (product name, quantity) in the order shown. */
    fun message(templates: OrderTemplates, contact: String, shopName: String, items: List<Pair<String, Long>>): String =
        buildString {
            append(templates.greeting.format(contact, shopName))
            items.forEach { (name, qty) -> append('\n').append(templates.line.format(name, qty)) }
            append('\n').append(templates.thanks)
        }
}
