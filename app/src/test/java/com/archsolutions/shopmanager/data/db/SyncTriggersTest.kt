package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.archsolutions.cloud.contract.shop.SyncTables
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncTriggersTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val open = mutableListOf<AppDatabase>()

    @After
    fun tearDown() {
        open.forEach { it.close() }
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    private fun fresh(): SupportSQLiteDatabase =
        AppDatabase.createInMemory(context).also { open += it }.openHelper.writableDatabase

    /** A file database that went through MIGRATION_1_2 (created at v1, opened by the app's own builder). */
    private fun migrated(): SupportSQLiteDatabase {
        helper.createDatabase(AppDatabase.DATABASE_NAME, 1).close()
        return DatabaseModule.provideAppDatabase(context).also { open += it }.openHelper.writableDatabase
    }

    private data class Out(val tbl: String, val uid: String, val op: String)

    private fun SupportSQLiteDatabase.outbox(): List<Out> =
        query("SELECT tbl, uid, op FROM sync_outbox ORDER BY seq").use { c ->
            buildList { while (c.moveToNext()) add(Out(c.getString(0), c.getString(1), c.getString(2))) }
        }

    private fun SupportSQLiteDatabase.count(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun SupportSQLiteDatabase.enable() = execSQL("UPDATE sync_state SET enabled = 1 WHERE id = 1")

    private fun SupportSQLiteDatabase.insertSale(uid: String) = execSQL(
        "INSERT INTO sale (dayId, createdAt, total, costTotal, method, status, uid) VALUES (1, 0, 1000, 800, 'CASH', 'DONE', '$uid')",
    )

    private val insertsByTable = mapOf(
        "category" to "INSERT INTO category (name, sortOrder, uid) VALUES ('c', 1, 'u-category')",
        "product" to "INSERT INTO product (name, categoryId, unit, sellPrice, costPrice, stockQty, lowStockLevel, barcode, active, createdAt, updatedAt, uid) VALUES ('p', NULL, 'PCS', 1, 1, 0.0, 0.0, NULL, 1, 0, 0, 'u-product')",
        "customer" to "INSERT INTO customer (name, phone, note, uid) VALUES ('c', NULL, NULL, 'u-customer')",
        "business_day" to "INSERT INTO business_day (date, status, openedAt, openingCash, openingMobile, reopenedCount, uid) VALUES ('2026-09-01', 'OPEN', 0, 0, 0, 0, 'u-business_day')",
        "sale_item" to "INSERT INTO sale_item (saleId, productId, productName, qty, unitPrice, unitCost, uid) VALUES (1, 1, 'p', 1.0, 1, 1, 'u-sale_item')",
        "money_entry" to "INSERT INTO money_entry (dayId, createdAt, type, method, amount, uid) VALUES (1, 0, 'EXPENSE', 'CASH', 5, 'u-money_entry')",
        "purchase" to "INSERT INTO purchase (dayId, supplierId, createdAt, total, paid, uid) VALUES (1, NULL, 0, 5, 'PAID', 'u-purchase')",
    )

    @Test
    fun `triggers cover exactly the contract tables`() {
        assertEquals(SyncTables.ALL, SyncTriggers.TABLES)
    }

    @Test
    fun `a fresh database has 24 triggers and a disabled sync_state row`() {
        val db = fresh()
        assertEquals(24L, db.count("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync_%'"))
        assertEquals(1L, db.count("SELECT COUNT(*) FROM sync_state WHERE id = 1 AND enabled = 0"))
    }

    @Test
    fun `nothing is written while sync is disabled`() {
        val db = fresh()
        db.insertSale("s1")
        db.execSQL("UPDATE sale SET total = 5 WHERE uid = 's1'")
        db.execSQL("DELETE FROM sale WHERE uid = 's1'")
        assertEquals(emptyList<Out>(), db.outbox())
    }

    private fun insertUpdateDelete(db: SupportSQLiteDatabase) {
        db.enable()
        db.insertSale("s1")
        assertEquals(listOf(Out("sale", "s1", "UPSERT")), db.outbox())

        db.execSQL("DELETE FROM sync_outbox")
        db.execSQL("UPDATE sale SET total = 7 WHERE uid = 's1'")
        assertEquals(listOf(Out("sale", "s1", "UPSERT")), db.outbox())

        db.execSQL("DELETE FROM sync_outbox")
        db.execSQL("UPDATE sale SET uid = 's2' WHERE uid = 's1'")
        assertEquals(listOf(Out("sale", "s1", "DELETE"), Out("sale", "s2", "UPSERT")), db.outbox())

        db.execSQL("DELETE FROM sync_outbox")
        db.execSQL("DELETE FROM sale WHERE uid = 's2'")
        assertEquals(listOf(Out("sale", "s2", "DELETE")), db.outbox())
    }

    @Test
    fun `insert update uid change and delete on a fresh database`() = insertUpdateDelete(fresh())

    @Test
    fun `insert update uid change and delete on a migrated database`() = insertUpdateDelete(migrated())

    private fun otherTables(db: SupportSQLiteDatabase) {
        db.enable()
        for ((table, sql) in insertsByTable) {
            db.execSQL("DELETE FROM sync_outbox")
            db.execSQL(sql)
            assertEquals("table $table", listOf(Out(table, "u-$table", "UPSERT")), db.outbox())
        }
    }

    @Test
    fun `one insert into each other synced table writes one UPSERT`() = otherTables(fresh())

    @Test
    fun `one insert into each other synced table writes one UPSERT on a migrated database`() = otherTables(migrated())

    @Test
    fun `unsynced tables write nothing`() {
        val db = fresh()
        db.enable()
        db.execSQL("INSERT INTO supplier (name, active) VALUES ('s', 1)")
        db.execSQL("INSERT INTO stock_movement (productId, type, qtyChange, createdAt) VALUES (1, 'SALE', -1.0, 0)")
        assertEquals(emptyList<Out>(), db.outbox())
    }

    @Test
    fun `a REPLACE over an existing row writes DELETE then UPSERT for the same uid`() {
        val appDb = AppDatabase.createInMemory(context).also { open += it }
        // A Room query first: its invalidation tracker is what turns recursive_triggers on.
        appDb.categoryDao()
        appDb.query("SELECT 1", null).use { it.moveToFirst() }
        val db = appDb.openHelper.writableDatabase
        db.enable()
        db.execSQL("INSERT INTO category (id, name, sortOrder, uid) VALUES (1, 'a', 1, 'c1')")
        db.execSQL("DELETE FROM sync_outbox")

        db.execSQL("INSERT OR REPLACE INTO category (id, name, sortOrder, uid) VALUES (1, 'b', 2, 'c1')")

        assertEquals(listOf(Out("category", "c1", "DELETE"), Out("category", "c1", "UPSERT")), db.outbox())
    }
}
