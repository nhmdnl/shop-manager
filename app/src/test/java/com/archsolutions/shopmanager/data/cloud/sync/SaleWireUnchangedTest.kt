package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SaleWireUnchangedTest {

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

    @Test
    fun `a sale carrying staffId uploads the same wire JSON as one without`() = runTest {
        sql.execSQL("INSERT INTO business_day (id, date, status, openedAt, openingCash, openingMobile, reopenedCount, uid) VALUES (1, '2026-09-01', 'OPEN', 0, 10000, 0, 0, 'day-1')")
        sql.execSQL("INSERT INTO sale (id, dayId, createdAt, total, costTotal, method, status, uid) VALUES (1, 1, 5, 1000, 800, 'CASH', 'DONE', 'sale-plain')")
        sql.execSQL(
            "INSERT INTO sale (id, dayId, createdAt, total, costTotal, method, status, staffId, voidedByStaffId, uid) VALUES " +
                "(2, 1, 5, 1000, 800, 'CASH', 'DONE', 7, 8, 'sale-staff')",
        )
        val repo = repo()
        repo.startFill()
        val batch = repo.nextBatch()!!

        val plain = batch.rows.single { it.table == "sale" && it.uid == "sale-plain" }.data!!.jsonObject
        val staff = batch.rows.single { it.table == "sale" && it.uid == "sale-staff" }.data!!.jsonObject
        assertEquals(plain.keys, staff.keys)
        assertTrue(staff.keys.none { it.startsWith("staff") || it.contains("StaffId") })
        assertEquals(plain, staff)
    }
}
