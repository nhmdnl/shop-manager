package com.archsolutions.shopmanager.feature.suppliers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.dao.SupplierProductRow
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.domain.Reorder
import com.archsolutions.shopmanager.domain.ReorderLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** A low product on the reorder card: ticked by default, quantity editable before sending (5.6). */
data class ReorderItem(val line: ReorderLine, val checked: Boolean, val qty: Long)

data class SupplierDetailUiState(
    val supplier: SupplierEntity? = null,
    val products: List<SupplierProductRow> = emptyList(),
    val reorder: List<ReorderItem> = emptyList(),
    val shopName: String = "",
) {
    val orderItems: List<ReorderItem> get() = reorder.filter { it.checked }
}

@HiltViewModel
class SupplierDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    supplierRepo: SupplierRepo,
    settingsStore: SettingsStore,
) : ViewModel() {
    // Phones arrive through SupplierDetailRoute(id); the tablet pane picks the supplier with select().
    private val supplierId = MutableStateFlow(savedStateHandle.get<Long>("id") ?: 0L)
    private val unchecked = MutableStateFlow<Set<Long>>(emptySet())
    private val quantities = MutableStateFlow<Map<Long, Long>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SupplierDetailUiState> = supplierId.flatMapLatest { id ->
        combine(
            supplierRepo.getById(id),
            supplierRepo.getSupplierProductRows(),
            unchecked,
            quantities,
            settingsStore.settings,
        ) { supplier, rows, off, qty, settings ->
            SupplierDetailUiState(
                supplier = supplier,
                products = rows.filter { it.supplierId == id },
                reorder = Reorder.lines(rows, id).map { line ->
                    ReorderItem(line, checked = line.productId !in off, qty = qty[line.productId] ?: line.suggested)
                },
                shopName = settings.shopName,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupplierDetailUiState())

    fun select(id: Long) {
        if (supplierId.value == id) return
        unchecked.value = emptySet()
        quantities.value = emptyMap()
        supplierId.value = id
    }

    fun toggle(productId: Long) = unchecked.update { if (productId in it) it - productId else it + productId }

    fun setQty(productId: Long, qty: Long) = quantities.update { it + (productId to qty.coerceAtLeast(1L)) }
}
