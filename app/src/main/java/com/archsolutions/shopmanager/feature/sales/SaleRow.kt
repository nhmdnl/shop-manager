package com.archsolutions.shopmanager.feature.sales

import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.dao.SaleWithItems
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.SaleItemEntity
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import java.time.ZoneId

/** One sale as listed on Home (Recent sales) and in Today's sales. */
data class SaleRow(
    val saleId: Long,
    val time: String,
    val summary: String,
    val method: PayMethod,
    val customerName: String?,
    val voided: Boolean,
    val voidReason: String?,
    val total: Long,
)

/** "Sugar 1kg, Bread ×2" */
fun itemsSummary(items: List<SaleItemEntity>): String =
    items.joinToString(", ") { item ->
        if (item.qty == 1.0) item.productName else "${item.productName} ×${Qty.format(item.qty)}"
    }

fun SaleWithItems.toRow(zone: ZoneId, customerNames: Map<Long, String>): SaleRow = SaleRow(
    saleId = sale.id,
    time = DayFormat.time(sale.createdAt, zone),
    summary = itemsSummary(items),
    method = sale.method,
    customerName = sale.customerId?.let { customerNames[it] },
    voided = sale.status == SaleStatus.VOIDED,
    voidReason = sale.voidReason,
    total = sale.total,
)
