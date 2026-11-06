package com.archsolutions.shopmanager.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns one scanning session (debounce window and running count) for the screen that hosts it, so the
 * state survives rotation. The host decides what a known product does through [onCode]'s callback.
 */
@HiltViewModel
class ScanViewModel internal constructor(
    lookup: suspend (String) -> ProductEntity?,
    clock: AppClock,
) : ViewModel() {
    @Inject constructor(products: ProductRepo, clock: AppClock) : this({ products.findByBarcode(it) }, clock)

    private val controller = ScanController(ScanSession(clock), lookup)
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Looks the code up; for a known product runs [onKnown] first, then counts it. Repeats inside the window do nothing. */
    fun onCode(code: ScannedCode, onKnown: suspend (ProductEntity) -> Unit) {
        viewModelScope.launch {
            val outcome = controller.handle(code)
            if (outcome is ScanOutcome.Known) onKnown(outcome.product)
            _state.update { it.reduce(outcome) { p -> p.name } }
        }
    }
}
