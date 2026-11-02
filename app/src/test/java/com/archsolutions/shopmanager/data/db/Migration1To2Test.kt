package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Two rows in each of the 8 synced tables plus a supplier, written the way a v1 database holds them. */
internal fun insertV1Rows(db: SupportSQLiteDatabase) {
    repeat(2) { i ->
        val n = i + 1
        db.execSQL("INSERT INTO category (name, sortOrder) VALUES ('c$n', $n)")
        db.execSQL("INSERT INTO product (name, categoryId, unit, sellPrice, costPrice, stockQty, lowStockLevel, barcode, active, createdAt, updatedAt) VALUES ('p$n', 1, 'PCS', 1000, 800, 5.0, 1.0, NULL, 1, 0, 0)")
        db.execSQL("INSERT INTO customer (name, phone, note) VALUES ('cu$n', NULL, NULL)")
        db.execSQL("INSERT INTO business_day (date, status, openedAt, openingCash, openingMobile, reopenedCount) VALUES ('2026-09-0$n', 'OPEN', 0, 0, 0, 0)")
        db.execSQL("INSERT INTO sale (dayId, createdAt, total, costTotal, method, status) VALUES (1, 0, 1000, 800, 'CASH', 'DONE')")
        db.execSQL("INSERT INTO sale_item (saleId, productId, productName, qty, unitPrice, unitCost) VALUES (1, 1, 'p1', 1.0, 1000, 800)")
        db.execSQL("INSERT INTO money_entry (dayId, createdAt, type, method, amount) VALUES (1, 0, 'EXPENSE', 'CASH', 500)")
        db.execSQL("INSERT INTO purchase (dayId, supplierId, createdAt, total, paid) VALUES (1, NULL, 0, 800, 'PAID')")
    }
    db.execSQL("INSERT INTO supplier (name, active) VALUES ('s1', 1)")
}

@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val uuidV4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> =
        query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }

    @After
    fun tearDown() {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun `migrating gives every synced row a v4 uid and leaves the sync tables ready`() {
        helper.createDatabase("migrate-test", 1).use { insertV1Rows(it) }

        val db = helper.runMigrationsAndValidate("migrate-test", 2, true, MIGRATION_1_2)

        val uids = SyncTriggers.TABLES.flatMap { db.strings("SELECT uid FROM $it") }
        assertEquals(16, uids.size)
        assertTrue(uids.all { uuidV4.matches(it) })
        assertEquals(16, uids.toSet().size)
        db.query("SELECT id, enabled, fillId, fillStartSeq FROM sync_state").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
            assertEquals(1, c.count)
        }
        assertEquals(0L, db.long("SELECT COUNT(*) FROM sync_outbox"))
        assertEquals(24L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync_%'"))
        assertEquals(listOf("s1"), db.strings("SELECT name FROM supplier"))
    }

    @Test
    fun `opens a v1 backup database`() {
        helper.createDatabase(AppDatabase.DATABASE_NAME, 1).use { insertV1Rows(it) }

        val db = DatabaseModule.provideAppDatabase(context)
        try {
            val sqlite = db.openHelper.writableDatabase
            val uids = SyncTriggers.TABLES.flatMap { sqlite.strings("SELECT uid FROM $it") }
            assertEquals(16, uids.size)
            assertTrue(uids.all { uuidV4.matches(it) })
            assertEquals(1L, sqlite.long("SELECT COUNT(*) FROM sync_state WHERE enabled = 0"))
        } finally {
            db.close()
        }
    }
}
