package com.archsolutions.shopmanager.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.SeedDataLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** A full-screen step the gate shows once per business date (6.3-1, 6.3-2). */
sealed interface GatePrompt {
    data object OpenDay : GatePrompt
    data class YesterdayOpen(val date: String) : GatePrompt
}

@HiltViewModel
class DayGateViewModel @Inject constructor(
    private val dayGate: DayGate,
    seedDataLoader: SeedDataLoader,
) : ViewModel() {
    private val seeded = MutableStateFlow(false)
    private var promptedKey: String? = null

    init {
        // The gate must not judge an empty database that the seed is about to fill.
        viewModelScope.launch {
            seedDataLoader.loadSeedDataIfEmpty()
            seeded.value = true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DayGateState> = seeded
        .flatMapLatest { ready -> if (ready) dayGate.state else flowOf(DayGateState.Loading) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayGateState.Loading)

    val today: StateFlow<String> = dayGate.today

    val zoneId: ZoneId = dayGate.zoneId

    fun onForeground() = dayGate.refreshDate()

    /**
     * The screen to show for the current gate state, at most once per business date and state. Backing
     * out of it leaves Home locked ("gate dismissed") instead of showing it again.
     */
    fun consumePrompt(): GatePrompt? {
        val prompt = when (val gate = state.value) {
            DayGateState.OpenDayRequired -> GatePrompt.OpenDay
            is DayGateState.PreviousDayStillOpen -> GatePrompt.YesterdayOpen(gate.day.date)
            else -> return null
        }
        val key = "${today.value}|$prompt"
        if (promptedKey == key) return null
        promptedKey = key
        return prompt
    }
}
