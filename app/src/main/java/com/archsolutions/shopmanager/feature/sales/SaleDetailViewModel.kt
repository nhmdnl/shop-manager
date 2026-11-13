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
import com.archsolutions.shopmanager.data.staff.StaffSession
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
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.domain.staff.Permission
import com.archsolutions.shopmanager.feature.pin.pinNeededFlow
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
    /** The Void sheet will ask for a PIN: the real gate decision (shared PIN, or an Owner PIN for a Cashier). */
    val pinNeeded: Boolean = false,
    val voiding: Boolean = false,
    val soldBy: String? = null,
    val voidedBy: String? = null,
)

@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saleRepo: SaleRepo,
    dayRepo: DayRepo,
    customerRepo: CustomerRepo,
    settingsStore: SettingsStore,
    clock: AppClock,
    private val staffSession: StaffSession,
    staffRepo: StaffRepo,
) : ViewModel() {
    private val saleId = savedStateHandle.toRoute<SaleDetailRoute>().id
    private val voiding = MutableStateFlow(false)
    private val pinNeeded = pinNeededFlow(settingsStore.settings, staffSession.current, staffRepo.staffActive, Permission.VOID_SALE)

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
                val soldBy = saleRepo.staffName(sale.staffId)
                val voidedBy = saleRepo.staffName(sale.voidedByStaffId)
                combine(dayRepo.getDayById(sale.dayId), customer, pinNeeded, voiding) { day, buyer, needsPin, isVoiding ->
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
                        pinNeeded = needsPin,
                        voiding = isVoiding,
                        soldBy = soldBy,
                        voidedBy = voidedBy,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaleDetailUiState())

    fun void(reason: String, note: String) {
        if (voiding.value) return
        // R1: read the actor now, before any coroutine; the PIN gate clears the approver right after this returns.
        val staffId = staffSession.actingId
        voiding.value = true
        viewModelScope.launch {
            try {
                saleRepo.voidSale(saleId, if (note.isBlank()) reason else "$reason — ${note.trim()}", staffId)
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
