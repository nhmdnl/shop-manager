package com.archsolutions.cloud.contract.shop

import kotlinx.serialization.Serializable

/** Names of the synced tables, parent first. Suppliers are not synced. */
object SyncTables {
    const val CATEGORY = "category"
    const val PRODUCT = "product"
    const val CUSTOMER = "customer"
    const val BUSINESS_DAY = "business_day"
    const val SALE = "sale"
    const val SALE_ITEM = "sale_item"
    const val MONEY_ENTRY = "money_entry"
    const val PURCHASE = "purchase"

    val ALL: List<String> = listOf(CATEGORY, PRODUCT, CUSTOMER, BUSINESS_DAY, SALE, SALE_ITEM, MONEY_ENTRY, PURCHASE)
}

// Row data as it travels in SyncRow.data. The uid sits on SyncRow; foreign keys are uid strings.

@Serializable
data class CategoryRow(val name: String, val sortOrder: Int)

@Serializable
data class ProductRow(
    val name: String,
    val categoryUid: String?,
    val unit: StockUnit,
    val sellPrice: Long,
    val costPrice: Long,
    val stockQty: Double,
    val lowStockLevel: Double,
    val barcode: String?,
    val active: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CustomerRow(val name: String, val phone: String?, val note: String?)

@Serializable
data class BusinessDayRow(
    val date: String,
    val status: DayStatus,
    val openedAt: Long,
    val openingCash: Long,
    val openingMobile: Long,
    val closedAt: Long?,
    val expectedCash: Long?,
    val expectedMobile: Long?,
    val countedCash: Long?,
    val countedMobile: Long?,
    val diffCash: Long?,
    val diffMobile: Long?,
    val diffReason: String?,
    val closeNote: String?,
    val reopenedCount: Int,
)

@Serializable
data class SaleRow(
    val dayUid: String,
    val createdAt: Long,
    val total: Long,
    val costTotal: Long,
    val method: PayMethod,
    val customerUid: String?,
    val cashReceived: Long?,
    val status: SaleStatus,
    val voidReason: String?,
    val voidedAt: Long?,
)

@Serializable
data class SaleItemRow(
    val saleUid: String,
    val productUid: String,
    val productName: String,
    val qty: Double,
    val unitPrice: Long,
    val unitCost: Long,
)

@Serializable
data class MoneyEntryRow(
    val dayUid: String,
    val createdAt: Long,
    val type: MoneyEntryType,
    val method: MoneyMethod,
    val amount: Long,
    val category: String?,
    val customerUid: String?,
    val note: String?,
)

@Serializable
data class PurchaseRow(
    val dayUid: String,
    val createdAt: Long,
    val total: Long,
    val paid: PurchasePaid,
    val note: String?,
)
