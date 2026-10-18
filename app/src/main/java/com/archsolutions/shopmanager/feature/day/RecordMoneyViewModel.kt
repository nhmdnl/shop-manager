package com.archsolutions.shopmanager.feature.day

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.app.DayGate
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.data.db.dao.CustomerWithOwed
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.domain.Debtors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Expense "What for" tiles (7.4). [key] is what money_entry.category stores. */
enum class ExpenseCategory(val key: String) {
    TRANSPORT("Transport"),
    RENT("Rent"),
    POWER("Power"),
    WATER("Water"),
    LUNCH("Lunch"),
    CLEANING("Cleaning"),
    AIRTIME("Airtime"),
    OTHER("Other"),
}

data class RecordMoneyUiState(
    val loaded: Boolean = false,
    val type: MoneyEntryType = MoneyEntryType.EXPENSE,
    val amount: Long? = null,
    val category: ExpenseCategory = ExpenseCategory.TRANSPORT,
    val method: MoneyMethod = MoneyMethod.CASH,
    val note: String = "",
    val customers: List<CustomerWithOwed> = emptyList(),
    val selectedCustomer: CustomerWithOwed? = null,
    /** When the selected customer's debt started (FIFO), for "Owes X · since 20 Sep". */
    val owedSinceMillis: Long? = null,
    val saving: Boolean = false,
) {
    /** Addendum CreditPaid: "After this X owes N"; negative means credit in the customer's favour. */
    val owedAfter: Long? get() = selectedCustomer?.let { it.owed - (amount ?: 0L) }

    /** Addendum C3: paying back more than is owed needs a confirmation. */
    val isOverpayment: Boolean
        get() = type == MoneyEntryType.CREDIT_REPAYMENT && selectedCustomer != null && (amount ?: 0L) > selectedCustomer.owed

    val canSave: Boolean
        get() = (amount ?: 0L) > 0L && !saving && (type != MoneyEntryType.CREDIT_REPAYMENT || selectedCustomer != null)
}

private data class RecordInputs(
    val type: MoneyEntryType,
    val amount: Long? = null,
    val category: ExpenseCategory = ExpenseCategory.TRANSPORT,
    val method: MoneyMethod = MoneyMethod.CASH,
    val note: String = "",
    val customerId: Long? = null,
    val saving: Boolean = false,
)

/** Record money (7.4, addendum CreditPaid / CashIn): expense, credit paid back, cash out, cash in. */
@HiltViewModel
class RecordMoneyViewModel @Inject constructor(
    private val moneyRepo: MoneyRepo,
    customerRepo: CustomerRepo,
    saleRepo: SaleRepo,
    dayGate: DayGate,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // RecordMoneyRoute(type, customerId): customerId 0 means none; the tablet Day panel has neither key.
    private val inputs = MutableStateFlow(
        RecordInputs(
            type = savedStateHandle.get<String>("type")?.let { runCatching { MoneyEntryType.valueOf(it) }.getOrNull() }
                ?: MoneyEntryType.EXPENSE,
            customerId = savedStateHandle.get<Long>("customerId")?.takeIf { it > 0 },
        )
    )
    private val gateState = dayGate.state.stateIn(viewModelScope, SharingStarted.Eagerly, DayGateState.Loading)

    private val _saved = MutableStateFlow<Long?>(null)

    /** Amount just saved, for the confirmation message. */
    val saved: StateFlow<Long?> = _saved.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val _confirmOverpayment = MutableStateFlow(false)
    val confirmOverpayment: StateFlow<Boolean> = _confirmOverpayment.asStateFlow()

    private val since = combine(customerRepo.getAll(), saleRepo.getCreditSales(), moneyRepo.getRepayments()) { customers, sales, repayments ->
        Debtors.compute(customers, sales, repayments).associate { it.customer.id to it.sinceMillis }
    }

    val state: StateFlow<RecordMoneyUiState> = combine(inputs, customerRepo.getAllWithOwed(), since) { input, customers, sinceById ->
        RecordMoneyUiState(
            loaded = true,
            type = input.type,
            amount = input.amount,
            category = input.category,
            method = input.method,
            note = input.note,
            customers = customers,
            selectedCustomer = customers.firstOrNull { it.customer.id == input.customerId },
            owedSinceMillis = input.customerId?.let(sinceById::get),
            saving = input.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordMoneyUiState())

    fun setType(type: MoneyEntryType) = inputs.update { it.copy(type = type) }

    fun setAmount(amount: Long?) = inputs.update { it.copy(amount = amount) }

    fun setCategory(category: ExpenseCategory) = inputs.update { it.copy(category = category) }

    fun setMethod(method: MoneyMethod) = inputs.update { it.copy(method = method) }

    fun setNote(note: String) = inputs.update { it.copy(note = note) }

    /** 0 clears the choice so the list shows again. */
    fun selectCustomer(customerId: Long) = inputs.update { it.copy(customerId = customerId.takeIf { id -> id > 0 }) }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        if (current.isOverpayment && !_confirmOverpayment.value) {
            _confirmOverpayment.value = true
            return
        }
        _confirmOverpayment.value = false
        inputs.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val gate = gateState.first { it != DayGateState.Loading }
                val dayId = (gate as? DayGateState.Ready)?.day?.id ?: throw IllegalStateException("The day is not open")
                val amount = current.amount ?: 0L
                val note = current.note.trim().ifEmpty { null }
                when (current.type) {
                    MoneyEntryType.EXPENSE -> moneyRepo.addExpense(dayId, amount, current.method, current.category.key, note)
                    MoneyEntryType.CREDIT_REPAYMENT -> moneyRepo.addCreditRepayment(
                        dayId,
                        current.selectedCustomer!!.customer.id,
                        amount,
                        current.method,
                        note,
                    )
                    MoneyEntryType.CASH_OUT -> moneyRepo.addCashOut(dayId, amount, current.method, note)
                    MoneyEntryType.CASH_IN -> moneyRepo.addCashIn(dayId, amount, current.method, note)
                }
                _saved.value = amount
                // Ready for the next entry of the same kind.
                inputs.update { it.copy(amount = null, note = "", saving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                inputs.update { it.copy(saving = false) }
                _saveFailed.value = true
            }
        }
    }

    /** "Keep as credit" in the overpayment dialog. */
    fun confirmOverpayment() {
        if (_confirmOverpayment.value) save()
    }

    fun dismissOverpayment() {
        _confirmOverpayment.value = false
    }

    fun savedShown() {
        _saved.value = null
    }

    fun saveFailedShown() {
        _saveFailed.value = false
    }
}
