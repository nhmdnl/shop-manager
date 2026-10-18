package com.archsolutions.shopmanager.feature.sales

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.archsolutions.shopmanager.app.SaleDetailRoute
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.SaleItemEntity
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SaleDetailUiState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val time: String = "",
    val date: String = "",
    val method: PayMethod = PayMethod.CASH,
    val customerName: String? = null,
    val items: List<SaleItemEntity> = emptyList(),
    val total: Long = 0L,
    val cashReceived: Long? = null,
    val change: Long? = null,
    val voided: Boolean = false,
    val voidReason: String? = null,
    // 5.2 / 6.7: only a DONE sale of the OPEN day can be voided.
    val canVoid: Boolean = false,
    val pinEnabled: Boolean = false,
    val voiding: Boolean = false,
)

@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saleRepo: SaleRepo,
    dayRepo: DayRepo,
    customerRepo: CustomerRepo,
    settingsStore: SettingsStore,
    clock: AppClock,
) : ViewModel() {
    private val saleId = savedStateHandle.toRoute<SaleDetailRoute>().id
    private val voiding = MutableStateFlow(false)

    private val _voidFailed = MutableStateFlow(false)
    val voidFailed: StateFlow<Boolean> = _voidFailed.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SaleDetailUiState> = saleRepo.getSaleWithItems(saleId)
        .flatMapLatest { saleWithItems ->
            if (saleWithItems == null) {
                flowOf(SaleDetailUiState(loaded = true, notFound = true))
            } else {
                val sale = saleWithItems.sale
                val customer = sale.customerId?.let { customerRepo.getById(it) } ?: flowOf(null)
                combine(dayRepo.getDayById(sale.dayId), customer, settingsStore.settings, voiding) { day, buyer, settings, isVoiding ->
                    SaleDetailUiState(
                        loaded = true,
                        time = DayFormat.time(sale.createdAt, clock.zoneId),
                        date = day?.date.orEmpty(),
                        method = sale.method,
                        customerName = buyer?.name,
                        items = saleWithItems.items,
                        total = sale.total,
                        cashReceived = sale.cashReceived,
                        change = sale.cashReceived?.minus(sale.total)?.takeIf { it >= 0 },
                        voided = sale.status == SaleStatus.VOIDED,
                        voidReason = sale.voidReason,
                        canVoid = sale.status == SaleStatus.DONE && day?.status == DayStatus.OPEN,
                        pinEnabled = settings.pinEnabled,
                        voiding = isVoiding,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaleDetailUiState())

    fun void(reason: String, note: String) {
        if (voiding.value) return
        voiding.value = true
        viewModelScope.launch {
            try {
                saleRepo.voidSale(saleId, if (note.isBlank()) reason else "$reason — ${note.trim()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _voidFailed.value = true
            } finally {
                voiding.value = false
            }
        }
    }

    fun voidFailedShown() {
        _voidFailed.value = false
    }
}
