package com.archsolutions.shopmanager.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.data.db.dao.ReportItemRow
import com.archsolutions.shopmanager.data.db.dao.ReportSaleRow
import com.archsolutions.shopmanager.data.repo.ReportRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.ClosedDayRow
import com.archsolutions.shopmanager.domain.ReportData
import com.archsolutions.shopmanager.domain.ReportPeriod
import com.archsolutions.shopmanager.domain.ReportPreset
import com.archsolutions.shopmanager.domain.Reports
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class ReportTab { OVERVIEW, DAYS }

data class ReportsUiState(
    val loaded: Boolean = false,
    val tab: ReportTab = ReportTab.OVERVIEW,
    val preset: ReportPreset = ReportPreset.WEEK,
    val today: LocalDate? = null,
    val report: ReportData? = null,
    val closedDays: List<ClosedDayRow> = emptyList(),
    val allowedDifference: Long = 0L,
    val shopName: String = "",
    // The raw rows the CSV export writes out.
    val sales: List<ReportSaleRow> = emptyList(),
    val items: List<ReportItemRow> = emptyList(),
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    reportRepo: ReportRepo,
    settingsStore: SettingsStore,
    dayGate: DayGate,
) : ViewModel() {
    private val tab = MutableStateFlow(ReportTab.OVERVIEW)
    private val preset = MutableStateFlow(ReportPreset.WEEK)
    private val customPeriod = MutableStateFlow<ReportPeriod?>(null)

    val zoneId: ZoneId = dayGate.zoneId

    private val period = combine(dayGate.today, preset, customPeriod) { todayText, chosen, custom ->
        val today = LocalDate.parse(todayText)
        today to (custom?.takeIf { chosen == ReportPreset.CUSTOM } ?: ReportPeriod.of(chosen, today))
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportsUiState> = period.flatMapLatest { (today, range) ->
        combine(
            reportRepo.getSales(range.from, range.to),
            reportRepo.getItems(range.from, range.to),
            reportRepo.getClosedDays(range.from, range.to),
            settingsStore.settings,
            combine(tab, preset, ::Pair),
        ) { sales, items, days, settings, (shownTab, shownPreset) ->
            ReportsUiState(
                loaded = true,
                tab = shownTab,
                preset = shownPreset,
                today = today,
                report = Reports.build(range, sales, items),
                closedDays = Reports.closedDays(days, sales),
                allowedDifference = settings.allowedDifference,
                shopName = settings.shopName,
                sales = sales,
                items = items,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun setTab(value: ReportTab) {
        tab.value = value
        // The Days tab lists closed days, so it has no "Today" chip (design board).
        if (value == ReportTab.DAYS && preset.value == ReportPreset.TODAY) preset.value = ReportPreset.WEEK
    }

    fun setPreset(value: ReportPreset) {
        preset.value = value
    }

    fun setCustom(from: LocalDate, to: LocalDate) {
        customPeriod.value = ReportPeriod(minOf(from, to), maxOf(from, to))
        preset.value = ReportPreset.CUSTOM
    }
}
