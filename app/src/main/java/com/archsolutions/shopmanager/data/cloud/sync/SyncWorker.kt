package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
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
    override suspend fun doWork(): Result = syncWorkResult(
        run = { EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).syncRunner().run() },
        runAfter = { EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).syncScheduler().runAfter(it) },
    )

    companion object {
        const val TAG = "cloud-sync"

        fun constraints(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val BACKOFF = BackoffPolicy.EXPONENTIAL
        const val BACKOFF_SECONDS = 30L
        val BACKOFF_UNIT = TimeUnit.SECONDS
    }
}

/**
 * The mapping from a run to the worker's [Result], without WorkManager or Hilt so the JVM can test it. A retry retries,
 * a wait plans a later run and succeeds, anything thrown fails the work, and only cancellation is rethrown.
 */
internal suspend fun syncWorkResult(run: suspend () -> SyncResult, runAfter: (Int) -> Unit): ListenableWorker.Result = try {
    val result = run()
    when (result.outcome) {
        SyncOutcome.RETRY -> ListenableWorker.Result.retry()
        SyncOutcome.WAIT -> {
            runAfter(result.waitSeconds)
            ListenableWorker.Result.success()
        }
        else -> ListenableWorker.Result.success()
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ListenableWorker.Result.failure()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun syncRunner(): SyncRunner
    fun syncScheduler(): SyncScheduler
}
