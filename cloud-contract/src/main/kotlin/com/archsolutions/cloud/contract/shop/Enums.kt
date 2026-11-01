package com.archsolutions.cloud.contract.shop

import kotlinx.serialization.Serializable

/** The app's `Unit`, renamed so a star import never shadows `kotlin.Unit`. The wire value is the constant name. */
@Serializable enum class StockUnit { PCS, KG, LITRE, PACK }
@Serializable enum class PayMethod { CASH, MOBILE_MONEY, CREDIT }
@Serializable enum class MoneyMethod { CASH, MOBILE_MONEY }
@Serializable enum class SaleStatus { DONE, VOIDED }
@Serializable enum class MoneyEntryType { EXPENSE, CREDIT_REPAYMENT, CASH_OUT, CASH_IN }
@Serializable enum class PurchasePaid { CASH, MOBILE_MONEY, NOT_PAID }
@Serializable enum class DayStatus { OPEN, CLOSED }
