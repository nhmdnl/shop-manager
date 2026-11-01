package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import com.archsolutions.cloud.contract.BackupDto
import com.archsolutions.shopmanager.data.backup.BackupManager
import com.archsolutions.shopmanager.data.backup.RestoreResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** What the Restore screen needs: the shop's cloud backups and staging one. A fake in its tests. */
interface CloudBackups {
    /** Where a download lands before it is staged; the caller deletes it. */
    fun tempFile(): File
    suspend fun list(): List<BackupDto>
    suspend fun download(id: String, dest: File)
    suspend fun restore(file: File): RestoreResult
}

class CloudBackupsImpl @Inject constructor(
    private val repo: CloudRepo,
    private val store: CloudStore,
    private val backupManager: BackupManager,
    @param:ApplicationContext private val context: Context,
) : CloudBackups {
    override fun tempFile() = File(context.cacheDir, "cloud-restore.zip")

    // The cloud datastore is not in a backup zip, so a fresh or restored install learns its last cloud backup here.
    override suspend fun list() = repo.call { it.listBackups() }.also { list ->
        list.maxOfOrNull { it.uploadedAt }?.let { store.noteCloudBackup(it) }
    }
    override suspend fun download(id: String, dest: File) = repo.call { it.downloadBackup(id, dest) }
    override suspend fun restore(file: File) = backupManager.restoreFromFile(file)
}
