package com.archsolutions.shopmanager.data.cloud.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.archsolutions.cloud.contract.Features
import com.archsolutions.shopmanager.data.cloud.CloudApi
import com.archsolutions.shopmanager.data.cloud.CloudStore
import com.archsolutions.shopmanager.data.db.dao.SyncDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Plans every sync run and watches the outbox, the link and the plan; only these works call [SyncRunner.run]. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncDao: SyncDao,
    private val store: CloudStore,
    private val repo: SyncRepo,
    private val runner: SyncRunner,
    private val marker: FillMarker,
    private val api: CloudApi?,
) {
    private val started = AtomicBoolean(false)

    // Test signals: how many collectors `start` launched, and how many values each watcher has finished handling.
    internal val collectorsLaunched = AtomicInteger(0)
    internal val outboxValuesHandled = MutableStateFlow(0)
    internal val liveValuesHandled = MutableStateFlow(0)

    /** Idempotent, never throws. Each collector catches and drops its own exceptions. */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        if (api == null) {
            // A restore from a synced tablet on a build without a cloud URL: nothing else would ever turn sync off.
            // Works an earlier cloud build left behind would only wake the phone to do nothing.
            guard { cancelAll() }
            scope.launch {
                try {
                    if (repo.isEnabled()) repo.disable()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not turn sync off", e)
                }
            }
            return
        }
        collectorsLaunched.incrementAndGet()
        scope.launch {
            var previous = false
            guardCollect(syncDao.hasOutbox()) { has ->
                try {
                    if (has && !previous) runSoon()
                    previous = has
                } finally {
                    outboxValuesHandled.update { it + 1 }
                }
            }
        }
        collectorsLaunched.incrementAndGet()
        scope.launch {
            guardCollect(store.snapshot.map { it.linked }.distinctUntilChanged()) { linked ->
                // The periodic run only exists while linked: a shop that never links is never woken.
                if (linked) {
                    guard { enqueuePeriodic() }
                } else {
                    guard { cancelAll() }
                    runner.disableIfUnlinked()
                }
            }
        }
        collectorsLaunched.incrementAndGet()
        scope.launch {
            var previous: Boolean? = null
            guardCollect(store.snapshot.map { it.linked to (Features.LIVE in it.status?.features.orEmpty()) }.distinctUntilChanged()) { (linked, live) ->
                try {
                    if (shouldRunNow(previous, live, linked, repo.isEnabled(), marker.isSet())) runNow()
                } finally {
                    previous = live
                    liveValuesHandled.update { it + 1 }
                }
            }
        }
    }

    fun runSoon() = enqueueOnce(SOON, 120, ExistingWorkPolicy.KEEP)

    fun runNow() = enqueueOnce(NOW, 0, ExistingWorkPolicy.REPLACE)

    /** APPEND_OR_REPLACE so a worker planning its own follow-up never cancels itself (the CloseReminder pattern). */
    fun runAfter(seconds: Int) = enqueueOnce(LATER, maxOf(seconds, 30), ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun enqueueOnce(name: String, delaySeconds: Int, policy: ExistingWorkPolicy) {
        if (api == null) return
        guard {
            val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(SyncWorker.constraints())
                .setBackoffCriteria(SyncWorker.BACKOFF, SyncWorker.BACKOFF_SECONDS, SyncWorker.BACKOFF_UNIT)
                .setInitialDelay(delaySeconds.toLong(), TimeUnit.SECONDS)
                .addTag(SyncWorker.TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
        }
    }

    private fun cancelAll() {
        val work = WorkManager.getInstance(context)
        listOf(PERIODIC, SOON, NOW, LATER).forEach { work.cancelUniqueWork(it) }
    }

    private fun enqueuePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(SyncWorker.constraints())
            .setBackoffCriteria(SyncWorker.BACKOFF, SyncWorker.BACKOFF_SECONDS, SyncWorker.BACKOFF_UNIT)
            .addTag(SyncWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync scheduling step failed", e)
        }
    }

    private suspend fun <T> guardCollect(flow: kotlinx.coroutines.flow.Flow<T>, onEach: suspend (T) -> Unit) {
        try {
            flow.collect { value ->
                try {
                    onEach(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Sync watcher step failed", e)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync watcher stopped", e)
        }
    }

    companion object {
        const val PERIODIC = "cloud-sync"
        const val SOON = "cloud-sync-soon"
        const val NOW = "cloud-sync-now"
        const val LATER = "cloud-sync-later"
        private const val TAG = "SyncScheduler"
    }
}

/**
 * Sync turns on within seconds of a payment (A2-E8). The first value (an app start) only counts when the tablet
 * is linked and sync is off or a restore left the marker; every later `false -> true` counts when linked.
 */
internal fun shouldRunNow(previousLive: Boolean?, live: Boolean, linked: Boolean, enabled: Boolean, markerSet: Boolean): Boolean = when {
    !live || !linked -> false
    previousLive == null -> !enabled || markerSet
    else -> !previousLive
}
