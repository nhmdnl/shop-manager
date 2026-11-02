package com.archsolutions.shopmanager.data.cloud.sync

import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.cloud.contract.Features
import com.archsolutions.cloud.contract.shop.MAX_BATCH_ROWS
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.cloud.CloudException
import com.archsolutions.shopmanager.data.cloud.CloudRepo
import com.archsolutions.shopmanager.data.cloud.CloudState
import com.archsolutions.shopmanager.data.cloud.CloudStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncOutcome { SENT, NOTHING_TO_SEND, OFF, NOT_LINKED, PAUSED, REPLACED, REJECTED, WAIT, RETRY }

/** One sync run: status, then a fill if needed, then batches until the outbox is empty. */
@Singleton
class SyncRunner @Inject internal constructor(
    private val cloud: CloudRepo,
    private val store: CloudStore,
    private val repo: SyncRepo,
    private val marker: FillMarker,
    private val clock: AppClock,
) {
    /** The delay behind the last [SyncOutcome.WAIT], in seconds. */
    @Volatile var lastWaitSeconds: Int = 60
        private set

    /** Waits its turn (not tryLock): the periodic, soon, now and later works never interleave, so one sender at a time. */
    suspend fun run(): SyncOutcome = mutex.withLock { runLocked() }

    private suspend fun runLocked(): SyncOutcome {
        if (cloud.state.first() is CloudState.Off) return SyncOutcome.OFF
        if (store.token() == null) {
            if (repo.isEnabled()) repo.disable()
            return SyncOutcome.NOT_LINKED
        }

        val status = try {
            cloud.call { it.status() }.also { store.saveStatus(it) }
        } catch (e: IOException) {
            return SyncOutcome.RETRY
        } catch (e: CloudException) {
            return failure(e, batch = false)
        }

        if (Features.LIVE !in status.features) {
            if (repo.isEnabled()) repo.disable()
            marker.clear()
            return SyncOutcome.OFF
        }

        if (!repo.isEnabled() || !repo.hasFill() || marker.isSet() || (status.resync && !repo.fillInFlight())) {
            repo.startFill()
            marker.clear()
        }

        var rows = MAX_BATCH_ROWS
        var sent = false
        while (true) {
            val batch = try {
                repo.nextBatch(rows)
            } catch (e: IOException) {
                return SyncOutcome.RETRY
            }
            if (batch == null) {
                // nextBatch is also null for entries with no active fill; that is not "nothing left".
                if (repo.hasOutbox()) return SyncOutcome.RETRY
                store.setLastSyncAt(clock.nowMillis())
                return if (sent) SyncOutcome.SENT else SyncOutcome.NOTHING_TO_SEND
            }
            try {
                val reply = cloud.call { it.sendBatch(batch) }
                // A bad reply must never delete entries that were never sent, nor stall the loop.
                if (reply.ackSeq < batch.fromSeq) return SyncOutcome.RETRY
                repo.ack(reply.copy(ackSeq = minOf(reply.ackSeq, batch.toSeq)))
                sent = true
            } catch (e: IOException) {
                return SyncOutcome.RETRY
            } catch (e: CloudException) {
                if (e.http == 413) {
                    if (rows > SMALL_BATCH_ROWS) {
                        rows = SMALL_BATCH_ROWS
                        continue
                    }
                    return SyncOutcome.RETRY
                }
                return failure(e, batch = true)
            }
        }
    }

    /** Maps a server answer to an outcome; revoked and unauthorized also turn sync off (the link itself is handled by [CloudRepo.call]). */
    private suspend fun failure(e: CloudException, batch: Boolean): SyncOutcome = when {
        e.code == ErrorCode.DEVICE_REVOKED -> {
            repo.disable()
            SyncOutcome.REPLACED
        }
        e.code == ErrorCode.UNAUTHORIZED -> {
            repo.disable()
            SyncOutcome.NOT_LINKED
        }
        batch && (e.code == ErrorCode.PLAN_LAPSED || e.http == 402) -> SyncOutcome.PAUSED
        e.http == 429 -> {
            lastWaitSeconds = e.retryAfterSeconds ?: 60
            SyncOutcome.WAIT
        }
        // 5xx, and a 2xx whose body did not decode (a captive portal): try again later.
        e.http !in 400..499 -> SyncOutcome.RETRY
        else -> SyncOutcome.REJECTED
    }

    private companion object {
        val mutex = Mutex()
        const val SMALL_BATCH_ROWS = 100
    }
}
