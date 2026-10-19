package com.archsolutions.shopmanager.feature.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.locale.LanguageManager
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.repo.ImportResult
import com.archsolutions.shopmanager.data.repo.ProductImporter
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.settings.ShopSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 1,
    val shopName: String = "",
    val language: String = "en",
    val theme: ThemeMode = ThemeMode.LIGHT,
    val openingCash: Long? = null,
    val openingMobile: Long? = null,
    val importing: Boolean = false,
    val imported: ImportResult? = null,
    val importFailed: Boolean = false,
    val starting: Boolean = false,
    val done: Boolean = false,
) {
    val canContinue: Boolean get() = step != 1 || shopName.isNotBlank()
}

/** First run (7.8): shop name and language, theme, then the opening money, which opens the first day. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val dayRepo: DayRepo,
    private val importer: ProductImporter,
    private val clock: AppClock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            // The default "My Shop" is a placeholder, not an answer.
            val name = settings.shopName.takeIf { it != ShopSettings().shopName }.orEmpty()
            _state.update { it.copy(shopName = name, language = settings.language, theme = settings.theme) }
        }
    }

    fun setShopName(name: String) = _state.update { it.copy(shopName = name.take(40)) }

    /** Applied at once, so the next steps already read in the chosen language. */
    fun setLanguage(code: String) {
        _state.update { it.copy(language = code) }
        viewModelScope.launch {
            settingsStore.setLanguage(code)
            LanguageManager.setAppLocale(code)
        }
    }

    fun setTheme(theme: ThemeMode) {
        _state.update { it.copy(theme = theme) }
        viewModelScope.launch { settingsStore.setTheme(theme) }
    }

    fun setOpeningCash(amount: Long?) = _state.update { it.copy(openingCash = amount) }

    fun setOpeningMobile(amount: Long?) = _state.update { it.copy(openingMobile = amount) }

    fun next() {
        val current = _state.value
        if (!current.canContinue) return
        if (current.step == 1) viewModelScope.launch { settingsStore.setShopName(current.shopName.trim()) }
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    }

    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }

    fun importProducts(uri: Uri) {
        _state.update { it.copy(importing = true, importFailed = false) }
        viewModelScope.launch {
            val result = importer.importFrom(uri)
            _state.update { it.copy(importing = false, imported = result ?: it.imported, importFailed = result == null) }
        }
    }

    fun startDay() {
        if (_state.value.starting) return
        _state.update { it.copy(starting = true) }
        viewModelScope.launch {
            val input = _state.value
            settingsStore.setShopName(input.shopName.trim().ifEmpty { ShopSettings().shopName })
            val today = clock.todayDateString()
            // A day can already exist if this screen came back after a restore; never open twice.
            if (dayRepo.getDayByDateDirect(today) == null) {
                dayRepo.openDay(today, openingCash = input.openingCash ?: 0L, openingMobile = input.openingMobile ?: 0L)
            }
            settingsStore.setOnboardingDone(true)
            _state.update { it.copy(starting = false, done = true) }
        }
    }

    companion object {
        const val LAST_STEP = 3
    }
}
