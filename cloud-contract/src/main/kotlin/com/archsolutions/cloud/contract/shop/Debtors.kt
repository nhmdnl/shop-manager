package com.archsolutions.cloud.contract.shop

/** A DONE credit sale; [customerKey] is whatever id the caller uses for the customer (row id in the app, uid on the server). */
data class CreditSaleIn(val customerKey: String, val total: Long, val createdAt: Long)

data class RepaymentIn(val customerKey: String, val amount: Long, val createdAt: Long)

/** A customer who owes the shop. [sinceMillis] is the oldest credit sale not yet covered by repayments (FIFO). */
data class DebtorLine(val customerKey: String, val owed: Long, val sinceMillis: Long, val lastPaidMillis: Long?)

object Debtors {
    /** Days after which the "since" line turns rust. */
    const val OVERDUE_DAYS = 14

    /**
     * owed = Σ DONE credit sales − Σ repayments, customers with owed > 0 only, oldest debt first.
     * [creditSales] must hold DONE credit sales only.
     */
    fun compute(
        customerKeys: Collection<String>,
        creditSales: List<CreditSaleIn>,
        repayments: List<RepaymentIn>,
    ): List<DebtorLine> {
        val salesByCustomer = creditSales.groupBy { it.customerKey }
        val paymentsByCustomer = repayments.groupBy { it.customerKey }
        return customerKeys.mapNotNull { key ->
            val sales = salesByCustomer[key].orEmpty().sortedBy { it.createdAt }
            val payments = paymentsByCustomer[key].orEmpty()
            val repaid = payments.sumOf { it.amount }
            val owed = sales.sumOf { it.total } - repaid
            if (owed <= 0) return@mapNotNull null
            // Repayments pay off the oldest sales first; the first sale not fully covered dates the debt.
            var covered = repaid
            val since = sales.first { sale ->
                covered -= sale.total
                covered < 0
            }.createdAt
            DebtorLine(key, owed, since, payments.maxOfOrNull { it.createdAt })
        }.sortedBy { it.sinceMillis }
    }
}
