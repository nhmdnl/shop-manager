package com.archsolutions.shopmanager.feature.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.data.db.entities.CountPhase
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.NoteCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpenDayUiState(
    val loaded: Boolean = false,
    val date: String = "",
    val shopName: String = "",
    val yesterdayCash: Long? = null,
    val yesterdayMobile: Long? = null,
    val cash: Long? = null,
    val mobile: Long? = null,
    val notes: Map<Long, Int> = emptyMap(),
    val coins: Long = 0L,
    // True while Cash equals the note count; typing Cash by hand drops the counts so they are not saved.
    val notesCounted: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val started: Boolean = false,
) {
    val notesTotal: Long get() = NoteCounter.total(notes, coins)
}

@HiltViewModel
class OpenDayViewModel @Inject constructor(
    private val dayRepo: DayRepo,
    private val dayGate: DayGate,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OpenDayUiState())
    val state: StateFlow<OpenDayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val lastClosed = dayRepo.getLatestClosedDayDirect()
            // DECISION: "Yesterday closed with" and the Matches check always use the last closed day;
            // carryClosingToOpening only decides whether its counts prefill the fields.
            val carry = settings.carryClosingToOpening
            _state.update {
                it.copy(
                    loaded = true,
                    date = dayGate.today.value,
                    shopName = settings.shopName,
                    yesterdayCash = lastClosed?.countedCash,
                    yesterdayMobile = lastClosed?.countedMobile,
                    cash = if (carry) lastClosed?.countedCash else null,
                    mobile = if (carry) lastClosed?.countedMobile else null,
                )
            }
        }
    }

    fun setCash(value: Long?) = _state.update { it.copy(cash = value, notesCounted = false) }

    fun setMobile(value: Long?) = _state.update { it.copy(mobile = value) }

    fun setNoteCount(denomination: Long, count: Int) = _state.update {
        val notes = it.notes + (denomination to count.coerceAtLeast(0))
        it.copy(notes = notes, notesCounted = true, cash = NoteCounter.total(notes, it.coins))
    }

    fun setCoins(amount: Long?) = _state.update {
        val coins = amount ?: 0L
        it.copy(coins = coins, notesCounted = true, cash = NoteCounter.total(it.notes, coins))
    }

    fun startDay() {
        val current = _state.value
        if (current.saving || current.started) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            dayGate.refreshDate()
            try {
                dayRepo.openDay(
                    date = dayGate.today.value,
                    openingCash = current.cash ?: 0L,
                    openingMobile = current.mobile ?: 0L,
                    counts = if (current.notesCounted) {
                        NoteCounter.toCashCounts(current.notes, current.coins, CountPhase.OPEN)
                    } else {
                        emptyList()
                    },
                )
                _state.update { it.copy(saving = false, started = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun saveErrorShown() = _state.update { it.copy(saveFailed = false) }
}
