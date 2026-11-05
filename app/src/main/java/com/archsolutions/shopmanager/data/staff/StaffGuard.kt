package com.archsolutions.shopmanager.data.staff

import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.repo.WrongOwnerPinException
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecoveryOutcome {
    data class Ok(val newCode: String) : RecoveryOutcome
    data object WrongCode : RecoveryOutcome
    data class Locked(val waitMillis: Long) : RecoveryOutcome
}

sealed interface EnableOutcome {
    data class Ok(val code: String) : EnableOutcome
    data class Failed(val result: PinResult) : EnableOutcome
}

/**
 * Sign-in, Owner approval and recovery with wrong-try counters: one per Cashier, and ONE shared "Owner PIN" counter
 * for everything that guesses an Owner's PIN (an Owner's sign-in, [approveAsOwner], re-enabling staff), so alternating
 * between those doors buys no extra tries. 5 wrong tries lock a key; each further lock doubles (30 s, 60 s, 120 s ...,
 * at most 15 min), and a success resets it. Counts live for the process only, like
 * [com.archsolutions.shopmanager.data.settings.PinGuard].
 */
@Singleton
class StaffGuard @Inject constructor(
    private val repo: StaffRepo,
    private val session: StaffSession,
    private val clock: AppClock,
) {
    private class Counter {
        var failures = 0
        var lockedUntil = 0L
        var locks = 0
    }

    private val counters = HashMap<String, Counter>()

    // One attempt at a time per key: lock check, verify and count happen together, so parallel
    // attempts can't each slip past the lock and a right PIN can't win while the lock lands.
    private val mutexes = HashMap<String, Mutex>()

    @Synchronized
    private fun mutexFor(key: String): Mutex = mutexes.getOrPut(key) { Mutex() }

    /** The Owner whose PIN the last successful [approveAsOwner] matched. */
    @Volatile var lastApproverId: Long? = null
        private set

    suspend fun signIn(staffId: Long, pin: String): PinResult {
        // An Owner's PIN is guessed on one shared counter whichever door is used; Cashiers get one counter each.
        val known = repo.staff.first().firstOrNull { it.id == staffId && it.active }
        val key = if (known != null && known.role == Role.OWNER.key) OWNER_KEY else "staff:$staffId"
        return mutexFor(key).withLock {
            lockedFor(key)?.let { return@withLock PinResult.Locked(it) }
            val person = repo.staff.first().firstOrNull { it.id == staffId && it.active }
            val ok = person != null && repo.verify(staffId, pin)
            val result = record(key, ok)
            if (result == PinResult.Ok && person != null) session.signIn(person)
            result
        }
    }

    suspend fun approveAsOwner(pin: String): PinResult {
        lastApproverId = null
        return mutexFor(OWNER_KEY).withLock {
            lastApproverId = null
            lockedFor(OWNER_KEY)?.let { return@withLock PinResult.Locked(it) }
            val owners = repo.staff.first().filter { it.active && it.role == Role.OWNER.key }
            // Check every owner but count a single failure for the attempt.
            val approver = owners.firstOrNull { repo.verify(it.id, pin) }
            val result = record(OWNER_KEY, approver != null)
            if (result == PinResult.Ok) lastApproverId = approver?.id
            result
        }
    }

    /**
     * Turns staff on. With an existing Owner row the right current Owner PIN is needed; wrong tries count on the shared
     * Owner-PIN counter. [beforeEnable] runs with the Owner just before the flag flips (see [StaffRepo.enableStaff]).
     */
    suspend fun enableStaff(ownerName: String, ownerPin: String, beforeEnable: suspend (StaffEntity) -> Unit = {}): EnableOutcome =
        mutexFor(OWNER_KEY).withLock {
            lockedFor(OWNER_KEY)?.let { return@withLock EnableOutcome.Failed(PinResult.Locked(it)) }
            try {
                val code = repo.enableStaff(ownerName, ownerPin, beforeEnable)
                record(OWNER_KEY, true)
                EnableOutcome.Ok(code)
            } catch (_: WrongOwnerPinException) {
                EnableOutcome.Failed(record(OWNER_KEY, false))
            }
        }

    suspend fun recover(ownerId: Long, code: String, newPin: String): RecoveryOutcome {
        val key = "recovery"
        return mutexFor(key).withLock {
            lockedFor(key)?.let { return@withLock RecoveryOutcome.Locked(it) }
            val fresh = repo.recoverOwner(ownerId, code, newPin)
            when (val r = record(key, fresh != null)) {
                PinResult.Ok -> RecoveryOutcome.Ok(fresh!!)
                is PinResult.Locked -> RecoveryOutcome.Locked(r.waitMillis)
                is PinResult.Wrong -> RecoveryOutcome.WrongCode
            }
        }
    }

    @Synchronized
    private fun lockedFor(key: String): Long? =
        counters[key]?.let { (it.lockedUntil - clock.nowMillis()).takeIf { w -> w > 0L } }

    @Synchronized
    private fun record(key: String, success: Boolean): PinResult {
        val c = counters.getOrPut(key) { Counter() }
        return when {
            success -> {
                c.failures = 0
                c.locks = 0
                PinResult.Ok
            }
            ++c.failures >= Pin.MAX_TRIES -> {
                c.failures = 0
                val wait = lockoutMillis(c.locks++)
                c.lockedUntil = clock.nowMillis() + wait
                PinResult.Locked(wait)
            }
            else -> PinResult.Wrong(triesLeft = Pin.MAX_TRIES - c.failures)
        }
    }

    private companion object {
        const val OWNER_KEY = "owner"
        const val MAX_LOCKOUT_MILLIS = 15 * 60_000L

        /** 30 s, 60 s, 120 s ... for the 1st, 2nd, 3rd lock, capped at 15 min. */
        fun lockoutMillis(previousLocks: Int): Long =
            if (previousLocks >= 5) MAX_LOCKOUT_MILLIS else minOf(Pin.LOCKOUT_MILLIS shl previousLocks, MAX_LOCKOUT_MILLIS)
    }
}
