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

@HiltViewModel
class DayGateViewModel @Inject constructor(
    private val dayGate: DayGate,
    seedDataLoader: SeedDataLoader,
) : ViewModel() {
    private val seeded = MutableStateFlow(false)
    private var openDayPromptedFor: String? = null

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
     * True at most once per business date, when the gate needs Open the day (6.3-2). Backing out of
     * Open the day leaves Home locked ("gate dismissed") instead of reopening the screen.
     */
    fun consumeOpenDayPrompt(): Boolean {
        val date = today.value
        if (state.value != DayGateState.OpenDayRequired || openDayPromptedFor == date) return false
        openDayPromptedFor = date
        return true
    }
}
