package com.archsolutions.shopmanager.data.db.entities

enum class Unit { PCS, KG, LITRE, PACK }
enum class PayMethod { CASH, MOBILE_MONEY, CREDIT }          // CREDIT only for sales
enum class MoneyMethod { CASH, MOBILE_MONEY }                // real money channels
enum class SaleStatus { DONE, VOIDED }
enum class MovementType { SALE, PURCHASE, ADJUSTMENT, VOID_RETURN }
enum class MoneyEntryType { EXPENSE, CREDIT_REPAYMENT, CASH_OUT, CASH_IN }
enum class PurchasePaid { CASH, MOBILE_MONEY, NOT_PAID }
enum class DayStatus { OPEN, CLOSED }
enum class CountPhase { OPEN, CLOSE }
