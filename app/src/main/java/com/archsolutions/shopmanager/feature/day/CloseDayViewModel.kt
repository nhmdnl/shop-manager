package com.archsolutions.shopmanager.feature.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.data.db.entities.CountPhase
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.SaleStatus
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.PurchaseRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.BalanceCalculator
import com.archsolutions.shopmanager.domain.BalanceStatus
import com.archsolutions.shopmanager.domain.DayBalance
import com.archsolutions.shopmanager.domain.Debtors
import com.archsolutions.shopmanager.domain.NoteCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A day with its live balance (6.1). Shared by Close the day, Day closed and Yesterday is still open. */
data class DayWithBalance(val day: BusinessDayEntity, val balance: DayBalance, val lastSaleAt: Long?)

/** Combines a day's sales, money entries and purchases into its balance. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun dayWithBalance(
    dayFlow: Flow<BusinessDayEntity?>,
    saleRepo: SaleRepo,
    moneyRepo: MoneyRepo,
    purchaseRepo: PurchaseRepo,
): Flow<DayWithBalance?> {
    return dayFlow.flatMapLatest { day ->
        if (day == null) {
            flowOf(null)
        } else {
            combine(
                saleRepo.getSalesForDay(day.id),
                moneyRepo.getEntriesForDay(day.id),
                purchaseRepo.getPurchasesForDay(day.id),
            ) { sales, entries, purchases ->
                DayWithBalance(
                    day = day,
                    balance = BalanceCalculator.compute(day, sales, entries, purchases),
                    lastSaleAt = sales.filter { it.status == SaleStatus.DONE }.maxOfOrNull { it.createdAt },
                )
            }
        }
    }
}

data class CloseDayUiState(
    val loaded: Boolean = false,
    val day: BusinessDayEntity? = null,
    val balance: DayBalance? = null,
    val allowedDifference: Long = 500L,
    val shopName: String = "",
    val countedCash: Long? = null,
    val countedMobile: Long? = null,
    val notes: Map<Long, Int> = emptyMap(),
    val coins: Long = 0L,
    val notesCounted: Boolean = false,
    val reason: String? = null,
    val reasonNote: String = "",
    val sendSummary: Boolean = true,
    val debtorsTotal: Long = 0L,
    val debtorsCount: Int = 0,
    val firstDebtorName: String? = null,
    val closing: Boolean = false,
    val closed: Boolean = false,
) {
    val cashDifference: Long? get() = countedCash?.let { it - (balance?.cash?.expected ?: 0L) }
    val mobileDifference: Long? get() = countedMobile?.let { it - (balance?.mobile?.expected ?: 0L) }
    val cashStatus: BalanceStatus? get() = cashDifference?.let { BalanceCalculator.status(it, allowedDifference) }
    val mobileStatus: BalanceStatus? get() = mobileDifference?.let { BalanceCalculator.status(it, allowedDifference) }
    val notesTotal: Long get() = NoteCounter.total(notes, coins)

    /** 6.6 step 3: Short or Over on either channel needs a reason. */
    val needsReason: Boolean
        get() = listOf(cashStatus, mobileStatus).any { it == BalanceStatus.SHORT || it == BalanceStatus.OVER }

    val canClose: Boolean
        get() = day?.status == DayStatus.OPEN && countedCash != null && countedMobile != null &&
            (!needsReason || reason != null) && !closing

    /** StepIndicator: 1 Check, 2 Count, 3 Close (ready). */
    val step: Int
        get() = when {
            countedCash == null && countedMobile == null -> 1
            !canClose -> 2
            else -> 3
        }
}

private data class CloseInputs(
    val countedCash: Long? = null,
    val countedMobile: Long? = null,
    val notes: Map<Long, Int> = emptyMap(),
    val coins: Long = 0L,
    val notesCounted: Boolean = false,
    val reason: String? = null,
    val reasonNote: String = "",
    val sendSummary: Boolean = true,
    val closing: Boolean = false,
    val closed: Boolean = false,
)

/** Close the day (6.6): check the balance, count, give a reason if needed, close. */
@HiltViewModel
class CloseDayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dayRepo: DayRepo,
    saleRepo: SaleRepo,
    moneyRepo: MoneyRepo,
    purchaseRepo: PurchaseRepo,
    customerRepo: CustomerRepo,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val date: String = savedStateHandle.get<String>("date").orEmpty()
    private val inputs = MutableStateFlow(CloseInputs())

    private val _closeFailed = MutableStateFlow(false)
    val closeFailed: StateFlow<Boolean> = _closeFailed.asStateFlow()

    private val debtors = combine(customerRepo.getAll(), saleRepo.getCreditSales(), moneyRepo.getRepayments()) { customers, sales, repayments ->
        Debtors.compute(customers, sales, repayments)
    }

    val state: StateFlow<CloseDayUiState> = combine(
        inputs,
        dayWithBalance(dayRepo.getDayByDate(date), saleRepo, moneyRepo, purchaseRepo),
        settingsStore.settings,
        debtors,
    ) { input, current, settings, owing ->
        CloseDayUiState(
            loaded = true,
            day = current?.day,
            balance = current?.balance,
            allowedDifference = settings.allowedDifference,
            shopName = settings.shopName,
            countedCash = input.countedCash,
            countedMobile = input.countedMobile,
            notes = input.notes,
            coins = input.coins,
            notesCounted = input.notesCounted,
            reason = input.reason,
            reasonNote = input.reasonNote,
            sendSummary = input.sendSummary,
            debtorsTotal = owing.sumOf { it.owed },
            debtorsCount = owing.size,
            firstDebtorName = owing.firstOrNull()?.customer?.name,
            closing = input.closing,
            closed = input.closed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloseDayUiState())

    fun setCountedCash(amount: Long?) = inputs.update { it.copy(countedCash = amount, notesCounted = false) }

    fun setCountedMobile(amount: Long?) = inputs.update { it.copy(countedMobile = amount) }

    /** The note counter fills Cash counted, as on Open the day. */
    fun setNoteCount(denomination: Long, count: Int) = inputs.update {
        val notes = it.notes + (denomination to count.coerceAtLeast(0))
        it.copy(notes = notes, notesCounted = true, countedCash = NoteCounter.total(notes, it.coins))
    }

    fun setCoins(amount: Long?) = inputs.update {
        val coins = amount ?: 0L
        it.copy(coins = coins, notesCounted = true, countedCash = NoteCounter.total(it.notes, coins))
    }

    fun setReason(reason: String) = inputs.update { it.copy(reason = reason) }

    fun setReasonNote(note: String) = inputs.update { it.copy(reasonNote = note) }

    fun setSendSummary(send: Boolean) = inputs.update { it.copy(sendSummary = send) }

    fun closeDay() {
        val current = state.value
        val day = current.day ?: return
        val balance = current.balance ?: return
        if (!current.canClose) return
        inputs.update { it.copy(closing = true) }
        viewModelScope.launch {
            try {
                val reason = if (current.needsReason) {
                    listOfNotNull(current.reason, current.reasonNote.trim().ifEmpty { null }).joinToString(" — ")
                } else {
                    null
                }
                dayRepo.closeDay(
                    dayId = day.id,
                    countedCash = current.countedCash ?: 0L,
                    countedMobile = current.countedMobile ?: 0L,
                    expectedCash = balance.cash.expected,
                    expectedMobile = balance.mobile.expected,
                    diffCash = current.cashDifference ?: 0L,
                    diffMobile = current.mobileDifference ?: 0L,
                    diffReason = reason,
                    counts = if (current.notesCounted) {
                        NoteCounter.toCashCounts(current.notes, current.coins, CountPhase.CLOSE)
                    } else {
                        emptyList()
                    },
                )
                inputs.update { it.copy(closing = false, closed = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                inputs.update { it.copy(closing = false) }
                _closeFailed.value = true
            }
        }
    }

    fun closeFailedShown() {
        _closeFailed.value = false
    }
}

/** Day closed (addendum DayClosed) and Yesterday is still open: one day by id or date, with its balance. */
@HiltViewModel
class DayRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    dayRepo: DayRepo,
    saleRepo: SaleRepo,
    moneyRepo: MoneyRepo,
    purchaseRepo: PurchaseRepo,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val dayFlow: Flow<BusinessDayEntity?> = savedStateHandle.get<Long>("dayId")
        ?.let { dayRepo.getDayById(it) }
        ?: dayRepo.getDayByDate(savedStateHandle.get<String>("date").orEmpty())

    val record: StateFlow<DayWithBalance?> = dayWithBalance(dayFlow, saleRepo, moneyRepo, purchaseRepo)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
