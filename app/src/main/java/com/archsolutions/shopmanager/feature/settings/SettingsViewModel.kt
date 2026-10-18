package com.archsolutions.shopmanager.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.locale.LanguageManager
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.settings.ShopSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<ShopSettings> = settingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShopSettings(),
        )

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch {
            settingsStore.setTheme(theme)
        }
    }

    fun setLanguage(langCode: String) {
        viewModelScope.launch {
            settingsStore.setLanguage(langCode)
            LanguageManager.setAppLocale(langCode)
        }
    }

    fun setShopName(name: String) {
        viewModelScope.launch {
            settingsStore.setShopName(name)
        }
    }

    fun setOpenDayOnStart(open: Boolean) {
        viewModelScope.launch {
            settingsStore.setOpenDayOnStart(open)
        }
    }

    fun setCloseReminderTime(time: String) {
        viewModelScope.launch {
            settingsStore.setCloseReminderTime(time)
        }
    }

    fun setCarryClosingToOpening(carry: Boolean) {
        viewModelScope.launch {
            settingsStore.setCarryClosingToOpening(carry)
        }
    }

    fun setAllowedDifference(diff: Long) {
        viewModelScope.launch {
            settingsStore.setAllowedDifference(diff)
        }
    }
}
