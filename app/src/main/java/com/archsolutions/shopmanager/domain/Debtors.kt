package com.archsolutions.shopmanager.domain

import com.archsolutions.cloud.contract.shop.CreditSaleIn
import com.archsolutions.cloud.contract.shop.RepaymentIn
import com.archsolutions.shopmanager.data.db.entities.CustomerEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.cloud.contract.shop.Debtors as ContractDebtors

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
    const val OVERDUE_DAYS = ContractDebtors.OVERDUE_DAYS

    /**
     * owed = Σ DONE credit sales − Σ repayments, customers with owed > 0 only, oldest debt first.
     * [creditSales] must hold DONE credit sales only. The rules live in cloud-contract; keys are customer ids.
     */
    fun compute(
        customers: List<CustomerEntity>,
        creditSales: List<SaleEntity>,
        repayments: List<MoneyEntryEntity>,
    ): List<Debtor> {
        val byKey = customers.associateBy { it.id.toString() }
        val lines = ContractDebtors.compute(
            byKey.keys,
            creditSales.mapNotNull { s -> s.customerId?.let { CreditSaleIn(it.toString(), s.total, s.createdAt) } },
            repayments.mapNotNull { r -> r.customerId?.let { RepaymentIn(it.toString(), r.amount, r.createdAt) } },
        )
        return lines.map { Debtor(byKey.getValue(it.customerKey), it.owed, it.sinceMillis, it.lastPaidMillis) }
    }
}
