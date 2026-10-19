package com.archsolutions.shopmanager.feature.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.currentDay
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.domain.BalanceCalculator
import com.archsolutions.shopmanager.domain.DayBalance
import com.archsolutions.shopmanager.feature.sales.itemsSummary
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

enum class MovementKind { SALE, CREDIT_SALE, CREDIT_PAID, EXPENSE, PURCHASE, CASH_OUT, CASH_IN }

enum class ChannelFilter { ALL, CASH, MOBILE_MONEY }

/** One line of "Money in and out today" (6.5). Texts are joined in the UI so they can be translated. */
data class MoneyMovement(
    val key: String,
    val createdAt: Long,
    val time: String,
    val kind: MovementKind,
    /** Items summary, customer or supplier name, or note, depending on [kind]. */
    val detail: String?,
    /** EXPENSE: the stored category key ("Transport"). */
    val category: String?,
    /** PURCHASE: number of products bought. */
    val itemCount: Int,
    /** CASH or MOBILE_MONEY; null for credit sales and unpaid purchases, which move no money. */
    val channel: MoneyMethod?,
    val amount: Long,
) {
    val isMoneyIn: Boolean get() = channel != null && kind in setOf(MovementKind.SALE, MovementKind.CREDIT_PAID, MovementKind.CASH_IN)
    val isMoneyOut: Boolean get() = channel != null && kind in setOf(MovementKind.EXPENSE, MovementKind.PURCHASE, MovementKind.CASH_OUT)
}

data class DayUiState(
    val loaded: Boolean = false,
    val day: BusinessDayEntity? = null,
    val balance: DayBalance? = null,
    val movements: List<MoneyMovement> = emptyList(),
    val filter: ChannelFilter = ChannelFilter.ALL,
)

/** Today's money (6.5): four stats and every money movement of the current day, newest first. */
@HiltViewModel
class DayViewModel @Inject constructor(
    dayGate: DayGate,
    saleRepo: SaleRepo,
    moneyRepo: MoneyRepo,
    purchaseRepo: PurchaseRepo,
    customerRepo: CustomerRepo,
    supplierRepo: SupplierRepo,
    clock: AppClock,
) : ViewModel() {
    private val filter = MutableStateFlow(ChannelFilter.ALL)
    private val names = combine(customerRepo.getAll(), supplierRepo.getAllActive()) { customers, suppliers ->
        customers.associate { it.id to it.name } to suppliers.associate { it.id to it.name }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DayUiState> = dayGate.state
        .map { it.currentDay }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            if (day == null) {
                filter.map { DayUiState(loaded = true, filter = it) }
            } else {
                val records = combine(
                    saleRepo.getSalesWithItemsForDay(day.id),
                    moneyRepo.getEntriesForDay(day.id),
                    purchaseRepo.getPurchasesWithItemsForDay(day.id),
                ) { sales, entries, purchases -> Triple(sales, entries, purchases) }
                combine(records, names, filter) { (sales, entries, purchases), (customerNames, supplierNames), selected ->
                    fun time(millis: Long) = DayFormat.time(millis, clock.zoneId)
                    val movements = buildList {
                        sales.filter { it.sale.status == SaleStatus.DONE }.forEach { (sale, items) ->
                            val credit = sale.method == PayMethod.CREDIT
                            add(
                                MoneyMovement(
                                    key = "sale-${sale.id}",
                                    createdAt = sale.createdAt,
                                    time = time(sale.createdAt),
                                    kind = if (credit) MovementKind.CREDIT_SALE else MovementKind.SALE,
                                    detail = if (credit) {
                                        listOfNotNull(sale.customerId?.let(customerNames::get), itemsSummary(items)).joinToString(" · ")
                                    } else {
                                        itemsSummary(items)
                                    },
                                    category = null,
                                    itemCount = items.size,
                                    channel = when (sale.method) {
                                        PayMethod.CASH -> MoneyMethod.CASH
                                        PayMethod.MOBILE_MONEY -> MoneyMethod.MOBILE_MONEY
                                        PayMethod.CREDIT -> null
                                    },
                                    amount = sale.total,
                                )
                            )
                        }
                        entries.forEach { entry ->
                            add(
                                MoneyMovement(
                                    key = "money-${entry.id}",
                                    createdAt = entry.createdAt,
                                    time = time(entry.createdAt),
                                    kind = when (entry.type) {
                                        MoneyEntryType.EXPENSE -> MovementKind.EXPENSE
                                        MoneyEntryType.CREDIT_REPAYMENT -> MovementKind.CREDIT_PAID
                                        MoneyEntryType.CASH_OUT -> MovementKind.CASH_OUT
                                        MoneyEntryType.CASH_IN -> MovementKind.CASH_IN
                                    },
                                    detail = if (entry.type == MoneyEntryType.CREDIT_REPAYMENT) {
                                        entry.customerId?.let(customerNames::get)
                                    } else {
                                        entry.note
                                    },
                                    category = entry.category,
                                    itemCount = 0,
                                    channel = entry.method,
                                    amount = entry.amount,
                                )
                            )
                        }
                        purchases.forEach { (purchase, items) ->
                            add(
                                MoneyMovement(
                                    key = "purchase-${purchase.id}",
                                    createdAt = purchase.createdAt,
                                    time = time(purchase.createdAt),
                                    kind = MovementKind.PURCHASE,
                                    detail = purchase.supplierId?.let(supplierNames::get),
                                    category = null,
                                    itemCount = items.size,
                                    channel = when (purchase.paid) {
                                        PurchasePaid.CASH -> MoneyMethod.CASH
                                        PurchasePaid.MOBILE_MONEY -> MoneyMethod.MOBILE_MONEY
                                        PurchasePaid.NOT_PAID -> null
                                    },
                                    amount = purchase.total,
                                )
                            )
                        }
                    }.sortedByDescending { it.createdAt }
                    DayUiState(
                        loaded = true,
                        day = day,
                        balance = BalanceCalculator.compute(day, sales.map { it.sale }, entries, purchases.map { it.purchase }),
                        movements = movements.filter { movement ->
                            when (selected) {
                                ChannelFilter.ALL -> true
                                ChannelFilter.CASH -> movement.channel == MoneyMethod.CASH
                                ChannelFilter.MOBILE_MONEY -> movement.channel == MoneyMethod.MOBILE_MONEY
                            }
                        },
                        filter = selected,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState())

    fun setFilter(value: ChannelFilter) {
        filter.value = value
    }
}
