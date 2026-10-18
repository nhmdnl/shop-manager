package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.PurchaseEntity
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.testutil.testDay
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    private val day = testDay("2026-09-30", DayStatus.OPEN).copy(openingCash = 150_000L, openingMobile = 40_000L)

    private fun sale(total: Long, method: PayMethod, status: SaleStatus = SaleStatus.DONE, cost: Long = total - 1_000) =
        SaleEntity(dayId = 1, createdAt = 0L, total = total, costTotal = cost, method = method, customerId = if (method == PayMethod.CREDIT) 1 else null, status = status)

    private fun entry(type: MoneyEntryType, method: MoneyMethod, amount: Long) =
        MoneyEntryEntity(dayId = 1, createdAt = 0L, type = type, method = method, amount = amount)

    private fun purchase(total: Long, paid: PurchasePaid) =
        PurchaseEntity(dayId = 1, supplierId = null, createdAt = 0L, total = total, paid = paid)

    @Test
    fun `worked example 6_2`() {
        val balance = BalanceCalculator.compute(
            day = day,
            sales = listOf(
                sale(100_000L, PayMethod.CASH),
                sale(26_800L, PayMethod.CASH),
                sale(59_700L, PayMethod.MOBILE_MONEY),
                sale(12_000L, PayMethod.CREDIT),
                // Voided sales count nowhere.
                sale(9_000L, PayMethod.CASH, SaleStatus.VOIDED),
            ),
            entries = listOf(
                entry(MoneyEntryType.CREDIT_REPAYMENT, MoneyMethod.CASH, 20_000L),
                entry(MoneyEntryType.EXPENSE, MoneyMethod.CASH, 18_500L),
                entry(MoneyEntryType.EXPENSE, MoneyMethod.MOBILE_MONEY, 5_000L),
            ),
            purchases = listOf(
                purchase(82_400L, PurchasePaid.CASH),
                // "Not paid yet" never reduces a channel.
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
        assertEquals(BalanceStatus.SHORT, BalanceCalculator.status(cashDifference, allowedDifference = 500L))
        assertEquals(BalanceStatus.BALANCED, BalanceCalculator.status(94_700L - balance.mobile.expected, allowedDifference = 500L))
    }

    @Test
    fun `summary text matches spec 6_6`() {
        val balance = BalanceCalculator.compute(
            day = day,
            sales = listOf(
                sale(126_800L, PayMethod.CASH, cost = 100_000L),
                sale(59_700L, PayMethod.MOBILE_MONEY, cost = 50_000L),
                sale(12_000L, PayMethod.CREDIT, cost = 10_000L),
            ),
            entries = listOf(
                entry(MoneyEntryType.CREDIT_REPAYMENT, MoneyMethod.CASH, 20_000L),
                entry(MoneyEntryType.EXPENSE, MoneyMethod.CASH, 18_500L),
                entry(MoneyEntryType.EXPENSE, MoneyMethod.MOBILE_MONEY, 5_000L),
            ),
            purchases = listOf(purchase(82_400L, PurchasePaid.CASH)),
        )
        // English templates, as in values/strings.xml.
        val templates = SummaryTemplates(
            title = "%1\$s — %2\$s",
            sales = "Sales: %1\$s (Cash %2\$s · MoMo %3\$s · Credit %4\$s)",
            costs = "Expenses: %1\$s · Purchases: %2\$s",
            channel = "%1\$s: expected %2\$s, counted %3\$s (%4\$s)",
            short = "short %1\$s",
            over = "over %1\$s",
            balanced = "balanced",
            withReason = "%1\$s — %2\$s",
            profit = "Profit (est.): %1\$s",
            cashLabel = "Cash",
            mobileLabel = "Mobile Money",
        )

        val text = DaySummary.build(
            templates = templates,
            shopName = "Mary Shop",
            dateText = "Wed 30 Sep 2026",
            balance = balance,
            countedCash = 195_000L,
            countedMobile = 94_700L,
            allowedDifference = 500L,
            reason = "Change error",
        )

        assertEquals(
            """
            Mary Shop — Wed 30 Sep 2026
            Sales: UGX 198,500 (Cash 126,800 · MoMo 59,700 · Credit 12,000)
            Expenses: UGX 23,500 · Purchases: UGX 82,400
            Cash: expected 195,900, counted 195,000 (short 900 — Change error)
            Mobile Money: expected 94,700, counted 94,700 (balanced)
            Profit (est.): UGX 38,500
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `cash in and cash out move the expected amount`() {
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
        assertEquals(BalanceStatus.OVER, BalanceCalculator.status(600L, allowedDifference = 500L))
        assertEquals(BalanceStatus.BALANCED, BalanceCalculator.status(-500L, allowedDifference = 500L))
    }

    @Test
    fun `debtors owe credit sales minus repayments, dated by the oldest unpaid sale`() {
        val joseph = CustomerEntity(id = 1, name = "Joseph K.", phone = null, note = null)
        val sarah = CustomerEntity(id = 2, name = "Sarah N.", phone = null, note = null)
        val grace = CustomerEntity(id = 3, name = "Grace A.", phone = null, note = null)
        fun credit(customer: Long, total: Long, at: Long, status: SaleStatus = SaleStatus.DONE) =
            SaleEntity(dayId = 1, createdAt = at, total = total, costTotal = 0, method = PayMethod.CREDIT, customerId = customer, status = status)
        fun repay(customer: Long, amount: Long, at: Long) =
            MoneyEntryEntity(dayId = 1, createdAt = at, type = MoneyEntryType.CREDIT_REPAYMENT, method = MoneyMethod.CASH, amount = amount, customerId = customer)

        val debtors = Debtors.compute(
            customers = listOf(joseph, sarah, grace),
            creditSales = listOf(
                credit(1, 10_000L, at = 100L),
                credit(1, 30_000L, at = 200L),
                credit(2, 5_000L, at = 50L),
                credit(3, 12_000L, at = 300L),
            ),
            repayments = listOf(repay(1, 15_000L, at = 250L), repay(2, 5_000L, at = 60L)),
        )

        // Joseph's 15,000 covers the first sale and part of the second, so his debt dates from 200.
        // Sarah paid everything and is not listed; the oldest debt comes first.
        assertEquals(listOf("Joseph K.", "Grace A."), debtors.map { it.customer.name })
        assertEquals(25_000L, debtors[0].owed)
        assertEquals(200L, debtors[0].sinceMillis)
        assertEquals(250L, debtors[0].lastPaidMillis)
        assertEquals(12_000L, debtors[1].owed)
        assertEquals(null, debtors[1].lastPaidMillis)
    }
}
