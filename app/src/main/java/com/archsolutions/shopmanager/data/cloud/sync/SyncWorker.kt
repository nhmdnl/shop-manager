package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Runs one sync. Never throws except to cancel: a stop at WorkManager's 10-minute limit keeps every
 * acknowledged batch, and the fill goes on next run.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val deps = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java)
        val result = deps.syncRunner().run()
        when (result.outcome) {
            SyncOutcome.RETRY -> Result.retry()
            SyncOutcome.WAIT -> {
                deps.syncScheduler().runAfter(result.waitSeconds)
                Result.success()
            }
            else -> Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure()
    }

    companion object {
        const val TAG = "cloud-sync"

        fun constraints(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val BACKOFF = BackoffPolicy.EXPONENTIAL
        const val BACKOFF_SECONDS = 30L
        val BACKOFF_UNIT = TimeUnit.SECONDS
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncRunner(): SyncRunner
    fun syncScheduler(): SyncScheduler
}
