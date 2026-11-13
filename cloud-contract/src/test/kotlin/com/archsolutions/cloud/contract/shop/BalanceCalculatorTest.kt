package com.archsolutions.cloud.contract.shop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BalanceCalculatorTest {
    private val day = DayIn(openingCash = 150_000L, openingMobile = 40_000L)

    private fun sale(total: Long, method: PayMethod, status: SaleStatus = SaleStatus.DONE, cost: Long = total - 1_000) =
        SaleIn(total, cost, method, status)

    private fun entry(type: MoneyEntryType, method: MoneyMethod, amount: Long) = MoneyEntryIn(type, method, amount)

    private fun purchase(total: Long, paid: PurchasePaid) = PurchaseIn(total, paid)

    @Test fun `worked example 6_2`() {
        val balance = BalanceCalculator.compute(
            day = day,
            sales = listOf(
                sale(100_000L, PayMethod.CASH),
                sale(26_800L, PayMethod.CASH),
                sale(59_700L, PayMethod.MOBILE_MONEY),
                sale(12_000L, PayMethod.CREDIT),
                sale(5_000L, PayMethod.CASH, SaleStatus.VOIDED),
            ),
            entries = listOf(
                entry(MoneyEntryType.CREDIT_REPAYMENT, MoneyMethod.CASH, 20_000L),
                entry(MoneyEntryType.EXPENSE, MoneyMethod.CASH, 18_500L),
                entry(MoneyEntryType.EXPENSE, MoneyMethod.MOBILE_MONEY, 5_000L),
            ),
            purchases = listOf(
                purchase(82_400L, PurchasePaid.CASH),
                purchase(30_000L, PurchasePaid.NOT_PAID),
            ),
        )
        assertEquals(195_900L, balance.cash.expected)
        assertEquals(94_700L, balance.mobile.expected)
        assertEquals(198_500L, balance.totalSales)
        assertEquals(12_000L, balance.creditSales)
        assertEquals(23_500L, balance.expensesTotal)
        assertEquals(112_400L, balance.purchasesTotal)
        assertEquals(4, balance.saleCount)

        val cashDifference = 195_000L - balance.cash.expected
        assertEquals(-900L, cashDifference)
        assertEquals(BalanceStatus.SHORT, BalanceCalculator.status(cashDifference, 500L))
        assertEquals(BalanceStatus.BALANCED, BalanceCalculator.status(94_700L - balance.mobile.expected, 500L))
    }

    @Test fun `profit counts done sales only`() {
        val balance = BalanceCalculator.compute(
            day = day,
            sales = listOf(
                sale(126_800L, PayMethod.CASH, cost = 100_000L),
                sale(12_000L, PayMethod.CREDIT, cost = 10_000L),
                sale(9_000L, PayMethod.CASH, SaleStatus.VOIDED, cost = 1_000L),
            ),
            entries = emptyList(),
            purchases = emptyList(),
        )
        assertEquals(26_800L + 2_000L, balance.profit)
    }

    @Test fun `cash in and cash out move the expected amount`() {
        val balance = BalanceCalculator.compute(
            day = day,
            sales = emptyList(),
            entries = listOf(
                entry(MoneyEntryType.CASH_IN, MoneyMethod.CASH, 50_000L),
                entry(MoneyEntryType.CASH_OUT, MoneyMethod.MOBILE_MONEY, 10_000L),
            ),
            purchases = emptyList(),
        )
        assertEquals(200_000L, balance.cash.expected)
        assertEquals(30_000L, balance.mobile.expected)
        assertEquals(BalanceStatus.OVER, BalanceCalculator.status(600L, 500L))
        assertEquals(BalanceStatus.BALANCED, BalanceCalculator.status(-500L, 500L))
    }

    @Test fun `a purchase not paid yet changes neither cash nor mobile money`() {
        val balance = BalanceCalculator.compute(
            day = day,
            sales = emptyList(),
            entries = emptyList(),
            purchases = listOf(purchase(82_400L, PurchasePaid.NOT_PAID), purchase(5_000L, PurchasePaid.MOBILE_MONEY)),
        )
        assertEquals(day.openingCash, balance.cash.expected)
        assertEquals(0L, balance.cash.purchasesPaid)
        assertEquals(day.openingMobile - 5_000L, balance.mobile.expected)
        assertEquals(87_400L, balance.purchasesTotal)
    }

    @Test fun `status edges`() {
        assertEquals(BalanceStatus.SHORT, BalanceCalculator.status(195_000L - 195_900L, 500L))
        assertEquals(BalanceStatus.BALANCED, BalanceCalculator.status(-900L, 1_000L))
        assertEquals(BalanceStatus.BALANCED, BalanceCalculator.status(0L, 0L))
        assertEquals(BalanceStatus.OVER, BalanceCalculator.status(1L, 0L))
    }
}
