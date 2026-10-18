package com.archsolutions.shopmanager.feature.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.currentDay
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SalesFilter { ALL, CASH, MOBILE_MONEY, CREDIT }

data class SalesUiState(
    val loaded: Boolean = false,
    val date: String? = null,
    val filter: SalesFilter = SalesFilter.ALL,
    val rows: List<SaleRow> = emptyList(),
    // Voided sales stay in the list but count in no total.
    val saleCount: Int = 0,
    val salesTotal: Long = 0L,
)

@HiltViewModel
class SalesViewModel @Inject constructor(
    dayGate: DayGate,
    saleRepo: SaleRepo,
    customerRepo: CustomerRepo,
    clock: AppClock,
) : ViewModel() {
    private val filter = MutableStateFlow(SalesFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SalesUiState> = dayGate.state
        .map { it.currentDay }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            if (day == null) {
                filter.map { SalesUiState(loaded = true, filter = it) }
            } else {
                combine(saleRepo.getSalesWithItemsForDay(day.id), customerRepo.getAll(), filter) { sales, customers, selected ->
                    val names = customers.associate { it.id to it.name }
                    val done = sales.filter { it.sale.status == SaleStatus.DONE }
                    SalesUiState(
                        loaded = true,
                        date = day.date,
                        filter = selected,
                        rows = sales
                            .filter { selected.matches(it.sale.method) }
                            .map { it.toRow(clock.zoneId, names) },
                        saleCount = done.size,
                        salesTotal = done.sumOf { it.sale.total },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SalesUiState())

    fun setFilter(value: SalesFilter) {
        filter.value = value
    }

    private fun SalesFilter.matches(method: PayMethod): Boolean = when (this) {
        SalesFilter.ALL -> true
        SalesFilter.CASH -> method == PayMethod.CASH
        SalesFilter.MOBILE_MONEY -> method == PayMethod.MOBILE_MONEY
        SalesFilter.CREDIT -> method == PayMethod.CREDIT
    }
}
