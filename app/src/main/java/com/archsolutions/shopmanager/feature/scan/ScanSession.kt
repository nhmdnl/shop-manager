package com.archsolutions.shopmanager.feature.scan

import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.domain.Barcodes

/**
 * Debounce per code: a code is accepted unless the same code (by [Barcodes.key]) was accepted
 * less than [debounceMillis] ago. Ignored repeats do not extend the window.
 *
 * Codes may arrive from different threads (the camera analyzer, test code, a second collector), so [accept]
 * is synchronized: the check and the update of the window are one step.
 */
class ScanSession(private val clock: AppClock, private val debounceMillis: Long = 1_500L) {
    private val lastAccepted = HashMap<String, Long>()

    @Synchronized
    fun accept(code: String): Boolean {
        val key = Barcodes.key(code)
        val now = clock.nowMillis()
        val last = lastAccepted[key]
        if (last != null && now - last < debounceMillis) return false
        lastAccepted[key] = now
        return true
    }
}
