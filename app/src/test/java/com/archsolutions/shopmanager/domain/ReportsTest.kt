package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.dao.ReportItemRow
import com.archsolutions.shopmanager.data.db.dao.ReportSaleRow
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReportsTest {

    private val today = LocalDate.of(2026, 9, 30)

    private fun sale(id: Long, date: String, total: Long, cost: Long, method: PayMethod = PayMethod.CASH) =
        ReportSaleRow(id, date, createdAt = 0L, method = method, total = total, costTotal = cost, customerName = null)

    private fun item(saleId: Long, productId: Long, name: String, qty: Double, price: Long) =
        ReportItemRow(saleId, productId, name, qty, price)

    @Test
    fun `presets cover today, the last seven days and this month`() {
        assertEquals(ReportPeriod(today, today), ReportPeriod.of(ReportPreset.TODAY, today))
        assertEquals(ReportPeriod(LocalDate.of(2026, 9, 24), today), ReportPeriod.of(ReportPreset.WEEK, today)) // "24–30 Sep"
        assertEquals(ReportPeriod(LocalDate.of(2026, 9, 1), today), ReportPeriod.of(ReportPreset.MONTH, today))
        assertEquals(7, ReportPeriod.of(ReportPreset.WEEK, today).days.size)
    }

    @Test
    fun `stats, daily totals and paid-by come from DONE sales only`() {
        val period = ReportPeriod.of(ReportPreset.WEEK, today)
        val sales = listOf(
            sale(1, "2026-09-29", total = 6_300, cost = 5_000),
            sale(2, "2026-09-30", total = 3_000, cost = 2_100, method = PayMethod.MOBILE_MONEY),
            sale(3, "2026-09-30", total = 700, cost = 600, method = PayMethod.CREDIT),
        )
        val report = Reports.build(period, sales, items = emptyList())

        assertEquals(ReportStats(sales = 10_000, profit = 2_300, count = 3), report.stats)
        assertEquals(3_333L, report.stats.average)
        // Every day of the period has a bar, quiet days at zero.
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 6_300L, 3_700L), report.daily.map { it.total })
        assertEquals(PaidBy(cash = 6_300, mobile = 3_000, credit = 700), report.paidBy)
        assertEquals(listOf(63, 30, 7), report.paidBy.percentages())
    }

    @Test
    fun `paid-by percentages always add up to 100`() {
        assertEquals(listOf(34, 33, 33), PaidBy(1, 1, 1).percentages())
        assertEquals(listOf(100, 0, 0), PaidBy(5_000, 0, 0).percentages())
        assertEquals(listOf(0, 0, 0), PaidBy(0, 0, 0).percentages())
    }

    @Test
    fun `top products rank by amount, keep fractions of kilos and take the latest name`() {
        val items = listOf(
            item(1, 10, "Sugar 1kg", qty = 2.0, price = 5_000),
            item(2, 11, "Beans", qty = 2.5, price = 3_000), // 7,500
            item(3, 10, "Sugar (1 kg)", qty = 1.0, price = 5_000),
            item(3, 12, "Salt 500g", qty = 1.0, price = 1_000),
        )
        val top = Reports.topProducts(items, limit = 2)
        assertEquals(listOf("Sugar (1 kg)" to 15_000L, "Beans" to 7_500L), top.map { it.name to it.amount })
        assertEquals(3.0, top.first().qty, 0.0)
    }
}
