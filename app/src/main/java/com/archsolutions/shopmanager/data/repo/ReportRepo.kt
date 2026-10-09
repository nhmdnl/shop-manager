package com.archsolutions.shopmanager.data.repo

import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import javax.inject.Inject
import javax.inject.Singleton

data class PeriodReportStats(
    val totalSales: Long,
    val profit: Long,
    val numberOfSales: Int,
    val averageSale: Long,
    val cashSales: Long,
    val mobileMoneySales: Long,
    val creditSales: Long,
)

data class ProductSaleSummary(
    val productId: Long,
    val productName: String,
    val totalQtySold: Double,
    val totalSalesAmount: Long,
)

@Singleton
class ReportRepo @Inject constructor(
    private val db: AppDatabase,
) {
    private val saleDao = db.saleDao()

    suspend fun getStatsForPeriod(startMillis: Long, endMillis: Long): PeriodReportStats {
        val sales = saleDao.getSalesBetweenDirect(startMillis, endMillis)
        val totalSales = sales.sumOf { it.total }
        val costTotal = sales.sumOf { it.costTotal }
        val profit = totalSales - costTotal
        val count = sales.size
        val avg = if (count > 0) totalSales / count else 0L

        val cashSales = sales.filter { it.method == PayMethod.CASH }.sumOf { it.total }
        val momoSales = sales.filter { it.method == PayMethod.MOBILE_MONEY }.sumOf { it.total }
        val creditSales = sales.filter { it.method == PayMethod.CREDIT }.sumOf { it.total }

        return PeriodReportStats(
            totalSales = totalSales,
            profit = profit,
            numberOfSales = count,
            averageSale = avg,
            cashSales = cashSales,
            mobileMoneySales = momoSales,
            creditSales = creditSales,
        )
    }

    suspend fun getTopProductsForPeriod(startMillis: Long, endMillis: Long): List<ProductSaleSummary> {
        val sales = saleDao.getSalesBetweenDirect(startMillis, endMillis)
        val allItems = sales.flatMap { saleDao.getSaleItems(it.id) }

        return allItems
            .groupBy { it.productId to it.productName }
            .map { (key, items) ->
                val (productId, productName) = key
                ProductSaleSummary(
                    productId = productId,
                    productName = productName,
                    totalQtySold = items.sumOf { it.qty },
                    totalSalesAmount = items.sumOf { (it.qty * it.unitPrice).toLong() },
                )
            }
            .sortedByDescending { it.totalSalesAmount }
    }
}
