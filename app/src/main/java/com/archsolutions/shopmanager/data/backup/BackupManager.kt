package com.archsolutions.shopmanager.data.backup

import android.content.Context
import android.net.Uri
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.AppDatabase
import com.archsolutions.shopmanager.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
        val out = context.contentResolver.openOutputStream(uri) ?: error("Can't write $uri")
        out.use(::writeZip)
        settingsStore.setLastBackupAt(clock.nowMillis())
    }

    /**
     * The automatic copy after closing a day, in app-specific storage; the newest [KEEP] stay. It stays on
     * this device, so it doesn't count as "last backup" (the Home reminder is about a lost phone).
     */
    suspend fun autoBackup(): File = withContext(Dispatchers.IO) {
        val dir = (context.getExternalFilesDir(BACKUP_DIR) ?: File(context.filesDir, BACKUP_DIR)).apply { mkdirs() }
        val file = File(dir, fileName())
        file.outputStream().use(::writeZip)
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        file
    }

    suspend fun restoreFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext RestoreResult.NOT_A_BACKUP
        input.use { stageRestore(it) }
    }

    internal fun writeZip(out: OutputStream) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            Properties().apply {
                setProperty(KEY_APP, APP_ID)
                setProperty(KEY_SCHEMA, AppDatabase.VERSION.toString())
                setProperty(KEY_CREATED, clock.nowMillis().toString())
            }.store(zip, null)
            zip.closeEntry()
            zip.putFile(DATABASE, context.getDatabasePath(AppDatabase.DATABASE_NAME))
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

    private fun File.isSqlite(): Boolean =
        exists() && inputStream().use { stream -> ByteArray(16).let { stream.read(it) == 16 && String(it, Charsets.US_ASCII) == "SQLite format 3\u0000" } }

    companion object {
        const val PREFIX = "shopmanager_backup_"
        const val KEEP = 7
        private const val BACKUP_DIR = "backups"
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
