package com.archsolutions.shopmanager.feature.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.settings.ShopSettings
import com.archsolutions.shopmanager.data.staff.StaffSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app shell needs to cover the app with sign-in: the setting, who is signed in, idle checks, Switch user. */
@HiltViewModel
class StaffGateViewModel @Inject constructor(
    private val session: StaffSession,
    staffRepo: StaffRepo,
    settingsStore: SettingsStore,
) : ViewModel() {
    val settings: StateFlow<ShopSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShopSettings())
    val current: StateFlow<StaffEntity?> = session.current

    init {
        // A person switched off (or removed) while signed in is signed out at once.
        viewModelScope.launch {
            staffRepo.staff.collect { all ->
                val id = session.currentId ?: return@collect
                if (all.none { it.id == id && it.active }) session.onDeactivated(id)
            }
        }
    }

    fun checkIdle() = session.checkIdle(settings.value.idleTimeoutMinutes)

    fun switchUser() = session.signOut()
}
