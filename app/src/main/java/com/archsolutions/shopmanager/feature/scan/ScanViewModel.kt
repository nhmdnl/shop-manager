package com.archsolutions.shopmanager.feature.scan

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.repo.ProductRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Owns one scanning session (debounce window and running count) for the screen that hosts it, so the
 * state survives rotation; count, last item and the not-found code are also kept in the [SavedStateHandle],
 * so they survive the process being killed (the debounce window does not need to).
 * The host decides what a known product does through [onCode]'s callback.
 *
 * [onKnown] must be idempotent: a lookup or callback that throws keeps the code's debounce stamp, so
 * the same code is ignored for 1.5 s and the host may see a retry after that.
 *
 * Single-shot hosts: camera frames keep arriving while the screen leaves, so [onCode] can be called again
 * for the same code after the first known result. [ScanScreen] guards that for single-shot mode; the host's
 * own result callback must still be idempotent.
 */
@HiltViewModel
class ScanViewModel internal constructor(
    lookup: suspend (String) -> ProductEntity?,
    clock: AppClock,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    @Inject constructor(products: ProductRepo, clock: AppClock, saved: SavedStateHandle) :
        this({ products.findByBarcode(it) }, clock, saved)

    private val controller = ScanController(ScanSession(clock), lookup)
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /**
     * Looks the code up; for a known product runs [onKnown] first, then counts it. Repeats inside the window do nothing.
     * A failing lookup or [onKnown] is logged and the scan is treated as not handled (state unchanged).
     */
    fun onCode(code: ScannedCode, onKnown: suspend (ProductEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val outcome = controller.handle(code)
                if (outcome is ScanOutcome.Known) onKnown(outcome.product)
                persist(_state.updateAndGet { it.reduce(outcome) { p -> p.name } })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ScanViewModel", "Scan not handled", e)
            }
        }
    }

    /** Drops the "Not found" banner (the host is taking the code to Add this product), in saved state too. */
    fun clearNotFound() {
        persist(_state.updateAndGet { it.copy(notFound = null) })
    }

    private fun persist(next: ScanUiState) {
        saved[K_COUNT] = next.count
        saved[K_LAST] = next.last
        saved[K_PRICE] = next.lastPrice
        saved[K_NOT_FOUND] = next.notFound
        saved[K_RESULTS] = next.results
    }

    private fun restore() = ScanUiState(
        count = saved[K_COUNT] ?: 0,
        last = saved[K_LAST],
        notFound = saved[K_NOT_FOUND],
        results = saved[K_RESULTS] ?: 0,
        lastPrice = saved.get<Long>(K_PRICE),
    )

    private companion object {
        const val K_COUNT = "scan_count"
        const val K_LAST = "scan_last"
        const val K_PRICE = "scan_price"
        const val K_NOT_FOUND = "scan_not_found"
        const val K_RESULTS = "scan_results"
    }
}
