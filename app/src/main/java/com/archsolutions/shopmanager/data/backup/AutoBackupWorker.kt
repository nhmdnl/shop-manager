package com.archsolutions.shopmanager.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.archsolutions.shopmanager.BuildConfig
import com.archsolutions.shopmanager.data.cloud.CloudBackupWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Section 11: a backup in app storage after each closed day, off the main thread and surviving a quick exit. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java).backupManager().autoBackup()
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            var chain = WorkManager.getInstance(context).beginUniqueWork(
                "auto-backup",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoBackupWorker>().build(),
            )
            // The cloud upload follows the local backup, and only in builds that have a cloud URL.
            if (BuildConfig.CLOUD_BASE_URL.isNotBlank()) chain = chain.then(CloudBackupWorker.request())
            chain.enqueue()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupEntryPoint {
    fun backupManager(): BackupManager
}
