package com.archsolutions.shopmanager.data.backup

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.cloud.sync.FillMarker
import com.archsolutions.shopmanager.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.first

enum class RestoreResult { OK, NOT_A_BACKUP, TOO_NEW }

/**
 * Back up and restore (section 11). A backup is one zip: a manifest, the database (after a full WAL
 * checkpoint) and the settings file. A restore is checked here, staged, and swapped in by
 * [PendingRestore] on the next start, before anything opens the database or the settings.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
    private val clock: AppClock,
) {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun fileName(): String = "$PREFIX${Instant.ofEpochMilli(clock.nowMillis()).atZone(clock.zoneId).format(stamp)}.zip"

    /** "Back up now": to wherever the user picked (Storage Access Framework, so Google Drive works too). */
    suspend fun backUpTo(uri: Uri) = withContext(Dispatchers.IO) {
        // Recorded before the copy, so a restore of this backup knows it was backed up (no reminder
        // straight after restoring); put back if the copy fails. A good copy also answers a failed automatic one.
        val previous = settingsStore.settings.first()
        settingsStore.setLastBackupAt(clock.nowMillis())
        settingsStore.setAutoBackupFailedAt(null)
        try {
            val out = context.contentResolver.openOutputStream(uri) ?: error("Can't write $uri")
            out.use(::writeZip)
        } catch (e: Exception) {
            settingsStore.setLastBackupAt(previous.lastBackupAt)
            settingsStore.setAutoBackupFailedAt(previous.autoBackupFailedAt)
            throw e
        }
    }

    /**
     * The automatic copy after closing a day, in app-specific storage; the newest [KEEP] stay. It stays on
     * this device, so it doesn't count as "last backup" (the Home reminder is about a lost phone).
     */
    suspend fun autoBackup(): File = withContext(Dispatchers.IO) {
        val dir = autoBackupDir(context.getExternalFilesDir(BACKUP_DIR), File(context.filesDir, BACKUP_DIR))
        val file = File(dir, fileName())
        file.outputStream().use(::writeZip)
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        file
    }

    /** [autoBackup] for the worker. A failure is kept for Settings to show, and the next good copy clears it. */
    suspend fun runAutoBackup(): Boolean {
        // Cleared before the copy, so the zip doesn't carry an old failure into a later restore.
        settingsStore.setAutoBackupFailedAt(null)
        return try {
            autoBackup()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            settingsStore.setAutoBackupFailedAt(clock.nowMillis())
            false
        }
    }

    suspend fun restoreFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext RestoreResult.NOT_A_BACKUP
        input.use { stageRestore(it) }
    }

    /** The newest automatic copy kept on this device (in either folder [autoBackupDir] picks), or null when there is none. */
    fun newestAutoBackup(): File? =
        listOfNotNull(context.getExternalFilesDir(BACKUP_DIR), File(context.filesDir, BACKUP_DIR))
            .flatMap { dir -> dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".zip") }.orEmpty().asList() }
            .maxByOrNull { it.name }

    /** Stages a restore from a backup file already on this device (the cloud download, an automatic copy). */
    suspend fun restoreFromFile(file: File): RestoreResult = withContext(Dispatchers.IO) {
        try {
            file.inputStream().use { stageRestore(it) }
        } catch (e: IOException) {
            RestoreResult.NOT_A_BACKUP
        }
    }

    internal fun writeZip(out: OutputStream) {
        // Through Room, so it waits its turn with Room's own connections.
        db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
        // Sync writes on its own (fill, acks, disable): the file is copied while holding Room's single writer
        // connection, so no write or auto-checkpoint changes pages under the copy. Only the copy is under the lock.
        val snapshot = File.createTempFile("backup_db", ".tmp", context.cacheDir)
        try {
            db.runInTransaction {
                context.getDatabasePath(AppDatabase.DATABASE_NAME).inputStream().use { input ->
                    snapshot.outputStream().use { input.copyTo(it) }
                }
            }
            zipWith(out, snapshot)
        } finally {
            snapshot.delete()
        }
    }

    private fun zipWith(out: OutputStream, database: File) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            Properties().apply {
                setProperty(KEY_APP, APP_ID)
                setProperty(KEY_SCHEMA, AppDatabase.VERSION.toString())
                setProperty(KEY_CREATED, clock.nowMillis().toString())
            }.store(zip, null)
            zip.closeEntry()
            zip.putFile(DATABASE, database)
            settingsFile(context).takeIf { it.exists() }?.let { zip.putFile(SETTINGS, it) }
        }
    }

    /** Only the three known entries are read, each into a fixed file, so a crafted zip can't write elsewhere. */
    internal fun stageRestore(input: InputStream): RestoreResult {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    if (entry.name in setOf(MANIFEST, DATABASE, SETTINGS)) {
                        File(staging, entry.name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val manifest = File(staging, MANIFEST).takeIf { it.exists() }
                ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
                ?: return RestoreResult.NOT_A_BACKUP
            val schema = manifest.getProperty(KEY_SCHEMA)?.toIntOrNull()
            val database = File(staging, DATABASE)
            if (manifest.getProperty(KEY_APP) != APP_ID || schema == null || !database.isSqlite()) return RestoreResult.NOT_A_BACKUP
            if (schema > AppDatabase.VERSION) return RestoreResult.TOO_NEW
            if (!database.passesQuickCheck()) return RestoreResult.NOT_A_BACKUP
            PendingRestore.stage(context, database, File(staging, SETTINGS).takeIf { it.exists() })
            return RestoreResult.OK
        } catch (e: IOException) {
            return RestoreResult.NOT_A_BACKUP
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    /** Read-only, with a handler that does nothing, so SQLite never deletes the file it finds damaged; closed before returning. */
    private fun File.passesQuickCheck(): Boolean = try {
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY, DatabaseErrorHandler { }).use { staged ->
            staged.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        }
    } catch (e: SQLiteException) {
        false
    }

    private fun File.isSqlite(): Boolean =
        exists() && inputStream().use { stream -> ByteArray(16).let { stream.read(it) == 16 && String(it, Charsets.US_ASCII) == "SQLite format 3\u0000" } }

    companion object {
        const val PREFIX = "arch-sme_backup_"
        const val KEEP = 7
        private const val BACKUP_DIR = "backups"

        /**
         * Where an automatic copy goes: app-specific shared storage, else app storage when that is missing
         * or can't be written (some Android builds hand the app a shared folder it may not write to).
         */
        internal fun autoBackupDir(shared: File?, local: File): File =
            shared?.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() } ?: local.apply { mkdirs() }
        internal const val MANIFEST = "manifest.properties"
        internal const val DATABASE = "shop_manager.db"
        internal const val SETTINGS = "shop_settings.preferences_pb"
        private const val APP_ID = "com.archsolutions.shopmanager"
        private const val KEY_APP = "app"
        private const val KEY_SCHEMA = "schemaVersion"
        private const val KEY_CREATED = "createdAt"

        internal fun settingsFile(context: Context) = File(context.filesDir, "datastore/$SETTINGS")
    }
}

/** A checked restore waiting for the next start; applied before Room or DataStore touch their files. */
object PendingRestore {
    private const val DIR = "pending_restore"
    private const val READY = "ready"

    internal fun stage(context: Context, database: File, settings: File?) {
        val dir = File(context.filesDir, DIR).apply { deleteRecursively(); mkdirs() }
        database.copyTo(File(dir, BackupManager.DATABASE))
        settings?.copyTo(File(dir, BackupManager.SETTINGS))
        // Written last: a restore cut off half-way is never applied.
        File(dir, READY).writeText("1")
    }

    /** Called from [com.archsolutions.shopmanager.app.ShopApp.onCreate], before dependency injection. */
    fun applyIfPending(context: Context) {
        val dir = File(context.filesDir, DIR)
        if (!File(dir, READY).exists()) {
            dir.deleteRecursively()
            return
        }
        // Before the copy: a crash in between leaves `ready`, so the next start applies it again with the marker set.
        FillMarker(context.filesDir).set()
        val target = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        target.parentFile?.mkdirs()
        listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
        File(dir, BackupManager.DATABASE).copyTo(target, overwrite = true)
        File(dir, BackupManager.SETTINGS).takeIf { it.exists() }?.let { settings ->
            BackupManager.settingsFile(context).also { it.parentFile?.mkdirs() }.let { settings.copyTo(it, overwrite = true) }
        }
        dir.deleteRecursively()
    }
}
