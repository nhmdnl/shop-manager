package com.archsolutions.shopmanager.data.db

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import com.archsolutions.shopmanager.data.backup.BackupManager
import com.archsolutions.shopmanager.data.cloud.CloudStore
import com.archsolutions.shopmanager.data.cloud.FakeTokenCipher
import com.archsolutions.shopmanager.data.cloud.cloudDataStoreFile
import com.archsolutions.shopmanager.data.cloud.sync.FillMarker
import com.archsolutions.shopmanager.data.backup.PendingRestore
import com.archsolutions.shopmanager.data.backup.RestoreResult
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.testutil.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRestoreTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var db: AppDatabase = openDb()

    private fun openDb(): AppDatabase =
        AppDatabase.configure(Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, AppDatabase.DATABASE_NAME))
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

    private fun dbFrom(zip: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: error("no database entry")
                if (e.name == "shop_manager.db") return z.readBytes()
            }
        }
    }

    private fun integrity(bytes: ByteArray): String {
        val file = tmpFolder.newFile()
        file.writeBytes(bytes)
        val copy = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        return copy.use { c -> c.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); it.getString(0) } }
    }

    @Test
    fun `a backup taken while another thread keeps writing is a whole database`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("w.preferences_pb") }))
        for (i in 1..300) db.productDao().insert(product("Seed $i"))
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val writes = java.util.concurrent.atomic.AtomicInteger(0)
        val writer = Thread {
            var n = 0
            try { while (running.get()) {
                db.runInTransaction {
                    db.openHelper.writableDatabase.execSQL("INSERT INTO product (name, unit, sellPrice, costPrice, stockQty, lowStockLevel, active, createdAt, updatedAt, uid) VALUES ('W${n++}', 'PCS', 1, 1, 0, 0, 1, 0, 0, hex(randomblob(16)))")
                    db.openHelper.writableDatabase.execSQL("DELETE FROM product WHERE name LIKE 'W%' AND id % 3 = 0")
                }
                writes.incrementAndGet()
            } } catch (t: Throwable) { failure.set(t) }
        }
        writer.start()
        try {
            repeat(15) {
                val zip = ByteArrayOutputStream().also { manager(store).writeZip(it) }.toByteArray()
                assertEquals("ok", integrity(dbFrom(zip)))
            }
        } finally {
            running.set(false)
            writer.join()
        }
        assertEquals(null, failure.get())
        assertTrue("the writer must have been writing (${writes.get()})", writes.get() > 50)
    }

    @Test
    fun `a staged database with a good header but a garbled body is refused`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("g.preferences_pb") }))
        for (i in 1..400) db.productDao().insert(product("Product number $i"))
        val good = ByteArrayOutputStream().also { manager(store).writeZip(it) }.toByteArray()
        val bytes = dbFrom(good)
        assertTrue(bytes.size > 3 * 4096)
        val random = java.util.Random(7)
        for (i in 4096 until bytes.size) bytes[i] = random.nextInt().toByte() // page 1 (header, schema) stays
        val garbled = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.properties"))
                zip.write("app=com.archsolutions.shopmanager\nschemaVersion=${AppDatabase.VERSION}\n".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("shop_manager.db"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }.toByteArray()

        assertEquals(RestoreResult.NOT_A_BACKUP, manager(store).stageRestore(ByteArrayInputStream(garbled)))
        assertFalse(File(context.filesDir, "pending_restore/ready").exists())
    }

    @Test
    fun `a restore sets the fill marker, and nothing staged leaves it unset`() = runTest {
        val marker = FillMarker(context.filesDir)
        marker.clear()
        PendingRestore.applyIfPending(context)
        assertFalse(marker.isSet())

        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("m.preferences_pb") }))
        val zip = ByteArrayOutputStream().also { manager(store).writeZip(it) }.toByteArray()
        assertEquals(RestoreResult.OK, manager(store).stageRestore(ByteArrayInputStream(zip)))
        db.close()
        PendingRestore.applyIfPending(context)
        db = openDb()
        assertTrue(marker.isSet())
        marker.clear()
    }

    @Test
    fun `the marker is written before the copy, so a failed copy still leaves it and the restore`() = runTest {
        val marker = FillMarker(context.filesDir)
        marker.clear()
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("o.preferences_pb") }))
        val zip = ByteArrayOutputStream().also { manager(store).writeZip(it) }.toByteArray()
        assertEquals(RestoreResult.OK, manager(store).stageRestore(ByteArrayInputStream(zip)))
        val staged = File(context.filesDir, "pending_restore")
        assertTrue(File(staged, "ready").exists())
        assertTrue(File(staged, BackupManager.DATABASE).delete()) // the copy will fail
        db.close()

        var thrown = false
        try {
            PendingRestore.applyIfPending(context)
        } catch (e: java.io.IOException) {
            thrown = true
        }
        db = openDb()
        assertTrue(thrown)
        assertTrue(marker.isSet())
        assertTrue(File(staged, "ready").exists())
        marker.clear()
        staged.deleteRecursively()
    }

    @Test
    fun `a v1 backup stages, applies and opens at v2 with uids set`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("v1.preferences_pb") }))
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
        helper.createDatabase("v1-source", 1).use { insertV1Rows(it) }
        val v1File = context.getDatabasePath("v1-source")
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.properties"))
            zip.write("app=com.archsolutions.shopmanager\nschemaVersion=1\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("shop_manager.db"))
            zip.write(v1File.readBytes())
            zip.closeEntry()
        }

        assertEquals(RestoreResult.OK, manager(store).stageRestore(ByteArrayInputStream(bytes.toByteArray())))

        db.close()
        PendingRestore.applyIfPending(context)
        db = openDb()
        val uids = SyncTriggers.TABLES.flatMap { t ->
            db.openHelper.writableDatabase.query("SELECT uid FROM $t").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        }
        assertEquals(16, uids.size)
        assertEquals(16, uids.toSet().size)
        assertTrue(uids.all { it.length == 36 })
        context.deleteDatabase("v1-source")
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

    @Test
    fun `backup zip never contains the cloud datastore`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("d.preferences_pb") }))
        // The real files a running app has: the shop settings and the cloud link.
        BackupManager.settingsFile(context).apply { parentFile?.mkdirs() }.writeText("settings")
        val cloud = CloudStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { cloudDataStoreFile(context) }),
            FakeTokenCipher(),
        )
        cloud.saveLink("tok-123", "shop-1", "0700111222")
        assertTrue(cloudDataStoreFile(context).exists())

        val zip = ByteArrayOutputStream().also { manager(store).writeZip(it) }.toByteArray()

        val names = ZipInputStream(ByteArrayInputStream(zip)).use { z -> generateSequence { z.nextEntry }.map { it.name }.toSet() }
        assertEquals(setOf("manifest.properties", "shop_manager.db", "shop_settings.preferences_pb"), names)
    }

    @Test
    fun `restoreFromFile stages a valid zip`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("e.preferences_pb") }))
        db.productDao().insert(product("Sugar 1kg"))
        val file: File = tmpFolder.newFile("backup.zip")
        file.outputStream().use { manager(store).writeZip(it) }
        db.productDao().insert(product("Bread"))

        assertEquals(RestoreResult.OK, manager(store).restoreFromFile(file))

        db.close()
        PendingRestore.applyIfPending(context)
        db = openDb()
        assertEquals(listOf("Sugar 1kg"), db.productDao().getAllActive().first().map { it.name })
    }

    @Test
    fun `automatic copies go to app storage when the shared folder can't be written`() {
        val shared = tmpFolder.newFolder("shared")
        val local = File(tmpFolder.root, "local")
        assertEquals(shared, BackupManager.autoBackupDir(shared, local))
        assertEquals(local, BackupManager.autoBackupDir(null, local))

        shared.setWritable(false)
        try {
            assertEquals(local, BackupManager.autoBackupDir(shared, local))
            assertTrue(local.isDirectory)
        } finally {
            shared.setWritable(true)
        }
    }

    @Test
    fun `a failed auto backup is kept for Settings and the next good one clears it`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("g.preferences_pb") }))

        // Neither folder can take the copy: the shared one is read-only and a plain file sits where the local one goes.
        val shared = context.getExternalFilesDir("backups")!!.apply { setWritable(false) }
        val local = File(context.filesDir, "backups").apply { deleteRecursively(); writeText("not a folder") }
        try {
            assertFalse(manager(store).runAutoBackup())
            assertEquals(FakeClock().nowMillis(), store.settings.first().autoBackupFailedAt)
        } finally {
            shared.setWritable(true)
            local.delete()
        }

        assertTrue(manager(store).runAutoBackup())
        assertNull(store.settings.first().autoBackupFailedAt)
        manager(store).newestAutoBackup()?.delete()
    }

    @Test
    fun `Back up now also clears a failed auto backup`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("h.preferences_pb") }))
        store.setAutoBackupFailedAt(1L)

        manager(store).backUpTo(Uri.fromFile(tmpFolder.newFile("manual.zip")))
        assertNull(store.settings.first().autoBackupFailedAt)
    }

    @Test
    fun `newestAutoBackup finds the newest copy in either folder`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmpFolder.newFile("f.preferences_pb") }))
        val shared = File(context.getExternalFilesDir("backups"), "${BackupManager.PREFIX}20261028_2000.zip").apply { parentFile?.mkdirs(); writeText("x") }
        val local = File(context.filesDir, "backups/${BackupManager.PREFIX}20261029_2000.zip").apply { parentFile?.mkdirs(); writeText("x") }

        assertEquals(local, manager(store).newestAutoBackup())
        local.delete()
        assertEquals(shared, manager(store).newestAutoBackup())
        shared.delete()
    }
}
