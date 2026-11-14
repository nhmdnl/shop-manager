package com.archsolutions.shopmanager.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.settings.ShopSettings
import com.archsolutions.shopmanager.data.staff.StaffSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app shell needs to cover the app with sign-in: the setting, who is signed in, idle checks, Switch user. */
@HiltViewModel
class StaffGateViewModel @Inject constructor(
    private val session: StaffSession,
    staffRepo: StaffRepo,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    /** Null until the first read: the gate treats unknown as covered, never as staff off. */
    val settings: StateFlow<ShopSettings?> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Staff on with an active Owner (the repo's rule); null until known, which the gate treats as covered, never as off. */
    val staffActive: StateFlow<Boolean?> = staffRepo.staffActive.map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val current: StateFlow<StaffEntity?> = session.current

    private val _signOutTick = MutableStateFlow(0)

    /** Goes up each time a signed-in person is signed out, so open dialogs can close. */
    val signOutTick: StateFlow<Int> = _signOutTick.asStateFlow()

    init {
        viewModelScope.launch {
            var wasSignedIn = false
            session.current.collect { person ->
                if (wasSignedIn && person == null) _signOutTick.value++
                wasSignedIn = person != null
            }
        }
        // A person switched off (or removed) while signed in is signed out at once.
        viewModelScope.launch {
            staffRepo.staff.collect { all ->
                val id = session.currentId ?: return@collect
                if (all.none { it.id == id && it.active }) session.onDeactivated(id)
            }
        }
    }

    fun checkIdle() = settings.value?.let { session.checkIdle(it.idleTimeoutMinutes) }

    /**
     * Switch user forgets the remembered person, so the sign-in screen that comes next does not offer the one who just
     * left. The forget is written first, so the old pick never shows; a failed write must not keep anyone signed in.
     * An idle sign-out does not come through here and keeps the person.
     */
    fun switchUser() {
        viewModelScope.launch {
            runCatching { settingsStore.setLastStaffId(null) }
            session.signOut()
        }
    }

    /** Keeps the idle clock fresh for activity that never reaches the Activity (dialog keypads, soft-keyboard typing). */
    fun touch() = session.touch()
}
