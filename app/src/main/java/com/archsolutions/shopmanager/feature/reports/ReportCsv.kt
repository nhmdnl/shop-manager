package com.archsolutions.shopmanager.feature.reports

import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.dao.ReportItemRow
import com.archsolutions.shopmanager.data.db.dao.ReportSaleRow
import com.archsolutions.shopmanager.domain.ClosedDayRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The CSV exports of section 10. Amounts are plain numbers so spreadsheets can add them up; the BOM
 * makes Excel read the file as UTF-8 (Sheets ignores it).
 */
internal object ReportCsv {
    private const val BOM = "﻿"
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    /** sales_{from}_{to}.csv: date, time, sale id, method, customer, items, total, profit. */
    fun sales(sales: List<ReportSaleRow>, items: List<ReportItemRow>, zoneId: ZoneId): String {
        val itemsBySale = items.groupBy { it.saleId }
        val rows = sales.map { sale ->
            listOf(
                sale.date,
                Instant.ofEpochMilli(sale.createdAt).atZone(zoneId).format(timeFormat),
                sale.id.toString(),
                sale.method.name,
                sale.customerName.orEmpty(),
                itemsBySale[sale.id].orEmpty().joinToString("; ") { "${it.productName} × ${Qty.format(it.qty)}" },
                sale.total.toString(),
                (sale.total - sale.costTotal).toString(),
            )
        }
        return table(listOf("date", "time", "sale id", "method", "customer", "items", "total", "profit"), rows)
    }

    /** days_{from}_{to}.csv: date, openings, expected, counted, differences, reason; oldest day first. */
    fun days(days: List<ClosedDayRow>): String {
        val rows = days.sortedBy { it.day.date }.map { (day, sales) ->
            listOf(
                day.date,
                sales.toString(),
                day.openingCash.toString(),
                day.openingMobile.toString(),
                day.expectedCash?.toString().orEmpty(),
                day.expectedMobile?.toString().orEmpty(),
                day.countedCash?.toString().orEmpty(),
                day.countedMobile?.toString().orEmpty(),
                day.diffCash?.toString().orEmpty(),
                day.diffMobile?.toString().orEmpty(),
                day.diffReason.orEmpty(),
            )
        }
        val header = listOf(
            "date", "sales", "opening cash", "opening momo", "expected cash", "expected momo",
            "counted cash", "counted momo", "cash difference", "momo difference", "reason",
        )
        return table(header, rows)
    }

    private fun table(header: List<String>, rows: List<List<String>>): String =
        BOM + (listOf(header) + rows).joinToString(separator = "\r\n", postfix = "\r\n") { row -> row.joinToString(",") { escape(it) } }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
