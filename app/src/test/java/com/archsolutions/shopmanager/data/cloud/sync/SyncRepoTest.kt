package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.cloud.contract.ArchJson
import com.archsolutions.cloud.contract.shop.BusinessDayRow
import com.archsolutions.cloud.contract.shop.CategoryRow
import com.archsolutions.cloud.contract.shop.CustomerRow
import com.archsolutions.cloud.contract.shop.MoneyEntryRow
import com.archsolutions.cloud.contract.shop.ProductRow
import com.archsolutions.cloud.contract.shop.PurchaseRow
import com.archsolutions.cloud.contract.shop.SaleItemRow
import com.archsolutions.cloud.contract.shop.SaleRow
import com.archsolutions.cloud.contract.shop.SaleStatus
import com.archsolutions.cloud.contract.shop.SyncBatch
import com.archsolutions.cloud.contract.shop.SyncReply
import com.archsolutions.cloud.contract.shop.SyncRow
import com.archsolutions.cloud.contract.shop.SyncTables
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SyncRepoTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val db: AppDatabase = AppDatabase.createInMemory(context)
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    private val clock = object : AppClock {
        override fun nowMillis() = 1_000L
        override fun todayDateString() = "2026-09-01"
        override val zoneId: ZoneId = ZoneId.of("Africa/Kampala")
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.repo() = SyncRepo(
        db,
        SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("sync.preferences_pb") })),
        clock,
    )

    private fun count(q: String): Long = sql.query(q).use { it.moveToFirst(); it.getLong(0) }

    private fun outbox(): List<Triple<Long, String, String>> =
        sql.query("SELECT seq, tbl, op FROM sync_outbox ORDER BY seq").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) }
        }

    /** One day, a category with two products, a customer, 4 sales (one on credit), items, money entries incl. a repayment, a purchase. */
    private fun fixture() {
        sql.execSQL("INSERT INTO category (id, name, sortOrder, uid) VALUES (1, 'Food', 1, 'cat-1')")
        sql.execSQL("INSERT INTO product (id, name, categoryId, unit, sellPrice, costPrice, stockQty, lowStockLevel, barcode, active, createdAt, updatedAt, uid) VALUES (1, 'Rice', 1, 'KG', 5000, 4000, 12.5, 2.0, NULL, 1, 1, 1, 'prod-1')")
        sql.execSQL("INSERT INTO product (id, name, categoryId, unit, sellPrice, costPrice, stockQty, lowStockLevel, barcode, active, createdAt, updatedAt, uid) VALUES (2, 'Soap', NULL, 'PCS', 2000, 1500, 10.0, 1.0, '123', 1, 1, 1, 'prod-2')")
        sql.execSQL("INSERT INTO customer (id, name, phone, note, uid) VALUES (1, 'Mary', '0700', NULL, 'cust-1')")
        sql.execSQL("INSERT INTO business_day (id, date, status, openedAt, openingCash, openingMobile, reopenedCount, uid) VALUES (1, '2026-09-01', 'OPEN', 0, 10000, 0, 0, 'day-1')")
        for (i in 1..4) {
            val credit = i == 4
            sql.execSQL(
                "INSERT INTO sale (id, dayId, createdAt, total, costTotal, method, customerId, status, uid) VALUES " +
                    "($i, 1, $i, ${i * 1000}, ${i * 800}, '${if (credit) "CREDIT" else "CASH"}', ${if (credit) "1" else "NULL"}, 'DONE', 'sale-$i')",
            )
            sql.execSQL("INSERT INTO sale_item (saleId, productId, productName, qty, unitPrice, unitCost, uid) VALUES ($i, 1, 'Rice', 1.5, 1000, 800, 'item-$i')")
        }
        sql.execSQL("INSERT INTO money_entry (id, dayId, createdAt, type, method, amount, uid) VALUES (1, 1, 1, 'EXPENSE', 'CASH', 500, 'money-1')")
        sql.execSQL("INSERT INTO money_entry (id, dayId, createdAt, type, method, amount, customerId, uid) VALUES (2, 1, 2, 'CREDIT_REPAYMENT', 'MOBILE_MONEY', 700, 1, 'money-2')")
        sql.execSQL("INSERT INTO purchase (id, dayId, supplierId, createdAt, total, paid, uid) VALUES (1, 1, NULL, 1, 9000, 'CASH', 'purch-1')")
    }

    private val syncedRows = 2 + 1 + 1 + 1 + 4 + 4 + 2 + 1 // category..purchase except suppliers

    private fun JsonObject.sale() = ArchJson.decodeFromJsonElement(SaleRow.serializer(), this)

    private fun SyncBatch.row(table: String, uid: String): SyncRow = rows.single { it.table == table && it.uid == uid }

    private fun bulkMoney(n: Int) {
        sql.beginTransaction()
        try {
            for (i in 1..n) sql.execSQL("INSERT INTO money_entry (dayId, createdAt, type, method, amount, uid) VALUES (1, $i, 'EXPENSE', 'CASH', 1, 'bulk-$i')")
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
    }

    @Test
    fun `startFill queues every synced row then FILL_END`() = runTest {
        fixture()
        val repo = repo()
        val fillId = repo.startFill()

        val out = outbox()
        assertEquals(syncedRows + 1, out.size)
        assertEquals("FILL_END", out.last().third)
        assertEquals(fillId, sql.query("SELECT uid FROM sync_outbox WHERE op = 'FILL_END'").use { it.moveToFirst(); it.getString(0) })
        assertEquals(fillId, db.syncDao().state().fillId)
        assertTrue(repo.isEnabled())
        assertTrue(repo.fillInFlight())
        // Tables come out in SyncTables.ALL order (category, product, ..., business_day before sale).
        val tables = out.dropLast(1).map { it.second }.distinct()
        assertEquals(SyncTables.ALL, tables)
        assertTrue(tables.indexOf("category") < tables.indexOf("product"))
        assertTrue(tables.indexOf("business_day") < tables.indexOf("sale"))
    }

    @Test
    fun `startFill twice replaces the first fill`() = runTest {
        fixture()
        val repo = repo()
        val first = repo.startFill()
        val second = repo.startFill()

        assertNotEquals(first, second)
        assertEquals(1L, count("SELECT COUNT(*) FROM sync_outbox WHERE op = 'FILL_END'"))
        assertEquals(syncedRows + 1L, count("SELECT COUNT(*) FROM sync_outbox"))
        assertEquals(second, db.syncDao().state().fillId)
    }

    @Test
    fun `batch writes foreign keys as uids`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        val batch = repo.nextBatch()!!

        val credit = batch.row("sale", "sale-4").data!!.jsonObject.sale()
        assertEquals("day-1", credit.dayUid)
        assertEquals("cust-1", credit.customerUid)
        val item = ArchJson.decodeFromJsonElement(SaleItemRow.serializer(), batch.row("sale_item", "item-2").data!!)
        assertEquals("sale-2", item.saleUid)
        assertEquals("prod-1", item.productUid)
        assertEquals("cat-1", ArchJson.decodeFromJsonElement(ProductRow.serializer(), batch.row("product", "prod-1").data!!).categoryUid)
        assertNull(ArchJson.decodeFromJsonElement(ProductRow.serializer(), batch.row("product", "prod-2").data!!).categoryUid)
        val repay = ArchJson.decodeFromJsonElement(MoneyEntryRow.serializer(), batch.row("money_entry", "money-2").data!!)
        assertEquals("cust-1", repay.customerUid)
        assertEquals("day-1", repay.dayUid)
    }

    @Test
    fun `batch decodes as the contract's rows`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        val batch = repo.nextBatch()!!

        assertEquals(syncedRows, batch.rows.size)
        for (r in batch.rows) {
            val d = r.data!!
            when (r.table) {
                "category" -> assertEquals("Food", ArchJson.decodeFromJsonElement(CategoryRow.serializer(), d).name)
                "product" -> ArchJson.decodeFromJsonElement(ProductRow.serializer(), d)
                "customer" -> assertEquals("Mary", ArchJson.decodeFromJsonElement(CustomerRow.serializer(), d).name)
                "business_day" -> assertEquals("2026-09-01", ArchJson.decodeFromJsonElement(BusinessDayRow.serializer(), d).date)
                "sale" -> r.data!!.jsonObject.sale()
                "sale_item" -> assertEquals(1.5, ArchJson.decodeFromJsonElement(SaleItemRow.serializer(), d).qty, 0.0)
                "money_entry" -> ArchJson.decodeFromJsonElement(MoneyEntryRow.serializer(), d)
                "purchase" -> assertEquals(9000L, ArchJson.decodeFromJsonElement(PurchaseRow.serializer(), d).total)
                else -> error("unexpected table ${r.table}")
            }
            assertEquals("UPSERT", r.op)
        }
        assertEquals(2, batch.schemaVersion - 0)
        assertEquals(500L, batch.shopSettings.allowedDifference)
    }

    @Test
    fun `repeats collapse to the latest data`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        sql.execSQL("DELETE FROM sync_outbox")
        for (t in listOf(1100L, 1200L, 1300L)) sql.execSQL("UPDATE sale SET total = $t WHERE uid = 'sale-1'")

        val batch = repo.nextBatch()!!
        assertEquals(1, batch.rows.size)
        assertEquals(1300L, batch.rows.single().data!!.jsonObject.sale().total)
        assertTrue(batch.toSeq > batch.fromSeq)
    }

    @Test
    fun `upsert of a gone row is skipped but acknowledged`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        repo.ack(SyncReply(Long.MAX_VALUE, emptyList()))
        sql.execSQL("INSERT INTO money_entry (dayId, createdAt, type, method, amount, uid) VALUES (1, 9, 'EXPENSE', 'CASH', 5, 'money-gone')")
        sql.execSQL("DELETE FROM money_entry WHERE uid = 'money-gone'")

        val batch = repo.nextBatch()!!
        assertEquals(listOf("DELETE"), batch.rows.map { it.op })
        assertEquals("money-gone", batch.rows.single().uid)
        assertNull(batch.rows.single().data)
        assertEquals(outbox().last().first, batch.toSeq)
        assertEquals(outbox().first().first, batch.fromSeq)
    }

    @Test
    fun `an orphan is skipped and acknowledged`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        repo.ack(SyncReply(Long.MAX_VALUE, emptyList()))
        sql.execSQL("INSERT INTO sale (dayId, createdAt, total, costTotal, method, status, uid) VALUES (99, 1, 1, 1, 'CASH', 'DONE', 'orphan')")

        val batch = repo.nextBatch()!!
        assertTrue(batch.rows.isEmpty())
        repo.ack(SyncReply(batch.toSeq, emptyList()))
        assertNull(repo.nextBatch())
    }

    @Test
    fun `500 cap and seq order`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        repo.ack(SyncReply(Long.MAX_VALUE, emptyList()))
        bulkMoney(1_200)

        val batches = mutableListOf<SyncBatch>()
        while (true) {
            val b = repo.nextBatch() ?: break
            batches += b
            repo.ack(SyncReply(b.toSeq, emptyList()))
        }
        assertEquals(3, batches.size)
        assertEquals(listOf(500, 500, 200), batches.map { it.rows.size })
        for (i in 1 until batches.size) assertTrue(batches[i].fromSeq > batches[i - 1].toSeq)
        assertEquals(1_200, batches.flatMap { it.rows }.map { it.uid }.toSet().size)
    }

    @Test
    fun `fillEnd is set only on the batch holding FILL_END`() = runTest {
        fixture()
        bulkMoney(1_200)
        val repo = repo()
        repo.startFill()

        val flags = mutableListOf<Boolean>()
        while (true) {
            val b = repo.nextBatch() ?: break
            flags += b.fillEnd
            repo.ack(SyncReply(b.toSeq, emptyList()))
        }
        assertEquals(listOf(false, false, true), flags.takeLast(3))
        assertEquals(1, flags.count { it })
        assertFalse(repo.fillInFlight())
    }

    @Test
    fun `fillStart is set only on the batch holding the fill's first entry`() = runTest {
        fixture()
        bulkMoney(1_200 - syncedRows - 1) // fill = 1,200 entries incl. FILL_END
        val repo = repo()
        repo.startFill()
        assertEquals(1_200L, count("SELECT COUNT(*) FROM sync_outbox"))

        val first = repo.nextBatch()!!
        assertTrue(first.fillStart)
        assertTrue(repo.nextBatch()!!.fillStart) // built again before ack
        repo.ack(SyncReply(first.toSeq, emptyList()))
        val second = repo.nextBatch()!!
        assertFalse(second.fillStart)
        repo.ack(SyncReply(second.toSeq, emptyList()))
        val third = repo.nextBatch()!!
        assertFalse(third.fillStart)
        assertTrue(third.fillEnd)
        repo.ack(SyncReply(third.toSeq, emptyList()))

        sql.execSQL("UPDATE sale SET total = 1 WHERE uid = 'sale-1'")
        assertFalse(repo.nextBatch()!!.fillStart)
    }

    @Test
    fun `an empty shop's fill is one batch with both flags`() = runTest {
        val repo = repo()
        repo.startFill()
        val batch = repo.nextBatch()!!
        assertTrue(batch.fillStart)
        assertTrue(batch.fillEnd)
        assertTrue(batch.rows.isEmpty())
    }

    @Test
    fun `the same slice gives the same batchId`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        assertEquals(repo.nextBatch()!!.batchId, repo.nextBatch()!!.batchId)
        val before = repo.nextBatch()!!.batchId
        repo.startFill()
        assertNotEquals(before, repo.nextBatch()!!.batchId)
    }

    @Test
    fun `null fields are left out`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        val obj = repo.nextBatch()!!.row("sale", "sale-1").data!!.jsonObject
        for (k in listOf("customerUid", "voidReason", "voidedAt")) assertFalse(k, obj.containsKey(k))
        val sale = obj.sale()
        assertEquals(SaleStatus.DONE, sale.status)
        assertNull(sale.customerUid)
        assertNull(sale.voidReason)
        assertNull(sale.voidedAt)
    }

    @Test
    fun `ack deletes up to ackSeq only`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        val batch = repo.nextBatch()!!
        sql.execSQL("UPDATE sale SET total = 5 WHERE uid = 'sale-1'")
        val late = outbox().last().first

        repo.ack(SyncReply(batch.toSeq, emptyList()))
        assertEquals(listOf(late), outbox().map { it.first })
        assertNotNull(repo.nextBatch())
    }

    @Test
    fun `disable empties the outbox and stops the triggers`() = runTest {
        fixture()
        val repo = repo()
        repo.startFill()
        repo.disable()

        assertFalse(repo.isEnabled())
        assertEquals(0L, count("SELECT COUNT(*) FROM sync_outbox"))
        assertNull(db.syncDao().state().fillId)
        sql.execSQL("UPDATE sale SET total = 7 WHERE uid = 'sale-1'")
        assertEquals(0L, count("SELECT COUNT(*) FROM sync_outbox"))
        assertNull(repo.nextBatch())
    }
}
