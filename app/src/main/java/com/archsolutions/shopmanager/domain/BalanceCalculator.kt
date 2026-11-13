package com.archsolutions.shopmanager.domain

import com.archsolutions.cloud.contract.shop.DayIn
import com.archsolutions.cloud.contract.shop.MoneyEntryIn
import com.archsolutions.cloud.contract.shop.PurchaseIn
import com.archsolutions.cloud.contract.shop.SaleIn
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryEntity
import com.archsolutions.shopmanager.data.db.entities.PurchaseEntity
import com.archsolutions.shopmanager.data.db.entities.SaleEntity
import com.archsolutions.cloud.contract.shop.BalanceCalculator as ContractBalanceCalculator
import com.archsolutions.cloud.contract.shop.MoneyEntryType as ContractMoneyEntryType
import com.archsolutions.cloud.contract.shop.MoneyMethod as ContractMoneyMethod
import com.archsolutions.cloud.contract.shop.PayMethod as ContractPayMethod
import com.archsolutions.cloud.contract.shop.PurchasePaid as ContractPurchasePaid
import com.archsolutions.cloud.contract.shop.SaleStatus as ContractSaleStatus

/** One money channel (cash or Mobile Money) for a day — the "Check" table rows of 6.2. */
typealias ChannelBalance = com.archsolutions.cloud.contract.shop.ChannelBalance
typealias DayBalance = com.archsolutions.cloud.contract.shop.DayBalance
typealias BalanceStatus = com.archsolutions.cloud.contract.shop.BalanceStatus

/**
 * Daily balance (spec 6.1). Voided sales count nowhere; unpaid purchases reduce no channel.
 * The arithmetic lives in cloud-contract so the server computes the same figures; this maps entities to its inputs.
 */
object BalanceCalculator {
    fun compute(
        day: BusinessDayEntity,
        sales: List<SaleEntity>,
        entries: List<MoneyEntryEntity>,
        purchases: List<PurchaseEntity>,
    ): DayBalance = ContractBalanceCalculator.compute(
        DayIn(day.openingCash, day.openingMobile),
        sales.map { SaleIn(it.total, it.costTotal, ContractPayMethod.valueOf(it.method.name), ContractSaleStatus.valueOf(it.status.name)) },
        entries.map { MoneyEntryIn(ContractMoneyEntryType.valueOf(it.type.name), ContractMoneyMethod.valueOf(it.method.name), it.amount) },
        purchases.map { PurchaseIn(it.total, ContractPurchasePaid.valueOf(it.paid.name)) },
    )

    /** 6.1: Balanced within the allowed difference, otherwise Short or Over. */
    fun status(difference: Long, allowedDifference: Long): BalanceStatus =
        ContractBalanceCalculator.status(difference, allowedDifference)
}
