package com.archsolutions.shopmanager.data.staff

import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Who is signed in right now. In memory only: a restart signs everyone out. */
@Singleton
class StaffSession @Inject constructor(private val clock: AppClock) {
    private val _current = MutableStateFlow<StaffEntity?>(null)
    val current: StateFlow<StaffEntity?> = _current.asStateFlow()
    val currentId: Long? get() = _current.value?.id

    @Volatile private var lastTouch = 0L

    /** An Owner who approved the action in hand; read by that action through [actingId], then cleared by the caller. */
    @Volatile var approvedBy: Long? = null

    /** Who gets the credit for a write: the approving Owner if there is one, else the signed-in person. */
    val actingId: Long? get() = approvedBy ?: currentId

    fun signIn(staff: StaffEntity) {
        lastTouch = clock.nowMillis()
        _current.value = staff
    }

    fun signOut() {
        approvedBy = null
        _current.value = null
    }

    fun touch() {
        lastTouch = clock.nowMillis()
    }

    fun checkIdle(timeoutMinutes: Int) {
        if (_current.value != null && clock.nowMillis() - lastTouch >= timeoutMinutes * 60_000L) signOut()
    }

    fun onDeactivated(staffId: Long) {
        if (currentId == staffId) signOut()
    }
}
