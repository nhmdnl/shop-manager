package com.archsolutions.shopmanager.app

import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Where the shop stands in the open → trade → close cycle (spec 6.3). */
sealed interface DayGateState {
    /** Not evaluated yet. */
    data object Loading : DayGateState

    /** 6.3-1: an earlier day was never closed. It must be closed before today can open. */
    data class PreviousDayStillOpen(val day: BusinessDayEntity) : DayGateState

    /** 6.3-2: no day for today and `openDayOnStart` is on, so the app shows Open the day. */
    data object OpenDayRequired : DayGateState

    /** No day for today and `openDayOnStart` is off: Home stays locked until the day is opened. */
    data object DayNotOpen : DayGateState

    /** 6.3-3: today is closed; Home is read-only. */
    data class TodayClosed(val day: BusinessDayEntity) : DayGateState

    /** 6.3-4: today is open; selling, purchases and money entries are allowed. */
    data class Ready(val day: BusinessDayEntity) : DayGateState
}

/** The day screens list records for: today's, or the earlier day still waiting to be closed. */
val DayGateState.currentDay: BusinessDayEntity?
    get() = when (this) {
        is DayGateState.Ready -> day
        is DayGateState.TodayClosed -> day
        is DayGateState.PreviousDayStillOpen -> day
        DayGateState.Loading, DayGateState.OpenDayRequired, DayGateState.DayNotOpen -> null
    }

// DECISION: with openDayOnStart off, v1.0 opened the day automatically on the first sale, but the
// v1.1 addendum (HomeLocked board, which wins) keeps Home locked with an "Open the day" card instead.
fun evaluateDayGate(
    previousOpenDay: BusinessDayEntity?,
    today: BusinessDayEntity?,
    openDayOnStart: Boolean,
): DayGateState = when {
    previousOpenDay != null -> DayGateState.PreviousDayStillOpen(previousOpenDay)
    today == null -> if (openDayOnStart) DayGateState.OpenDayRequired else DayGateState.DayNotOpen
    today.status == DayStatus.CLOSED -> DayGateState.TodayClosed(today)
    else -> DayGateState.Ready(today)
}

/**
 * Live gate state for the current business date. Room and DataStore flows keep it current after
 * opening or closing a day; [refreshDate] moves it to a new date after midnight.
 */
@Singleton
class DayGate @Inject constructor(
    dayRepo: DayRepo,
    settingsStore: SettingsStore,
    private val clock: AppClock,
) {
    private val _today = MutableStateFlow(clock.todayDateString())

    /** Business date (yyyy-MM-dd) the gate is evaluated for. */
    val today: StateFlow<String> = _today.asStateFlow()

    val zoneId: ZoneId get() = clock.zoneId

    /** Re-reads the date. Called whenever the app returns to the foreground. */
    fun refreshDate() {
        _today.value = clock.todayDateString()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<DayGateState> = _today
        .flatMapLatest { date ->
            combine(
                dayRepo.getOpenDayBefore(date),
                dayRepo.getDayByDate(date),
                settingsStore.settings.map { it.openDayOnStart }.distinctUntilChanged(),
            ) { previousOpenDay, today, openDayOnStart ->
                evaluateDayGate(previousOpenDay, today, openDayOnStart)
            }
        }
        .distinctUntilChanged()
}
