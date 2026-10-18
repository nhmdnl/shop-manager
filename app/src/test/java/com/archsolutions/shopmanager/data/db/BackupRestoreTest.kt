package com.archsolutions.shopmanager.data.db

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.backup.BackupManager
import com.archsolutions.shopmanager.data.backup.PendingRestore
import com.archsolutions.shopmanager.data.backup.RestoreResult
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRestoreTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var db: AppDatabase = openDb()

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .allowMainThreadQueries()
            .build()

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
    }

    private fun product(name: String) =
        ProductEntity(name = name, categoryId = null, sellPrice = 5_000, costPrice = 4_200, createdAt = 0L, updatedAt = 0L)

    private fun manager(store: SettingsStore) = BackupManager(context, db, store, FakeClock())

    @Test
    fun `a backup restores the shop as it was, on the next start`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("b.preferences_pb") }))
        db.productDao().insert(product("Sugar 1kg"))
        val zip = ByteArrayOutputStream().also { manager(store).writeZip(it) }.toByteArray()

        db.productDao().insert(product("Bread"))
        assertEquals(RestoreResult.OK, manager(store).stageRestore(ByteArrayInputStream(zip)))

        // What the app does on its next start: swap the staged files in before opening the database.
        db.close()
        PendingRestore.applyIfPending(context)
        db = openDb()
        assertEquals(listOf("Sugar 1kg"), db.productDao().getAllActive().first().map { it.name })
    }

    @Test
    fun `restore refuses other zips and backups from a newer schema`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("c.preferences_pb") }))
        fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
            return ByteArrayInputStream(bytes.toByteArray())
        }
        val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(84)

        assertEquals(RestoreResult.NOT_A_BACKUP, manager(store).stageRestore(zipOf("photo.jpg" to ByteArray(10))))
        assertEquals(RestoreResult.NOT_A_BACKUP, manager(store).stageRestore(ByteArrayInputStream("not a zip".toByteArray())))
        val newer = "app=com.archsolutions.shopmanager\nschemaVersion=99\n".toByteArray()
        assertEquals(
            RestoreResult.TOO_NEW,
            manager(store).stageRestore(zipOf("manifest.properties" to newer, "shop_manager.db" to sqliteHeader)),
        )
    }
}
