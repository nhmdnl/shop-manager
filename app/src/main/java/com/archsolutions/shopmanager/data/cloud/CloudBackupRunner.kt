package com.archsolutions.shopmanager.data.cloud

import androidx.work.ListenableWorker
import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

enum class CloudBackupOutcome { SENT, NOT_LINKED, NOTHING_TO_SEND, PAUSED, REPLACED, SIGNED_OUT, REJECTED, RETRY }

/** Only a retryable failure asks WorkManager to come back; every other outcome is final for this run. */
fun CloudBackupOutcome.toWorkResult(): ListenableWorker.Result =
    if (this == CloudBackupOutcome.RETRY) ListenableWorker.Result.retry() else ListenableWorker.Result.success()

/** Sends the newest closed-day backup to the cloud (BAK-01, BAK-08). */
class CloudBackupRunner internal constructor(
    private val repo: CloudRepo,
    private val store: CloudStore,
    private val clock: AppClock,
    private val newestBackup: () -> File?,
) {
    @Inject constructor(repo: CloudRepo, store: CloudStore, backups: BackupManager, clock: AppClock) :
        this(repo, store, clock, backups::newestAutoBackup)

    suspend fun run(): CloudBackupOutcome {
        if (repo.state.first() !is CloudState.Linked) return CloudBackupOutcome.NOT_LINKED
        val zip = newestBackup() ?: return CloudBackupOutcome.NOTHING_TO_SEND
        return try {
            val sha = withContext(Dispatchers.IO) { sha256Hex(zip) }
            repo.call { it.uploadBackup(zip, sha) }
            store.setLastCloudBackup(clock.nowMillis())
            CloudBackupOutcome.SENT
        } catch (e: CloudException) {
            when {
                e.code == ErrorCode.PLAN_LAPSED -> {
                    repo.refreshStatus()
                    CloudBackupOutcome.PAUSED
                }
                e.code == ErrorCode.DEVICE_REVOKED -> CloudBackupOutcome.REPLACED
                e.code == ErrorCode.UNAUTHORIZED -> CloudBackupOutcome.SIGNED_OUT
                // 5xx, 429, and a 2xx whose body did not decode (a captive portal): try again later.
                e.http == 429 || e.http !in 400..499 -> CloudBackupOutcome.RETRY
                else -> CloudBackupOutcome.REJECTED
            }
        } catch (e: IOException) {
            CloudBackupOutcome.RETRY
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
