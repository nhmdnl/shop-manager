package com.archsolutions.cloud.contract.shop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DebtorsTest {
    @Test fun `debtors owe credit sales minus repayments, dated by the oldest unpaid sale`() {
        val debtors = Debtors.compute(
            customerKeys = listOf("joseph", "sarah", "grace"),
            creditSales = listOf(
                CreditSaleIn("joseph", 10_000L, 100L),
                CreditSaleIn("joseph", 30_000L, 200L),
                CreditSaleIn("sarah", 5_000L, 50L),
                CreditSaleIn("grace", 12_000L, 300L),
            ),
            repayments = listOf(RepaymentIn("joseph", 15_000L, 250L), RepaymentIn("sarah", 5_000L, 60L)),
        )
        assertEquals(listOf("joseph", "grace"), debtors.map { it.customerKey })
        assertEquals(25_000L, debtors[0].owed)
        assertEquals(200L, debtors[0].sinceMillis)
        assertEquals(250L, debtors[0].lastPaidMillis)
        assertEquals(12_000L, debtors[1].owed)
        assertNull(debtors[1].lastPaidMillis)
    }

    @Test fun `two sales and a part repayment leave the later sale as since`() {
        val debtors = Debtors.compute(
            customerKeys = listOf("a"),
            creditSales = listOf(CreditSaleIn("a", 8_000L, 3L), CreditSaleIn("a", 12_000L, 1L)),
            repayments = listOf(RepaymentIn("a", 15_000L, 5L)),
        )
        assertEquals(1, debtors.size)
        assertEquals(5_000L, debtors[0].owed)
        assertEquals(3L, debtors[0].sinceMillis)
    }

    @Test fun `lastPaidMillis is the newest repayment`() {
        val debtors = Debtors.compute(
            customerKeys = listOf("a"),
            creditSales = listOf(CreditSaleIn("a", 10_000L, 1L)),
            repayments = listOf(RepaymentIn("a", 1_000L, 9L), RepaymentIn("a", 1_000L, 4L)),
        )
        assertEquals(9L, debtors.single().lastPaidMillis)
    }

    @Test fun `fully repaid and unknown customers are absent, oldest debt first`() {
        val debtors = Debtors.compute(
            customerKeys = listOf("late", "early", "paid"),
            creditSales = listOf(
                CreditSaleIn("late", 1_000L, 900L),
                CreditSaleIn("early", 1_000L, 10L),
                CreditSaleIn("paid", 1_000L, 5L),
                CreditSaleIn("stranger", 1_000L, 1L),
            ),
            repayments = listOf(RepaymentIn("paid", 1_000L, 6L)),
        )
        assertEquals(listOf("early", "late"), debtors.map { it.customerKey })
    }
}
