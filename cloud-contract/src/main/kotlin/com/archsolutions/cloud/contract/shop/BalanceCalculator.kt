package com.archsolutions.cloud.contract.shop

import kotlin.math.abs

data class DayIn(val openingCash: Long, val openingMobile: Long)
data class SaleIn(val total: Long, val costTotal: Long, val method: PayMethod, val status: SaleStatus)
data class MoneyEntryIn(val type: MoneyEntryType, val method: MoneyMethod, val amount: Long)
data class PurchaseIn(val total: Long, val paid: PurchasePaid)

/** One money channel (cash or Mobile Money) for a day. */
data class ChannelBalance(
    val opening: Long,
    val sales: Long,
    val creditRepaid: Long,
    val cashIn: Long,
    val expenses: Long,
    val cashOut: Long,
    val purchasesPaid: Long,
) {
    /** What the drawer (or the phone) should hold. */
    val expected: Long get() = opening + sales + creditRepaid + cashIn - expenses - cashOut - purchasesPaid
}

enum class BalanceStatus { BALANCED, SHORT, OVER }

data class DayBalance(
    val cash: ChannelBalance,
    val mobile: ChannelBalance,
    val creditSales: Long,
    val purchasesTotal: Long,
    val profit: Long,
    val saleCount: Int,
) {
    /** Total sales include credit; credit never reaches cash or Mobile Money. */
    val totalSales: Long get() = cash.sales + mobile.sales + creditSales
    val expensesTotal: Long get() = cash.expenses + mobile.expenses
}

/** Daily balance, the same arithmetic as the app's domain/BalanceCalculator. Voided sales count nowhere; unpaid purchases reduce no channel. */
object BalanceCalculator {
    fun compute(
        day: DayIn,
        sales: List<SaleIn>,
        entries: List<MoneyEntryIn>,
        purchases: List<PurchaseIn>,
    ): DayBalance {
        val done = sales.filter { it.status == SaleStatus.DONE }

        fun channel(method: MoneyMethod, opening: Long, payMethod: PayMethod, paid: PurchasePaid) = ChannelBalance(
            opening = opening,
            sales = done.filter { it.method == payMethod }.sumOf { it.total },
            creditRepaid = entries.sumFor(MoneyEntryType.CREDIT_REPAYMENT, method),
            cashIn = entries.sumFor(MoneyEntryType.CASH_IN, method),
            expenses = entries.sumFor(MoneyEntryType.EXPENSE, method),
            cashOut = entries.sumFor(MoneyEntryType.CASH_OUT, method),
            purchasesPaid = purchases.filter { it.paid == paid }.sumOf { it.total },
        )

        return DayBalance(
            cash = channel(MoneyMethod.CASH, day.openingCash, PayMethod.CASH, PurchasePaid.CASH),
            mobile = channel(MoneyMethod.MOBILE_MONEY, day.openingMobile, PayMethod.MOBILE_MONEY, PurchasePaid.MOBILE_MONEY),
            creditSales = done.filter { it.method == PayMethod.CREDIT }.sumOf { it.total },
            purchasesTotal = purchases.sumOf { it.total },
            profit = done.sumOf { it.total - it.costTotal },
            saleCount = done.size,
        )
    }

    /** Balanced within the allowed difference, otherwise Short or Over. */
    fun status(difference: Long, allowedDifference: Long): BalanceStatus = when {
        abs(difference) <= allowedDifference -> BalanceStatus.BALANCED
        difference < 0 -> BalanceStatus.SHORT
        else -> BalanceStatus.OVER
    }

    private fun List<MoneyEntryIn>.sumFor(type: MoneyEntryType, method: MoneyMethod): Long =
        filter { it.type == type && it.method == method }.sumOf { it.amount }
}
