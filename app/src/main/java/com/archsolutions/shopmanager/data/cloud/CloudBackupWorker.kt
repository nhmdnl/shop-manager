package com.archsolutions.shopmanager.data.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Uploads the newest closed-day backup once the phone is online. Never throws: a surprise is a failed run. */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, CloudBackupEntryPoint::class.java)
            .runner().run().toWorkResult()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure()
    }

    companion object {
        const val TAG = "cloud-backup"

        fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudBackupEntryPoint {
    fun runner(): CloudBackupRunner
}
