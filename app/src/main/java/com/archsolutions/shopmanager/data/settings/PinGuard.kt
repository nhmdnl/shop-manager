package com.archsolutions.shopmanager.data.settings

import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.core.util.AppClock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val triesLeft: Int) : PinResult
    data class Locked(val waitMillis: Long) : PinResult
}

/**
 * Checks the PIN and enforces C4: after 5 wrong tries, wait 30 seconds. The count lives for the
 * process; restarting the app to skip the wait is no faster than waiting.
 */
@Singleton
class PinGuard @Inject constructor(
    private val settingsStore: SettingsStore,
    private val clock: AppClock,
) {
    private var failures = 0
    private var lockedUntil = 0L

    @Synchronized
    fun lockedForMillis(): Long = (lockedUntil - clock.nowMillis()).coerceAtLeast(0L)

    suspend fun check(pin: String): PinResult {
        lockedForMillis().takeIf { it > 0L }?.let { return PinResult.Locked(it) }
        val matches = Pin.matches(pin, settingsStore.settings.first().pinHash)
        return synchronized(this) {
            when {
                matches -> {
                    failures = 0
                    PinResult.Ok
                }
                ++failures >= Pin.MAX_TRIES -> {
                    failures = 0
                    lockedUntil = clock.nowMillis() + Pin.LOCKOUT_MILLIS
                    PinResult.Locked(Pin.LOCKOUT_MILLIS)
                }
                else -> PinResult.Wrong(triesLeft = Pin.MAX_TRIES - failures)
            }
        }
    }
}
