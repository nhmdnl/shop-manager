package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.db.entities.SyncOutboxEntity
import com.archsolutions.shopmanager.data.db.entities.Unit as ProductUnit
import com.archsolutions.shopmanager.data.db.entities.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun state(): SyncStateEntity

    @Query("UPDATE sync_state SET enabled = :enabled, fillId = :fillId, fillStartSeq = NULL WHERE id = 1")
    suspend fun setState(enabled: Boolean, fillId: String?)

    /** A Boolean, not a count: the watcher only needs empty to non-empty, and a COUNT(*) over a fill's entries would run after every acknowledgement. */
    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox)")
    fun hasOutbox(): Flow<Boolean>

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("SELECT * FROM sync_outbox ORDER BY seq LIMIT :limit")
    suspend fun peek(limit: Int): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE seq <= :seq")
    suspend fun deleteUpTo(seq: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE op = 'FILL_END')")
    suspend fun fillEndPending(): Boolean

    // Room can't take a table name as a parameter, so a full fill is one query per synced table.
    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'category', uid, 'UPSERT' FROM category")
    suspend fun enqueueCategories()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'product', uid, 'UPSERT' FROM product")
    suspend fun enqueueProducts()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'customer', uid, 'UPSERT' FROM customer")
    suspend fun enqueueCustomers()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'business_day', uid, 'UPSERT' FROM business_day")
    suspend fun enqueueDays()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'sale', uid, 'UPSERT' FROM sale")
    suspend fun enqueueSales()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'sale_item', uid, 'UPSERT' FROM sale_item")
    suspend fun enqueueSaleItems()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'money_entry', uid, 'UPSERT' FROM money_entry")
    suspend fun enqueueMoneyEntries()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) SELECT 'purchase', uid, 'UPSERT' FROM purchase")
    suspend fun enqueuePurchases()

    @Query("INSERT INTO sync_outbox (tbl, uid, op) VALUES ('', :fillId, 'FILL_END')")
    suspend fun enqueueFillEnd(fillId: String)

    @Query("UPDATE sync_state SET fillStartSeq = (SELECT MIN(seq) FROM sync_outbox) WHERE id = 1")
    suspend fun markFillStart()

    // Readers by uid, at most 500 uids per call (SQLite allows 999 variables on API 26). Foreign keys come back as uids.
    @Query("SELECT uid, name, sortOrder FROM category WHERE uid IN (:uids)")
    suspend fun categoriesByUid(uids: List<String>): List<CategoryWire>

    @Query(
        "SELECT p.uid AS uid, p.name AS name, c.uid AS categoryUid, p.unit AS unit, p.sellPrice AS sellPrice, p.costPrice AS costPrice, " +
            "p.stockQty AS stockQty, p.lowStockLevel AS lowStockLevel, p.barcode AS barcode, p.active AS active, " +
            "p.createdAt AS createdAt, p.updatedAt AS updatedAt " +
            "FROM product p LEFT JOIN category c ON c.id = p.categoryId WHERE p.uid IN (:uids)",
    )
    suspend fun productsByUid(uids: List<String>): List<ProductWire>

    @Query("SELECT uid, name, phone, note FROM customer WHERE uid IN (:uids)")
    suspend fun customersByUid(uids: List<String>): List<CustomerWire>

    @Query(
        "SELECT uid, date, status, openedAt, openingCash, openingMobile, closedAt, expectedCash, expectedMobile, countedCash, " +
            "countedMobile, diffCash, diffMobile, diffReason, closeNote, reopenedCount FROM business_day WHERE uid IN (:uids)",
    )
    suspend fun daysByUid(uids: List<String>): List<DayWire>

    @Query(
        "SELECT s.uid AS uid, d.uid AS dayUid, s.createdAt AS createdAt, s.total AS total, s.costTotal AS costTotal, s.method AS method, " +
            "c.uid AS customerUid, s.cashReceived AS cashReceived, s.status AS status, s.voidReason AS voidReason, s.voidedAt AS voidedAt " +
            "FROM sale s LEFT JOIN business_day d ON d.id = s.dayId LEFT JOIN customer c ON c.id = s.customerId WHERE s.uid IN (:uids)",
    )
    suspend fun salesByUid(uids: List<String>): List<SaleWire>

    @Query(
        "SELECT i.uid AS uid, s.uid AS saleUid, p.uid AS productUid, i.productName AS productName, i.qty AS qty, " +
            "i.unitPrice AS unitPrice, i.unitCost AS unitCost " +
            "FROM sale_item i LEFT JOIN sale s ON s.id = i.saleId LEFT JOIN product p ON p.id = i.productId WHERE i.uid IN (:uids)",
    )
    suspend fun saleItemsByUid(uids: List<String>): List<SaleItemWire>

    @Query(
        "SELECT m.uid AS uid, d.uid AS dayUid, m.createdAt AS createdAt, m.type AS type, m.method AS method, m.amount AS amount, " +
            "m.category AS category, c.uid AS customerUid, m.note AS note " +
            "FROM money_entry m LEFT JOIN business_day d ON d.id = m.dayId LEFT JOIN customer c ON c.id = m.customerId WHERE m.uid IN (:uids)",
    )
    suspend fun moneyEntriesByUid(uids: List<String>): List<MoneyEntryWire>

    @Query(
        "SELECT p.uid AS uid, d.uid AS dayUid, p.createdAt AS createdAt, p.total AS total, p.paid AS paid, p.note AS note " +
            "FROM purchase p LEFT JOIN business_day d ON d.id = p.dayId WHERE p.uid IN (:uids)",
    )
    suspend fun purchasesByUid(uids: List<String>): List<PurchaseWire>
}

// Wire rows as read from the database: the entity columns, with each foreign key replaced by the parent's uid.
// A required parent uid that is null means the parent is gone (an orphan); SyncRepo skips such rows.
data class CategoryWire(val uid: String, val name: String, val sortOrder: Int)

data class ProductWire(
    val uid: String, val name: String, val categoryUid: String?, val unit: ProductUnit, val sellPrice: Long, val costPrice: Long,
    val stockQty: Double, val lowStockLevel: Double, val barcode: String?, val active: Boolean, val createdAt: Long, val updatedAt: Long,
)

data class CustomerWire(val uid: String, val name: String, val phone: String?, val note: String?)

data class DayWire(
    val uid: String, val date: String, val status: DayStatus, val openedAt: Long, val openingCash: Long, val openingMobile: Long,
    val closedAt: Long?, val expectedCash: Long?, val expectedMobile: Long?, val countedCash: Long?, val countedMobile: Long?,
    val diffCash: Long?, val diffMobile: Long?, val diffReason: String?, val closeNote: String?, val reopenedCount: Int,
)

data class SaleWire(
    val uid: String, val dayUid: String?, val createdAt: Long, val total: Long, val costTotal: Long, val method: PayMethod,
    val customerUid: String?, val cashReceived: Long?, val status: SaleStatus, val voidReason: String?, val voidedAt: Long?,
)

data class SaleItemWire(
    val uid: String, val saleUid: String?, val productUid: String?, val productName: String, val qty: Double,
    val unitPrice: Long, val unitCost: Long,
)

data class MoneyEntryWire(
    val uid: String, val dayUid: String?, val createdAt: Long, val type: MoneyEntryType, val method: MoneyMethod, val amount: Long,
    val category: String?, val customerUid: String?, val note: String?,
)

data class PurchaseWire(val uid: String, val dayUid: String?, val createdAt: Long, val total: Long, val paid: PurchasePaid, val note: String?)
