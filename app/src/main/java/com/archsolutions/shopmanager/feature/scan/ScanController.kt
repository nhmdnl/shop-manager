package com.archsolutions.shopmanager.feature.scan

import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.data.db.entities.ProductEntity

sealed interface ScanOutcome {
    data class Known(val product: ProductEntity) : ScanOutcome
    data class NotFound(val code: String) : ScanOutcome
    data object Ignored : ScanOutcome
}

class ScanController(
    private val session: ScanSession,
    private val lookup: suspend (String) -> ProductEntity?,
) {
    /** Asks the session first; only an accepted scan reaches the lookup. */
    suspend fun handle(code: ScannedCode): ScanOutcome {
        if (!session.accept(code.text)) return ScanOutcome.Ignored
        val product = lookup(code.text) ?: return ScanOutcome.NotFound(code.text)
        return ScanOutcome.Known(product)
    }
}

data class ScanUiState(val count: Int = 0, val last: String? = null, val notFound: String? = null)

fun ScanUiState.reduce(outcome: ScanOutcome, line: (ProductEntity) -> String): ScanUiState = when (outcome) {
    is ScanOutcome.Known -> copy(count = count + 1, last = line(outcome.product), notFound = null)
    is ScanOutcome.NotFound -> copy(notFound = outcome.code)
    ScanOutcome.Ignored -> this
}
