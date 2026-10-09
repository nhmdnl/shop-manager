package com.archsolutions.shopmanager.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "product",
    indices = [Index("categoryId"), Index("barcode"), Index("name")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long?,
    val unit: Unit = Unit.PCS,
    val sellPrice: Long,
    val costPrice: Long, // latest purchase cost, updated by purchases
    val stockQty: Double = 0.0, // cached; always equals SUM(stock_movement.qtyChange)
    val lowStockLevel: Double = 0.0,
    val barcode: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "supplier")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactPerson: String?,
    val phone: String?,
    val whatsapp: String?,
    val location: String?,
    val notes: String?,
    val active: Boolean = true,
)

@Entity(
    tableName = "product_supplier",
    primaryKeys = ["productId", "supplierId"],
    indices = [Index("supplierId")],
)
data class ProductSupplierEntity(
    val productId: Long,
    val supplierId: Long,
    val supplierCost: Long?,
    val isPreferred: Boolean = false,
)

@Entity(tableName = "customer")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String?,
    val note: String?,
)

@Entity(
    tableName = "business_day",
    indices = [Index(value = ["date"], unique = true)],
)
data class BusinessDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val status: DayStatus,
    val openedAt: Long,
    val openingCash: Long,
    val openingMobile: Long,
    val closedAt: Long? = null,
    val expectedCash: Long? = null,
    val expectedMobile: Long? = null,
    val countedCash: Long? = null,
    val countedMobile: Long? = null,
    val diffCash: Long? = null,
    val diffMobile: Long? = null,
    val diffReason: String? = null,
    val closeNote: String? = null,
    val reopenedCount: Int = 0,
)

@Entity(
    tableName = "cash_count",
    primaryKeys = ["dayId", "phase", "denomination"],
)
data class CashCountEntity(
    val dayId: Long,
    val phase: CountPhase,
    val denomination: Long, // 0 = coins total entered as an amount in `count` shillings
    val count: Int,
)

@Entity(
    tableName = "sale",
    indices = [Index("dayId"), Index("createdAt"), Index("customerId")],
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val createdAt: Long,
    val total: Long,
    val costTotal: Long,
    val method: PayMethod,
    val customerId: Long? = null, // required when method == CREDIT
    val cashReceived: Long? = null, // for change calculation
    val status: SaleStatus = SaleStatus.DONE,
    val voidReason: String? = null,
    val voidedAt: Long? = null,
)

@Entity(
    tableName = "sale_item",
    indices = [Index("saleId"), Index("productId")],
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val qty: Double,
    val unitPrice: Long,
    val unitCost: Long,
)

@Entity(
    tableName = "purchase",
    indices = [Index("dayId"), Index("supplierId")],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val supplierId: Long?,
    val createdAt: Long,
    val total: Long,
    val paid: PurchasePaid,
    val note: String? = null,
)

@Entity(
    tableName = "purchase_item",
    indices = [Index("purchaseId"), Index("productId")],
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val qty: Double,
    val unitCost: Long,
)

@Entity(
    tableName = "stock_movement",
    indices = [Index("productId"), Index("createdAt")],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val type: MovementType,
    val qtyChange: Double,
    val refId: Long?,
    val reason: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "money_entry",
    indices = [Index("dayId"), Index("customerId")],
)
data class MoneyEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val createdAt: Long,
    val type: MoneyEntryType,
    val method: MoneyMethod,
    val amount: Long, // always positive
    val category: String? = null, // EXPENSE: Transport, Rent, Power, Water, Lunch, Cleaning, Airtime, Other
    val customerId: Long? = null, // CREDIT_REPAYMENT
    val note: String? = null,
)
