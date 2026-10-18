package com.archsolutions.shopmanager.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.currentDay
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.dao.LowStockItem
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.feature.sales.SaleRow
import com.archsolutions.shopmanager.feature.sales.toRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    dayGate: DayGate,
    saleRepo: SaleRepo,
    customerRepo: CustomerRepo,
    productRepo: ProductRepo,
    clock: AppClock,
) : ViewModel() {
    /** 7.1: up to five low-stock products, lowest first, with their preferred supplier. */
    val lowStock: StateFlow<List<LowStockItem>> = productRepo.getLowStockWithSupplier()
        .map { it.take(LOW_STOCK_ROWS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 7.1: the last five sales of the current day, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentSales: StateFlow<List<SaleRow>> = dayGate.state
        .map { it.currentDay?.id }
        .distinctUntilChanged()
        .flatMapLatest { dayId ->
            if (dayId == null) {
                flowOf(emptyList())
            } else {
                combine(saleRepo.getSalesWithItemsForDay(dayId), customerRepo.getAll()) { sales, customers ->
                    val names = customers.associate { it.id to it.name }
                    sales.filter { it.sale.status == SaleStatus.DONE }
                        .take(RECENT_SALES)
                        .map { it.toRow(clock.zoneId, names) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        const val RECENT_SALES = 5
        const val LOW_STOCK_ROWS = 5
    }
}
