package com.archsolutions.shopmanager.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinGuard
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
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
enum class StaffInputProblem { BLANK_NAME, BAD_PIN }

/** The shared PIN typed during setup does not match the one in Settings. */
class WrongSharedPinException : IllegalStateException("The shared PIN does not match")

/** A PIN is exactly [Pin.LENGTH] ASCII digits; the repo does not check this, so every entry point does. */
fun isValidPin(pin: String): Boolean = pin.length == Pin.LENGTH && pin.all { it in '0'..'9' }

/** The Staff screen needs the SIGNED-IN person to be an Owner and staff to be on; an Owner's approval of one action does not count. Unknown is closed. */
fun canOpenStaffScreen(signedInRole: Role?, staffEnabled: Boolean?): Boolean = staffEnabled == true && signedInRole == Role.OWNER

/** Setup, the staff list and turning staff off. Owner-only: the screens are reached through the Owner PIN gate. */
@HiltViewModel
class StaffViewModel @Inject constructor(
    private val repo: StaffRepo,
    private val session: StaffSession,
    private val settings: SettingsStore,
    private val pinGuard: PinGuard,
) : ViewModel() {
    /** Active people first, then by name. */
    val people: StateFlow<List<StaffEntity>> = repo.staff
        .map { all -> all.sortedWith(compareByDescending<StaffEntity> { it.active }.thenBy { it.name.lowercase() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the setting is read; screens treat unknown as off. */
    val staffEnabled: StateFlow<Boolean?> = settings.settings.map<_, Boolean?> { it.staffEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** The shared PIN check for the confirm step of setup; same 5-tries-then-30-s rule as the PIN gate. */
    suspend fun checkSharedPin(pin: String): PinResult = pinGuard.check(pin)

    /**
     * Turns staff on with [ownerName] and [ownerPin] (the current shared PIN when there is one, which is verified
     * here first), signs the new Owner in, and returns the recovery code, also held in [shownCode] until confirmed.
     * Throws [WrongSharedPinException], IllegalArgumentException (blank name, PIN not 4 digits) or IllegalStateException
     * (already on); the screens check input first.
     */
    suspend fun setUp(ownerName: String, ownerPin: String): String {
        val name = ownerName.trim()
        require(name.isNotEmpty()) { "Name must not be blank" }
        require(isValidPin(ownerPin)) { "PIN must be ${Pin.LENGTH} digits" }
        val current = settings.settings.first()
        if (current.pinEnabled && current.pinHash.isNotEmpty() && !Pin.matches(ownerPin, current.pinHash)) {
            throw WrongSharedPinException()
        }
        val code = repo.enableStaff(name, ownerPin)
        repo.staff.first().filter { it.role == Role.OWNER.key }.minByOrNull { it.id }?.let(session::signIn)
        _shownCode.value = code
        return code
    }

    suspend fun add(name: String, role: Role, pin: String): StaffInputProblem? {
        val clean = name.trim()
        if (clean.isEmpty()) return StaffInputProblem.BLANK_NAME
        if (!isValidPin(pin)) return StaffInputProblem.BAD_PIN
        repo.addStaff(clean, role, pin)
        return null
    }

    suspend fun rename(id: Long, name: String): StaffInputProblem? {
        val clean = name.trim()
        if (clean.isEmpty()) return StaffInputProblem.BLANK_NAME
        repo.rename(id, clean)
        return null
    }

    suspend fun resetPin(id: Long, pin: String): StaffInputProblem? {
        if (!isValidPin(pin)) return StaffInputProblem.BAD_PIN
        repo.resetPin(id, pin)
        return null
    }

    /** Switches the person off or on again. False when refused (the last active Owner); a person switched off is signed out at once. */
    suspend fun toggleActive(id: Long): Boolean {
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
