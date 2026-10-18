package com.archsolutions.shopmanager.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.currentDay
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.dao.LowStockItem
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.feature.day.dayWithBalance
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Home "Today" card once the day is closed: sales, profit and what customers owe. */
data class TodaySummary(val sales: Long, val profit: Long, val owed: Long)

@HiltViewModel
class HomeViewModel @Inject constructor(
    dayGate: DayGate,
    saleRepo: SaleRepo,
    customerRepo: CustomerRepo,
    productRepo: ProductRepo,
    private val dayRepo: DayRepo,
    moneyRepo: MoneyRepo,
    purchaseRepo: PurchaseRepo,
    clock: AppClock,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val todaySummary: StateFlow<TodaySummary?> = combine(
        dayWithBalance(dayGate.state.map { it.currentDay }.distinctUntilChanged(), saleRepo, moneyRepo, purchaseRepo),
        customerRepo.getTotalOwedToShop(),
    ) { record, owed ->
        record?.let { TodaySummary(sales = it.balance.totalSales, profit = it.balance.profit, owed = owed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // DECISION: the PIN check for reopening (addendum C4) arrives with the PIN dialog in M9.
    /** 6.3-3: reopening clears the close fields and counts the reopen; the gate then shows the day as open. */
    fun reopenDay(dayId: Long) {
        viewModelScope.launch { dayRepo.reopenDay(dayId) }
    }

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
