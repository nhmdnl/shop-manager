package com.archsolutions.shopmanager.feature.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.repo.CustomerRepo
import com.archsolutions.shopmanager.data.repo.MoneyRepo
import com.archsolutions.shopmanager.data.repo.SaleRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.Debtor
import com.archsolutions.shopmanager.domain.Debtors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DebtorsUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val debtors: List<Debtor> = emptyList(),
    val totalOwed: Long = 0L,
    val count: Int = 0,
    val shopName: String = "",
)

/** Owed to the shop (addendum B). */
@HiltViewModel
class DebtorsViewModel @Inject constructor(
    customerRepo: CustomerRepo,
    saleRepo: SaleRepo,
    moneyRepo: MoneyRepo,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val debtors = combine(customerRepo.getAll(), saleRepo.getCreditSales(), moneyRepo.getRepayments()) { customers, sales, repayments ->
        Debtors.compute(customers, sales, repayments)
    }

    val state: StateFlow<DebtorsUiState> = combine(debtors, query, settingsStore.settings) { all, text, settings ->
        val trimmed = text.trim()
        DebtorsUiState(
            loaded = true,
            query = text,
            debtors = all.filter {
                trimmed.isEmpty() || it.customer.name.contains(trimmed, ignoreCase = true) || it.customer.phone?.contains(trimmed) == true
            },
            totalOwed = all.sumOf { it.owed },
            count = all.size,
            shopName = settings.shopName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtorsUiState())

    fun setQuery(text: String) {
        query.value = text
    }
}
