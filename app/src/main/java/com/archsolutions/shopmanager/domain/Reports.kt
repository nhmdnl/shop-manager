package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.dao.ReportItemRow
import com.archsolutions.shopmanager.data.db.dao.ReportSaleRow
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import java.time.LocalDate
import kotlin.math.roundToLong

enum class ReportPreset { TODAY, WEEK, MONTH, CUSTOM }

/** Business-day dates, both ends included. */
data class ReportPeriod(val from: LocalDate, val to: LocalDate) {
    val days: List<LocalDate> get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    companion object {
        /** Today; the last 7 days up to today (the design's "24–30 Sep"); this month up to today. */
        fun of(preset: ReportPreset, today: LocalDate): ReportPeriod = when (preset) {
            ReportPreset.TODAY -> ReportPeriod(today, today)
            ReportPreset.WEEK -> ReportPeriod(today.minusDays(6), today)
            ReportPreset.MONTH, ReportPreset.CUSTOM -> ReportPeriod(today.withDayOfMonth(1), today)
        }
    }
}

data class ReportStats(val sales: Long, val profit: Long, val count: Int) {
    val average: Long get() = if (count == 0) 0L else (sales.toDouble() / count).roundToLong()
}

data class DailyTotal(val date: LocalDate, val total: Long)

data class PaidBy(val cash: Long, val mobile: Long, val credit: Long) {
    val total: Long get() = cash + mobile + credit

    /** Whole percentages for cash, mobile and credit that add up to 100 (largest remainder); zeros without sales. */
    fun percentages(): List<Int> {
        if (total <= 0L) return listOf(0, 0, 0)
        val exact = listOf(cash, mobile, credit).map { it * 100.0 / total }
        val whole = exact.map { it.toInt() }.toMutableList()
        exact.indices
            .sortedByDescending { exact[it] - whole[it] }
            .take(100 - whole.sum())
            .forEach { whole[it]++ }
        return whole
    }
}

data class TopProduct(val productId: Long, val name: String, val qty: Double, val amount: Long)

data class ReportData(
    val period: ReportPeriod,
    val stats: ReportStats,
    val daily: List<DailyTotal>,
    val paidBy: PaidBy,
    val top: List<TopProduct>,
)

data class ClosedDayRow(val day: BusinessDayEntity, val sales: Long)

object Reports {
    const val TOP_PRODUCTS = 5

    /** Section 10: everything over DONE sales; profit = Σ(total − costTotal). */
    fun build(period: ReportPeriod, sales: List<ReportSaleRow>, items: List<ReportItemRow>): ReportData {
        val byDate = sales.groupBy { it.date }
        fun paid(method: PayMethod) = sales.filter { it.method == method }.sumOf { it.total }
        return ReportData(
            period = period,
            stats = ReportStats(sales = sales.sumOf { it.total }, profit = sales.sumOf { it.total - it.costTotal }, count = sales.size),
            daily = period.days.map { date -> DailyTotal(date, byDate[date.toString()].orEmpty().sumOf { it.total }) },
            paidBy = PaidBy(cash = paid(PayMethod.CASH), mobile = paid(PayMethod.MOBILE_MONEY), credit = paid(PayMethod.CREDIT)),
            top = topProducts(items),
        )
    }

    fun topProducts(items: List<ReportItemRow>, limit: Int = TOP_PRODUCTS): List<TopProduct> =
        items.groupBy { it.productId }
            .map { (productId, rows) ->
                // The latest name, in case the product was renamed during the period.
                TopProduct(productId, rows.last().productName, rows.sumOf { it.qty }, rows.sumOf { it.qty * it.unitPrice }.roundToLong())
            }
            .sortedWith(compareByDescending<TopProduct> { it.amount }.thenBy { it.name })
            .take(limit)

    fun closedDays(days: List<BusinessDayEntity>, sales: List<ReportSaleRow>): List<ClosedDayRow> {
        val byDate = sales.groupBy { it.date }
        return days.map { day -> ClosedDayRow(day, byDate[day.date].orEmpty().sumOf { it.total }) }
    }
}
