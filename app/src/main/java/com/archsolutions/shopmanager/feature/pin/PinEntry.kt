package com.archsolutions.shopmanager.feature.pin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.archsolutions.shopmanager.core.security.Pin

/**
 * The digits on a keypad host and whether a check is running. While [checking], digits and backspace are ignored, so
 * extra taps during a slow hash can't start a second check with the same PIN (N3).
 */
class PinEntry {
    var digits by mutableStateOf("")
        private set
    var checking by mutableStateOf(false)
        private set

    /** Adds a digit. Returns the full PIN once it is complete (and starts [checking]); null otherwise or while checking. */
    fun digit(d: String): String? {
        if (checking) return null
        if (digits.length < Pin.LENGTH) digits += d
        if (digits.length != Pin.LENGTH) return null
        checking = true
        return digits
    }

    fun backspace() {
        if (!checking) digits = digits.dropLast(1)
    }

    /** The check is over; [clear] empties the digits (a failed try). */
    fun finish(clear: Boolean) {
        if (clear) digits = ""
        checking = false
    }
}
