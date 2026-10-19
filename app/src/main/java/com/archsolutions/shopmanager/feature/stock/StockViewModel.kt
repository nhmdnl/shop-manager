package com.archsolutions.shopmanager.feature.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import com.archsolutions.shopmanager.domain.isLowStock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Stock chips (7.3): All, Low N, then one per category. */
sealed interface StockFilter {
    data object All : StockFilter
    data object Low : StockFilter
    data class Category(val id: Long) : StockFilter
}

data class StockRow(val product: ProductEntity, val categoryName: String?) {
    val isLow: Boolean get() = product.isLowStock
}

data class StockUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val filter: StockFilter = StockFilter.All,
    val categories: List<CategoryEntity> = emptyList(),
    val rows: List<StockRow> = emptyList(),
    val productCount: Int = 0,
    val lowCount: Int = 0,
    val stockValue: Long = 0L,
)

private data class StockInputs(val query: String = "", val filter: StockFilter = StockFilter.All)

@HiltViewModel
class StockViewModel @Inject constructor(
    productRepo: ProductRepo,
) : ViewModel() {
    private val inputs = MutableStateFlow(StockInputs())

    val state: StateFlow<StockUiState> = combine(
        inputs,
        productRepo.getAllActive(),
        productRepo.getCategories(),
        productRepo.getTotalStockValue(),
    ) { input, products, categories, value ->
        val categoryNames = categories.associate { it.id to it.name }
        val query = input.query.trim()
        val all = products.map { StockRow(it, it.categoryId?.let(categoryNames::get)) }
        StockUiState(
            loaded = true,
            query = input.query,
            filter = input.filter,
            categories = categories,
            // Low items first so the ones to reorder are on top.
            rows = all
                .filter { row ->
                    when (val filter = input.filter) {
                        StockFilter.All -> true
                        StockFilter.Low -> row.isLow
                        is StockFilter.Category -> row.product.categoryId == filter.id
                    } && (query.isEmpty() || row.product.name.contains(query, ignoreCase = true) || row.product.barcode == query)
                }
                .sortedWith(compareByDescending<StockRow> { it.isLow }.thenBy { it.product.name.lowercase() }),
            productCount = products.size,
            lowCount = all.count { it.isLow },
            stockValue = value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun setQuery(query: String) = inputs.update { it.copy(query = query) }

    fun setFilter(filter: StockFilter) = inputs.update { it.copy(filter = filter) }
}
