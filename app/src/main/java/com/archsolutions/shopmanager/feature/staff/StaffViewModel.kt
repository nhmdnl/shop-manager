package com.archsolutions.shopmanager.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinGuard
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.EnableOutcome
import com.archsolutions.shopmanager.data.staff.StaffGuard
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.domain.staff.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What was wrong with a name or PIN typed on a staff screen; the screen turns it into a message. */
enum class StaffInputProblem { BLANK_NAME, BAD_PIN, NOT_OWNER }

/** The shared PIN typed during setup does not match the one in Settings. */
class WrongSharedPinException : IllegalStateException("The shared PIN does not match")

/** A PIN is exactly [Pin.LENGTH] ASCII digits; the repo does not check this, so every entry point does. */
fun isValidPin(pin: String): Boolean = pin.length == Pin.LENGTH && pin.all { it in '0'..'9' }

/** The Staff screen needs the SIGNED-IN person to be an Owner and staff to be on; an Owner's approval of one action does not count. Unknown is closed. */
fun canOpenStaffScreen(signedInRole: Role?, staffEnabled: Boolean?): Boolean = staffEnabled == true && signedInRole == Role.OWNER

/**
 * Settings (which holds backup, restore and cloud) is open to anyone while staff is off; with staff on only the
 * signed-in person being an Owner opens it. Unknown state is closed.
 */
fun canOpenSettings(signedInRole: Role?, staffActive: Boolean?): Boolean = when (staffActive) {
    null -> false
    false -> true
    true -> signedInRole == Role.OWNER
}

/** Setup, the staff list and turning staff off. Owner-only: the screens are reached through the Owner PIN gate. */
@HiltViewModel
class StaffViewModel @Inject constructor(
    private val repo: StaffRepo,
    private val session: StaffSession,
    private val settings: SettingsStore,
    private val pinGuard: PinGuard,
    private val guard: StaffGuard,
) : ViewModel() {
    /** Active people first, then by name. */
    val people: StateFlow<List<StaffEntity>> = repo.staff
        .map { all -> all.sortedWith(compareByDescending<StaffEntity> { it.active }.thenBy { it.name.lowercase() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Staff on with an active Owner (the repo's rule); null until known, which screens treat as closed. */
    val staffEnabled: StateFlow<Boolean?> = repo.staffActive.map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The signed-in person's role, or null (nobody, or unknown). */
    val signedInRole: StateFlow<Role?> = session.current.map { p -> p?.takeIf { it.active }?.let { Role.fromKey(it.role) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val idleMinutes: StateFlow<Int> = settings.settings.map { it.idleTimeoutMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10)

    val currentId: StateFlow<Long?> = session.current.map { it?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.currentId)

    /** True only while an active Owner is signed in; never true through an approval. */
    val signedInOwner: StateFlow<Boolean> = session.current
        .map { it != null && it.active && Role.fromKey(it.role) == Role.OWNER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _shownCode = MutableStateFlow<String?>(null)

    /**
     * The recovery code waiting to be written down, or null. Only here until [codeWritten] (or a sign-out, via
     * [discardCode]); it is never saved or logged, and the store keeps only its hash.
     */
    val shownCode: StateFlow<String?> = _shownCode.asStateFlow()

    fun codeWritten() {
        _shownCode.value = null
    }

    /** Drops a code nobody confirmed (sign-out), so the next person never sees it. */
    fun discardCode() {
        _shownCode.value = null
    }

    /**
     * A fresh recovery code for the signed-in Owner, shown once through [shownCode]; null (nothing shown) for anyone
     * else. The old code stops working at once.
     */
    suspend fun newRecoveryCode(): String? {
        val me = session.current.value ?: return null
        if (Role.fromKey(me.role) != Role.OWNER) return null
        val code = repo.regenerateRecoveryCode(me.id) ?: return null
        _shownCode.value = code
        return code
    }

    private fun ownerSignedIn(): Boolean =
        session.current.value?.let { it.active && Role.fromKey(it.role) == Role.OWNER } == true

    /** True when an Owner row already exists, i.e. turning staff on again needs that Owner's current PIN. */
    suspend fun hasExistingOwner(): Boolean = repo.existingOwner() != null

    /** The shared PIN check for the confirm step of setup; same 5-tries-then-30-s rule as the PIN gate. */
    suspend fun checkSharedPin(pin: String): PinResult = pinGuard.check(pin)

    /**
     * First set-up: turns staff on with a new Owner called [ownerName] and a NEW PIN [ownerPin]. When the old shared
     * PIN is set, [sharedPin] must match it (the screen checks it through [checkSharedPin] first) and [ownerPin] must
     * differ from it, since staff may know the shared one. Signs the new Owner in before the flag flips (no flash of
     * the sign-in cover), and returns the recovery code, also held in [shownCode] until confirmed.
     * Throws [WrongSharedPinException], IllegalArgumentException (blank name, PIN not 4 digits, PIN equal to the shared
     * one) or IllegalStateException (already on, or an Owner exists: use [reEnable]).
     */
    suspend fun setUp(ownerName: String, ownerPin: String, sharedPin: String? = null): String {
        val name = ownerName.trim()
        require(name.isNotEmpty()) { "Name must not be blank" }
        require(isValidPin(ownerPin)) { "PIN must be ${Pin.LENGTH} digits" }
        val current = settings.settings.first()
        if (current.pinEnabled && current.pinHash.isNotEmpty()) {
            if (sharedPin == null || !Pin.matches(sharedPin, current.pinHash)) throw WrongSharedPinException()
            require(ownerPin != sharedPin) { "Choose a new Owner PIN, not the old shared one" }
        }
        return when (val outcome = enable(name, ownerPin)) {
            is EnableOutcome.Ok -> outcome.code
            is EnableOutcome.Failed -> throw IllegalStateException("An Owner already exists")
        }
    }

    /**
     * Turning staff on again after "Turn off staff": needs the existing Owner's current PIN, which counts on the shared
     * Owner-PIN counter. The Owner keeps name and PIN; a new recovery code is shown through [shownCode]. Ok, or the
     * wrong/locked result.
     */
    suspend fun reEnable(ownerPin: String): PinResult {
        if (!isValidPin(ownerPin)) return PinResult.Wrong(0)
        return when (val outcome = enable("", ownerPin)) {
            is EnableOutcome.Ok -> PinResult.Ok
            is EnableOutcome.Failed -> outcome.result
        }
    }

    private suspend fun enable(name: String, pin: String): EnableOutcome {
        val before = session.current.value
        // The new Owner is signed in just ahead of the flag flipping; if it then fails, put the session back as it was.
        fun undo() {
            if (session.current.value?.id != before?.id) before?.let(session::signIn) ?: session.signOut()
        }
        val outcome = try {
            guard.enableStaff(name, pin, beforeEnable = session::signIn)
        } catch (e: Exception) {
            undo()
            throw e
        }
        if (outcome is EnableOutcome.Ok) _shownCode.value = outcome.code else undo()
        return outcome
    }

    suspend fun add(name: String, role: Role, pin: String): StaffInputProblem? {
        if (!ownerSignedIn()) return StaffInputProblem.NOT_OWNER
        val clean = name.trim()
        if (clean.isEmpty()) return StaffInputProblem.BLANK_NAME
        if (!isValidPin(pin)) return StaffInputProblem.BAD_PIN
        repo.addStaff(clean, role, pin)
        return null
    }

    suspend fun rename(id: Long, name: String): StaffInputProblem? {
        if (!ownerSignedIn()) return StaffInputProblem.NOT_OWNER
        val clean = name.trim()
        if (clean.isEmpty()) return StaffInputProblem.BLANK_NAME
        repo.rename(id, clean)
        return null
    }

    suspend fun resetPin(id: Long, pin: String): StaffInputProblem? {
        if (!ownerSignedIn()) return StaffInputProblem.NOT_OWNER
        if (!isValidPin(pin)) return StaffInputProblem.BAD_PIN
        // Resetting another Owner's PIN would be taking that Owner's seat; an Owner can only reset their own or a Cashier's.
        val target = repo.staff.first().firstOrNull { it.id == id } ?: return null
        if (Role.fromKey(target.role) == Role.OWNER && target.id != session.currentId) return StaffInputProblem.NOT_OWNER
        repo.resetPin(id, pin)
        return null
    }

    /** Switches the person off or on again. False when refused (the last active Owner); a person switched off is signed out at once. */
    suspend fun toggleActive(id: Long): Boolean {
        if (!ownerSignedIn()) return false
        val person = repo.staff.first().firstOrNull { it.id == id } ?: return false
        val nowActive = !person.active
        if (!repo.setActive(id, nowActive)) return false
        if (!nowActive) session.onDeactivated(id)
        return true
    }

    /** Staff off: the rows stay for a later set-up; nobody stays signed in. */
    fun turnOff(): Job = viewModelScope.launch {
        repo.disableStaff()
        session.signOut()
    }

    /** 1 to 60 minutes; anything else is ignored. */
    fun setIdleMinutes(minutes: Int): Job? {
        if (minutes !in MIN_IDLE_MINUTES..MAX_IDLE_MINUTES) return null
        return viewModelScope.launch { settings.setIdleTimeoutMinutes(minutes) }
    }

    companion object {
        const val MIN_IDLE_MINUTES = 1
        const val MAX_IDLE_MINUTES = 60
    }
}
