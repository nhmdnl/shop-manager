package com.archsolutions.shopmanager.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.RecoveryOutcome
import com.archsolutions.shopmanager.data.staff.StaffGuard
import com.archsolutions.shopmanager.data.staff.StaffSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The sign-in screen: who is working (active staff only), then their PIN. */
@HiltViewModel
class StaffSignInViewModel @Inject constructor(
    staffRepo: StaffRepo,
    private val staffGuard: StaffGuard,
    session: StaffSession,
    private val settings: SettingsStore,
) : ViewModel() {
    val people: StateFlow<List<StaffEntity>> = staffRepo.staff
        .map { all -> all.filter { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The person tapped on the list, if any. */
    private val tapped = MutableStateFlow<Long?>(null)

    /**
     * The person whose PIN is being asked for, or null when nobody is: the tapped person if still active, else the
     * last person to sign in on this device if still active. Eager so [enter] can read it at any time.
     */
    val picked: StateFlow<Long?> = combine(
        tapped,
        people,
        settings.settings.map { it.lastStaffId },
    ) { tappedId, active, lastId ->
        val activeIds = active.map { it.id }
        tappedId?.takeIf { it in activeIds } ?: lastId?.takeIf { it in activeIds }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun pick(id: Long) {
        tapped.value = id
    }

    /** Checks the PIN; on Ok the session holds the person, they are remembered and the screen is left. */
    suspend fun enter(pin: String): PinResult {
        val id = picked.value ?: return PinResult.Wrong(0)
        val result = staffGuard.signIn(id, pin)
        if (result == PinResult.Ok) {
            // Launched on the view model's scope, not awaited here: the screen's own scope is cancelled as soon as the
            // session changes, which would cancel this write and the next sign-in would not remember this person.
            // Best-effort: a failed DataStore write only loses the remembered person, it must not crash the app.
            viewModelScope.launch { runCatching { settings.setLastStaffId(id) } }
            tapped.value = null
        }
        return result
    }

    private val _shownCode = MutableStateFlow<String?>(null)

    /** The new recovery code after a recovery, until the person confirms they wrote it down; never saved or logged. */
    val shownCode: StateFlow<String?> = _shownCode.asStateFlow()

    fun codeWritten() {
        _shownCode.value = null
    }

    /** Sets a new PIN for the Owner when [code] is right (own 5-tries lock); the fresh code lands in [shownCode]. */
    suspend fun recover(ownerId: Long, code: String, newPin: String): RecoveryOutcome {
        require(isValidPin(newPin)) { "PIN must be 4 digits" }
        val outcome = staffGuard.recover(ownerId, code, newPin)
        if (outcome is RecoveryOutcome.Ok) _shownCode.value = outcome.newCode
        return outcome
    }

    init {
        // Whenever the signed-in person changes (idle or Switch-user sign-out, a new sign-in) a half-done pick and a
        // recovery code nobody confirmed are nobody's to see any more.
        viewModelScope.launch {
            session.current.drop(1).collect {
                tapped.value = null
                _shownCode.value = null
            }
        }
    }
}
