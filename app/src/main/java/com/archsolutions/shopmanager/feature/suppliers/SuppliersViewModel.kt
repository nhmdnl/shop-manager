package com.archsolutions.shopmanager.feature.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.repo.SupplierRepo
import com.archsolutions.shopmanager.domain.LowAcrossSuppliers
import com.archsolutions.shopmanager.domain.Reorder
import com.archsolutions.shopmanager.domain.SupplierSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SuppliersUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val lowOnly: Boolean = false,
    val suppliers: List<SupplierSummary> = emptyList(), // after search and the low-only filter
    val hasAny: Boolean = false,
    val lowAcross: LowAcrossSuppliers = LowAcrossSuppliers(items = 0, suppliers = 0),
)

@HiltViewModel
class SuppliersViewModel @Inject constructor(
    supplierRepo: SupplierRepo,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val lowOnly = MutableStateFlow(false)

    val state: StateFlow<SuppliersUiState> = combine(
        supplierRepo.getAllActive(),
        supplierRepo.getSupplierProductRows(),
        query,
        lowOnly,
    ) { suppliers, rows, text, low ->
        // Most low items first, as on the design board, then by name.
        val all = Reorder.summaries(suppliers, rows)
            .sortedWith(compareByDescending<SupplierSummary> { it.lowCount }.thenBy { it.supplier.name.lowercase() })
        SuppliersUiState(
            loaded = true,
            query = text,
            lowOnly = low,
            suppliers = all.filter { (!low || it.lowCount > 0) && it.supplier.matches(text) },
            hasAny = all.isNotEmpty(),
            lowAcross = Reorder.lowAcross(all, rows),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuppliersUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun toggleLowOnly() = lowOnly.update { !it }
}

private fun SupplierEntity.matches(query: String): Boolean {
    val text = query.trim()
    return text.isEmpty() || listOfNotNull(name, contactPerson, phone).any { it.contains(text, ignoreCase = true) }
}
