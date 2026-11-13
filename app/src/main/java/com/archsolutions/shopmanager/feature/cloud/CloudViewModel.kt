package com.archsolutions.shopmanager.feature.cloud

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.archsolutions.cloud.contract.Features
import com.archsolutions.cloud.contract.PlanState
import com.archsolutions.cloud.contract.ShopStatus
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.backup.AutoBackupWorker
import com.archsolutions.shopmanager.data.cloud.CloudBackupWorker
import com.archsolutions.shopmanager.data.cloud.CloudLink
import com.archsolutions.shopmanager.data.cloud.CloudState
import com.archsolutions.shopmanager.data.cloud.sync.CloudSync
import com.archsolutions.shopmanager.data.cloud.sync.SyncStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CloudCardState(
    val visible: Boolean = false,
    val signedIn: Boolean = false,
    val planLine: String? = null,
    val backupLine: String = "",
    val canBackUpNow: Boolean = false,
    val ownerViewLine: String? = null,
)

enum class CloudNotice { ENDS_SOON, GRACE, PAUSED }

enum class BackupDay { TODAY, YESTERDAY, EARLIER }

/** The card's texts. The Android one reads strings.xml; tests use plain English ones. */
interface CloudTexts {
    fun replaced(): String
    fun paused(): String
    fun waiting(): String
    fun noBackup(): String
    fun lastBackup(day: BackupDay, at: Long, zone: ZoneId): String
    fun trialUntil(at: Long, zone: ZoneId): String
    fun activeUntil(at: Long, zone: ZoneId): String
    fun payBy(at: Long, zone: ZoneId): String
    fun ownerViewOn(day: BackupDay, at: Long, zone: ZoneId): String
    fun ownerViewOnNever(): String
    fun ownerViewFilling(): String
    fun ownerViewWaiting(): String
}

/** WorkManager as the card sees it, so tests need no real WorkManager. */
interface CloudWork {
    /** Every work tagged [CloudBackupWorker.TAG]. */
    val workInfos: Flow<List<WorkInfo>>

    /** The local backup, then the cloud upload behind it. */
    fun backUpNow()
}

@Singleton
class WorkManagerCloudWork @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CloudWork {
    override val workInfos: Flow<List<WorkInfo>>
        get() = WorkManager.getInstance(context).getWorkInfosByTagFlow(CloudBackupWorker.TAG)

    override fun backUpNow() = AutoBackupWorker.enqueue(context)
}

@Singleton
class AndroidCloudTexts @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CloudTexts {
    // Read per call: a language change in Settings applies to the next text, not only to a new process.
    private fun localized(): Pair<Context, Locale> {
        val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return context to context.resources.configuration.locales[0]
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config) to locale
    }

    private fun date(at: Long, zone: ZoneId, locale: Locale) = DayFormat.dateOf(at, zone, "d MMM", locale)

    override fun replaced() = localized().first.getString(R.string.cloud_line_replaced)
    override fun paused() = localized().first.getString(R.string.cloud_line_paused)
    override fun waiting() = localized().first.getString(R.string.cloud_line_waiting)
    override fun noBackup() = localized().first.getString(R.string.cloud_line_none)

    override fun lastBackup(day: BackupDay, at: Long, zone: ZoneId): String {
        val (ctx, locale) = localized()
        val time = DayFormat.time(at, zone)
        val whenText = when (day) {
            BackupDay.TODAY -> ctx.getString(R.string.backup_today, time)
            BackupDay.YESTERDAY -> ctx.getString(R.string.cloud_yesterday, time)
            BackupDay.EARLIER -> "${date(at, zone, locale)} $time"
        }
        return ctx.getString(R.string.cloud_line_last, whenText)
    }

    override fun ownerViewOn(day: BackupDay, at: Long, zone: ZoneId): String {
        val (ctx, locale) = localized()
        val time = DayFormat.time(at, zone)
        val whenText = when (day) {
            BackupDay.TODAY -> ctx.getString(R.string.backup_today, time)
            BackupDay.YESTERDAY -> ctx.getString(R.string.cloud_yesterday, time)
            BackupDay.EARLIER -> "${date(at, zone, locale)} $time"
        }
        return ctx.getString(R.string.cloud_owner_view_on, whenText)
    }

    override fun ownerViewOnNever() = localized().first.getString(R.string.cloud_owner_view_on_never)
    override fun ownerViewFilling() = localized().first.getString(R.string.cloud_owner_view_filling)
    override fun ownerViewWaiting() = localized().first.getString(R.string.cloud_owner_view_waiting)

    override fun trialUntil(at: Long, zone: ZoneId): String {
        val (ctx, locale) = localized()
        return ctx.getString(R.string.cloud_plan_trial, date(at, zone, locale))
    }

    override fun activeUntil(at: Long, zone: ZoneId): String {
        val (ctx, locale) = localized()
        return ctx.getString(R.string.cloud_plan_active, date(at, zone, locale))
    }

    override fun payBy(at: Long, zone: ZoneId): String {
        val (ctx, locale) = localized()
        return ctx.getString(R.string.cloud_plan_grace, date(at, zone, locale))
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudUiModule {
    @Binds abstract fun bindCloudWork(work: WorkManagerCloudWork): CloudWork
    @Binds abstract fun bindCloudTexts(texts: AndroidCloudTexts): CloudTexts
}

/** A run ends with an empty outbox at least every 15 minutes online, so entries still waiting after 30 mean no connection. */
private const val WAITING_MILLIS = 30 * 60 * 1000L
private const val ENDS_SOON_MILLIS = 3 * 24 * 60 * 60 * 1000L

@HiltViewModel
class CloudViewModel @Inject constructor(
    private val link: CloudLink,
    private val work: CloudWork,
    private val sync: CloudSync,
    private val texts: CloudTexts,
    private val clock: AppClock,
) : ViewModel() {
    /** Bumped when the app language changes, so the lines are written again in the new language. */
    private val textsVersion = MutableStateFlow(0)

    // WorkManager can fail to read: "waiting" is then simply not shown.
    private val waiting: Flow<Boolean> = work.workInfos
        .combine(textsVersion) { infos, _ -> infos.any { it.state == WorkInfo.State.ENQUEUED } }
        .catch { emit(false) }

    val card: StateFlow<CloudCardState> = combine(
        combine(link.state, waiting, textsVersion) { state, isWaiting, _ -> state to isWaiting },
        sync.status,
    ) { (state, isWaiting), syncStatus ->
        cardFor(state, isWaiting, syncStatus)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudCardState())

    val notice: StateFlow<CloudNotice?> = link.state
        .combine(textsVersion) { state, _ -> noticeFor(state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Fetches the plan status; screens call it each time they come back, so a payment made meanwhile shows.
     * Offline or refused leaves the cached status in place.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                if (link.state.first() is CloudState.Linked) link.refreshStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun backUpNow() = work.backUpNow()

    fun unlink() {
        viewModelScope.launch { link.unlink() }
    }

    fun refreshTexts() = textsVersion.update { it + 1 }

    private fun cardFor(state: CloudState, isWaiting: Boolean, syncStatus: SyncStatus): CloudCardState {
        val zone = clock.zoneId
        return when (state) {
            CloudState.Off -> CloudCardState()
            CloudState.SignedOut -> CloudCardState(visible = true, backupLine = texts.noBackup())
            CloudState.Replaced -> CloudCardState(visible = true, backupLine = texts.replaced())
            is CloudState.Linked -> {
                val status = state.status
                val last = state.lastCloudBackupAt
                val backupLine = when {
                    status?.state == PlanState.LAPSED -> texts.paused()
                    isWaiting -> texts.waiting()
                    last != null -> texts.lastBackup(backupDay(last), last, zone)
                    else -> texts.noBackup()
                }
                CloudCardState(
                    visible = true,
                    signedIn = true,
                    planLine = status?.let { planLine(it, zone) },
                    backupLine = backupLine,
                    // The server refuses uploads while paused; the line already says to pay, and Pay sits below.
                    canBackUpNow = status?.state != PlanState.LAPSED,
                    ownerViewLine = if (status != null && Features.LIVE in status.features) ownerViewLine(syncStatus, zone) else null,
                )
            }
        }
    }

    private fun ownerViewLine(sync: SyncStatus, zone: ZoneId): String {
        val last = sync.lastSyncAt
        return when {
            sync.fillPending -> texts.ownerViewFilling()
            sync.hasOutbox && (last == null || clock.nowMillis() - last > WAITING_MILLIS) -> texts.ownerViewWaiting()
            last != null -> texts.ownerViewOn(backupDay(last), last, zone)
            else -> texts.ownerViewOnNever()
        }
    }

    private fun planLine(status: ShopStatus, zone: ZoneId): String? = when (status.state) {
        PlanState.TRIAL -> status.trialEnd?.let { texts.trialUntil(it, zone) }
        PlanState.ACTIVE -> status.periodEnd?.let { texts.activeUntil(it, zone) }
        PlanState.GRACE -> status.graceEnd?.let { texts.payBy(it, zone) }
        PlanState.LAPSED -> null // the backup line already says it
    }

    private fun backupDay(at: Long): BackupDay {
        val zone = clock.zoneId
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return when (day) {
            today -> BackupDay.TODAY
            today.minusDays(1) -> BackupDay.YESTERDAY
            else -> BackupDay.EARLIER
        }
    }

    private fun noticeFor(state: CloudState): CloudNotice? {
        val status = (state as? CloudState.Linked)?.status ?: return null
        return when (status.state) {
            PlanState.GRACE -> CloudNotice.GRACE
            PlanState.LAPSED -> CloudNotice.PAUSED
            PlanState.TRIAL, PlanState.ACTIVE -> {
                val end = (if (status.state == PlanState.TRIAL) status.trialEnd else status.periodEnd) ?: return null
                CloudNotice.ENDS_SOON.takeIf { end - clock.nowMillis() <= ENDS_SOON_MILLIS }
            }
        }
    }
}
