package com.archsolutions.shopmanager.domain

import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.SaleEntity

/** A customer who owes the shop (addendum B). */
data class Debtor(
    val customer: CustomerEntity,
    val owed: Long,
    /** Oldest credit sale not yet covered by repayments (FIFO). */
    val sinceMillis: Long,
    val lastPaidMillis: Long?,
)

object Debtors {
    /** Days after which the "since" line turns rust (addendum B). */
    const val OVERDUE_DAYS = 14

    /**
     * owed = Σ DONE credit sales − Σ repayments, customers with owed > 0 only, oldest debt first.
     * [creditSales] must hold DONE credit sales only.
     */
    fun compute(
        customers: List<CustomerEntity>,
        creditSales: List<SaleEntity>,
        repayments: List<MoneyEntryEntity>,
    ): List<Debtor> {
        val salesByCustomer = creditSales.filter { it.customerId != null }.groupBy { it.customerId!! }
        val paymentsByCustomer = repayments.filter { it.customerId != null }.groupBy { it.customerId!! }
        return customers.mapNotNull { customer ->
            val sales = salesByCustomer[customer.id].orEmpty().sortedBy { it.createdAt }
            val payments = paymentsByCustomer[customer.id].orEmpty()
            val repaid = payments.sumOf { it.amount }
            val owed = sales.sumOf { it.total } - repaid
            if (owed <= 0) return@mapNotNull null
            // Repayments pay off the oldest sales first; the first sale not fully covered dates the debt.
            var covered = repaid
            val since = sales.first { sale ->
                covered -= sale.total
                covered < 0
            }.createdAt
            Debtor(customer, owed, since, payments.maxOfOrNull { it.createdAt })
        }.sortedBy { it.sinceMillis }
    }
}
