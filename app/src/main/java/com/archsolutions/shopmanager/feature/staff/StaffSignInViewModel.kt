package com.archsolutions.shopmanager.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.staff.RecoveryOutcome
import com.archsolutions.shopmanager.data.staff.StaffGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The sign-in screen: who is working (active staff only), then their PIN. */
@HiltViewModel
class StaffSignInViewModel @Inject constructor(
    staffRepo: StaffRepo,
    private val staffGuard: StaffGuard,
) : ViewModel() {
    val people: StateFlow<List<StaffEntity>> = staffRepo.staff
        .map { all -> all.filter { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _picked = MutableStateFlow<Long?>(null)

    /** The person whose PIN is being asked for, or null while the list shows. */
    val picked: StateFlow<Long?> = _picked.asStateFlow()

    fun pick(id: Long) {
        _picked.value = id
    }

    fun back() {
        _picked.value = null
    }

    /** Checks the PIN; on Ok the session holds the person and the screen is left. */
    suspend fun enter(pin: String): PinResult {
        val id = _picked.value ?: return PinResult.Wrong(0)
        val result = staffGuard.signIn(id, pin)
        if (result == PinResult.Ok) _picked.value = null
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
}
