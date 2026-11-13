package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { it.moveToFirst(); it.getLong(0) }

    @After
    fun tearDown() {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun `migrating v2 to v3 keeps every row, adds an empty staff table and null staff columns`() {
        helper.createDatabase("migrate-test", 2).use { db ->
            createV2Triggers(db) // the helper skips Room's callback, so install what a real v2 install has
            db.execSQL("INSERT INTO sale (dayId, createdAt, total, costTotal, method, status, uid) VALUES (1, 0, 1000, 800, 'CASH', 'DONE', 'sale-uid-1')")
        }

        val db = helper.runMigrationsAndValidate("migrate-test", 3, true, MIGRATION_2_3)

        assertEquals(1L, db.long("SELECT COUNT(*) FROM sale WHERE uid = 'sale-uid-1' AND total = 1000"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM staff"))
        assertEquals(1L, db.long("SELECT COUNT(*) FROM sale WHERE staffId IS NULL AND voidedByStaffId IS NULL"))
        assertEquals(24L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'sync_%'"))
    }
}
