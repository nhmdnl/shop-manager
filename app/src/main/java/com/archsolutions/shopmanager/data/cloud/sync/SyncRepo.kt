package com.archsolutions.shopmanager.data.cloud.sync

import android.util.Log
import androidx.room.withTransaction
import com.archsolutions.cloud.contract.ArchJson
import com.archsolutions.cloud.contract.shop.BusinessDayRow
import com.archsolutions.cloud.contract.shop.CategoryRow
import com.archsolutions.cloud.contract.shop.CustomerRow
import com.archsolutions.cloud.contract.shop.MAX_BATCH_ROWS
import com.archsolutions.cloud.contract.shop.MoneyEntryRow
import com.archsolutions.cloud.contract.shop.ProductRow
import com.archsolutions.cloud.contract.shop.PurchaseRow
import com.archsolutions.cloud.contract.shop.SYNC_SCHEMA_VERSION
import com.archsolutions.cloud.contract.shop.SaleItemRow
import com.archsolutions.cloud.contract.shop.SaleRow
import com.archsolutions.cloud.contract.shop.StockUnit
import com.archsolutions.cloud.contract.shop.SyncBatch
import com.archsolutions.cloud.contract.shop.SyncReply
import com.archsolutions.cloud.contract.shop.SyncRow
import com.archsolutions.cloud.contract.shop.SyncTables
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.db.entities.SyncOutboxEntity
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import javax.inject.Inject
import com.archsolutions.cloud.contract.shop.DayStatus as WireDayStatus
import com.archsolutions.cloud.contract.shop.MoneyEntryType as WireMoneyEntryType
import com.archsolutions.cloud.contract.shop.MoneyMethod as WireMoneyMethod
import com.archsolutions.cloud.contract.shop.PayMethod as WirePayMethod
import com.archsolutions.cloud.contract.shop.PurchasePaid as WirePurchasePaid
import com.archsolutions.cloud.contract.shop.SaleStatus as WireSaleStatus
import com.archsolutions.cloud.contract.shop.ShopSettings as SyncShopSettings

/** Full fills, outbox batches in the contract's wire shape (foreign keys as uids), and acknowledgements. */
class SyncRepo @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    @Suppress("unused") private val clock: AppClock,
) {
    private val dao get() = db.syncDao()

    suspend fun isEnabled(): Boolean = dao.state().enabled

    /**
     * One transaction on purpose: the sweep is only right if every row is queued before FILL_END.
     * The fill's own inserts touch only sync_outbox and sync_state, which carry no sync triggers.
     */
    suspend fun startFill(): String = db.withTransaction {
        val fillId = UUID.randomUUID().toString()
        dao.setState(true, fillId)
        dao.clearOutbox()
        enqueueAll()
        dao.enqueueFillEnd(fillId)
        dao.markFillStart()
        fillId
    }

    private suspend fun enqueueAll() {
        for (table in SyncTables.ALL) when (table) {
            SyncTables.CATEGORY -> dao.enqueueCategories()
            SyncTables.PRODUCT -> dao.enqueueProducts()
            SyncTables.CUSTOMER -> dao.enqueueCustomers()
            SyncTables.BUSINESS_DAY -> dao.enqueueDays()
            SyncTables.SALE -> dao.enqueueSales()
            SyncTables.SALE_ITEM -> dao.enqueueSaleItems()
            SyncTables.MONEY_ENTRY -> dao.enqueueMoneyEntries()
            SyncTables.PURCHASE -> dao.enqueuePurchases()
            else -> error("Unhandled synced table $table")
        }
    }

    suspend fun disable() = db.withTransaction {
        dao.setState(false, null)
        dao.clearOutbox()
    }

    suspend fun fillInFlight(): Boolean = dao.fillEndPending()

    suspend fun ack(reply: SyncReply) = dao.deleteUpTo(reply.ackSeq)

    /** The next slice of the outbox as a batch, or null when it is empty. Building it again before [ack] gives the same batch. */
    suspend fun nextBatch(maxRows: Int = MAX_BATCH_ROWS): SyncBatch? {
        val allowedDifference = settings.settings.first().allowedDifference
        return db.withTransaction {
            val slice = dao.peek(maxRows)
            if (slice.isEmpty()) return@withTransaction null
            val state = dao.state()
            val fromSeq = slice.first().seq
            val toSeq = slice.last().seq

            // Only the last entry per (table, uid) matters; the rest are acknowledged without a row.
            val last = LinkedHashMap<Pair<String, String>, SyncOutboxEntity>()
            var fillEnd = false
            for (e in slice) {
                if (e.op == SyncOutboxEntity.OP_FILL_END) fillEnd = true else last[e.tbl to e.uid] = e
            }
            val upserts = last.values.filter { it.op == SyncOutboxEntity.OP_UPSERT }.groupBy({ it.tbl }, { it.uid })
            val data = HashMap<Pair<String, String>, JsonElement>()
            for ((table, uids) in upserts) for (chunk in uids.chunked(READ_CHUNK)) readRows(table, chunk, data)

            var skipped = 0
            val rows = ArrayList<SyncRow>()
            for ((key, e) in last) {
                if (e.op == SyncOutboxEntity.OP_DELETE) {
                    rows += SyncRow(e.tbl, SyncOutboxEntity.OP_DELETE, e.uid)
                } else {
                    val json = data[key]
                    if (json == null) skipped++ else rows += SyncRow(e.tbl, SyncOutboxEntity.OP_UPSERT, e.uid, json)
                }
            }
            if (skipped > 0) Log.i(TAG, "Skipped $skipped outbox entries whose row is gone or has no parent")

            val fillId = state.fillId ?: ""
            SyncBatch(
                batchId = UUID.nameUUIDFromBytes("$fillId:$fromSeq:$toSeq".toByteArray()).toString(),
                schemaVersion = SYNC_SCHEMA_VERSION,
                fillId = fillId,
                fillStart = state.fillStartSeq != null && fromSeq <= state.fillStartSeq,
                fillEnd = fillEnd,
                fromSeq = fromSeq,
                toSeq = toSeq,
                shopSettings = SyncShopSettings(allowedDifference),
                rows = rows,
            )
        }
    }

    private suspend fun readRows(table: String, uids: List<String>, out: MutableMap<Pair<String, String>, JsonElement>) {
        fun put(uid: String, row: JsonElement) { out[table to uid] = row }
        when (table) {
            SyncTables.CATEGORY -> dao.categoriesByUid(uids).forEach {
                put(it.uid, ArchJson.encodeToJsonElement(CategoryRow.serializer(), CategoryRow(it.name, it.sortOrder)))
            }
            SyncTables.PRODUCT -> dao.productsByUid(uids).forEach {
                put(
                    it.uid,
                    ArchJson.encodeToJsonElement(
                        ProductRow.serializer(),
                        ProductRow(
                            it.name, it.categoryUid, StockUnit.valueOf(it.unit.name), it.sellPrice, it.costPrice, it.stockQty,
                            it.lowStockLevel, it.barcode, it.active, it.createdAt, it.updatedAt,
                        ),
                    ),
                )
            }
            SyncTables.CUSTOMER -> dao.customersByUid(uids).forEach {
                put(it.uid, ArchJson.encodeToJsonElement(CustomerRow.serializer(), CustomerRow(it.name, it.phone, it.note)))
            }
            SyncTables.BUSINESS_DAY -> dao.daysByUid(uids).forEach {
                put(
                    it.uid,
                    ArchJson.encodeToJsonElement(
                        BusinessDayRow.serializer(),
                        BusinessDayRow(
                            it.date, WireDayStatus.valueOf(it.status.name), it.openedAt, it.openingCash, it.openingMobile, it.closedAt,
                            it.expectedCash, it.expectedMobile, it.countedCash, it.countedMobile, it.diffCash, it.diffMobile,
                            it.diffReason, it.closeNote, it.reopenedCount,
                        ),
                    ),
                )
            }
            SyncTables.SALE -> dao.salesByUid(uids).forEach {
                val dayUid = it.dayUid ?: return@forEach
                put(
                    it.uid,
                    ArchJson.encodeToJsonElement(
                        SaleRow.serializer(),
                        SaleRow(
                            dayUid, it.createdAt, it.total, it.costTotal, WirePayMethod.valueOf(it.method.name), it.customerUid,
                            it.cashReceived, WireSaleStatus.valueOf(it.status.name), it.voidReason, it.voidedAt,
                        ),
                    ),
                )
            }
            SyncTables.SALE_ITEM -> dao.saleItemsByUid(uids).forEach {
                val saleUid = it.saleUid ?: return@forEach
                val productUid = it.productUid ?: return@forEach
                put(
                    it.uid,
                    ArchJson.encodeToJsonElement(
                        SaleItemRow.serializer(),
                        SaleItemRow(saleUid, productUid, it.productName, it.qty, it.unitPrice, it.unitCost),
                    ),
                )
            }
            SyncTables.MONEY_ENTRY -> dao.moneyEntriesByUid(uids).forEach {
                val dayUid = it.dayUid ?: return@forEach
                put(
                    it.uid,
                    ArchJson.encodeToJsonElement(
                        MoneyEntryRow.serializer(),
                        MoneyEntryRow(
                            dayUid, it.createdAt, WireMoneyEntryType.valueOf(it.type.name), WireMoneyMethod.valueOf(it.method.name),
                            it.amount, it.category, it.customerUid, it.note,
                        ),
                    ),
                )
            }
            SyncTables.PURCHASE -> dao.purchasesByUid(uids).forEach {
                val dayUid = it.dayUid ?: return@forEach
                put(
                    it.uid,
                    ArchJson.encodeToJsonElement(
                        PurchaseRow.serializer(),
                        PurchaseRow(dayUid, it.createdAt, it.total, WirePurchasePaid.valueOf(it.paid.name), it.note),
                    ),
                )
            }
            else -> Unit
        }
    }

    private companion object {
        const val TAG = "SyncRepo"
        const val READ_CHUNK = 500
    }
}
