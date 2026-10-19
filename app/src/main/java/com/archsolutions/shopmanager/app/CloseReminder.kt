package com.archsolutions.shopmanager.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 6.7: at `closeReminderTime`, if today is still OPEN, a notification leads to Close the day.
 * One one-time job is kept queued; each run queues the next evening's.
 */
@Singleton
class CloseReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
) {
    /** Plans the next reminder from now, replacing any planned one (app start, time changed). */
    fun schedule(time: String) = enqueue(time, earliest = now(), ExistingWorkPolicy.REPLACE)

    /**
     * Called by the running job. Appending (instead of replacing) keeps the running job from being
     * cancelled, and starting a minute ahead stops a run that fired early from planning tonight again.
     */
    internal fun scheduleNext(time: String) =
        enqueue(time, earliest = now().plusMinutes(1), ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun now(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), clock.zoneId)

    private fun enqueue(time: String, earliest: ZonedDateTime, policy: ExistingWorkPolicy) {
        val delay = Duration.between(now(), nextReminderAt(earliest, parseReminderTime(time)))
        val request = OneTimeWorkRequestBuilder<CloseReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun post() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        // Created on every post so the channel name follows the app language.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel))
                .build(),
        )
        val openCloseDay = Intent(context, MainActivity::class.java)
            .setAction(ACTION_CLOSE_DAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shop)
            .setColor(BRAND_GREEN)
            .setContentTitle(context.getString(R.string.reminder_close_title))
            .setContentText(context.getString(R.string.reminder_close_body))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, openCloseDay, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CLOSE_DAY = "com.archsolutions.shopmanager.action.CLOSE_DAY"
        private const val WORK_NAME = "close-day-reminder"
        private const val CHANNEL_ID = "close_day_reminder"
        private const val NOTIFICATION_ID = 6007
        private const val BRAND_GREEN = 0xFF1E6B47.toInt()
    }
}

/** "21:00" from settings; a broken value falls back to the default rather than dropping reminders. */
internal fun parseReminderTime(text: String): LocalTime =
    runCatching { LocalTime.parse(text) }.getOrDefault(LocalTime.of(21, 0))

/** The first [time] strictly after [now], in [now]'s zone. */
internal fun nextReminderAt(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(time).atZone(now.zone)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

class CloseReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, CloseReminderEntryPoint::class.java)
        val reminder = deps.closeReminder()
        val today = deps.dayRepo().getDayByDateDirect(deps.clock().todayDateString())
        if (today?.status == DayStatus.OPEN) reminder.post()
        reminder.scheduleNext(deps.settingsStore().settings.first().closeReminderTime)
        return Result.success()
    }
}

// WorkManager creates workers itself, so they reach Hilt objects through an entry point.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloseReminderEntryPoint {
    fun closeReminder(): CloseReminder
    fun dayRepo(): DayRepo
    fun settingsStore(): SettingsStore
    fun clock(): AppClock
}
