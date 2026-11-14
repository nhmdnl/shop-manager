package com.archsolutions.shopmanager.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shop_settings")

data class ShopSettings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val language: String = "en",
    val shopName: String = "My Shop",
    val currencyCode: String = "UGX",
    val openDayOnStart: Boolean = true,
    val closeReminderTime: String = "21:00",
    val allowedDifference: Long = 500L,
    val carryClosingToOpening: Boolean = true,
    val pinEnabled: Boolean = false,
    val pinHash: String = "",
    val lastBackupAt: Long? = null,
    /** When the automatic copy after closing a day last failed; null once one succeeds. */
    val autoBackupFailedAt: Long? = null,
    val onboardingDone: Boolean = false,
    val staffEnabled: Boolean = false,
    val recoveryHash: String = "",
    val idleTimeoutMinutes: Int = 10,
    /** The person who last signed in on this device; null until someone has. */
    val lastStaffId: Long? = null,
)

@Singleton
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.dataStore)

    companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_SHOP_NAME = stringPreferencesKey("shop_name")
        val KEY_CURRENCY = stringPreferencesKey("currency")
        val KEY_OPEN_DAY_ON_START = booleanPreferencesKey("open_day_on_start")
        val KEY_CLOSE_REMINDER_TIME = stringPreferencesKey("close_reminder_time")
        val KEY_ALLOWED_DIFFERENCE = longPreferencesKey("allowed_difference")
        val KEY_CARRY_CLOSING = booleanPreferencesKey("carry_closing")
        val KEY_PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_LAST_BACKUP = longPreferencesKey("last_backup")
        val KEY_AUTO_BACKUP_FAILED = longPreferencesKey("auto_backup_failed")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_STAFF_ENABLED = booleanPreferencesKey("staff_enabled")
        val KEY_RECOVERY_HASH = stringPreferencesKey("recovery_hash")
        val KEY_IDLE_TIMEOUT_MINUTES = intPreferencesKey("idle_timeout_minutes")
        val KEY_LAST_STAFF_ID = longPreferencesKey("last_staff_id")
    }

    val settings: Flow<ShopSettings> = dataStore.data.map { prefs ->
        ShopSettings(
            theme = prefs[KEY_THEME]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.LIGHT,
            language = prefs[KEY_LANGUAGE] ?: "en",
            shopName = prefs[KEY_SHOP_NAME] ?: "My Shop",
            currencyCode = prefs[KEY_CURRENCY] ?: "UGX",
            openDayOnStart = prefs[KEY_OPEN_DAY_ON_START] ?: true,
            closeReminderTime = prefs[KEY_CLOSE_REMINDER_TIME] ?: "21:00",
            allowedDifference = prefs[KEY_ALLOWED_DIFFERENCE] ?: 500L,
            carryClosingToOpening = prefs[KEY_CARRY_CLOSING] ?: true,
            pinEnabled = prefs[KEY_PIN_ENABLED] ?: false,
            pinHash = prefs[KEY_PIN_HASH] ?: "",
            lastBackupAt = prefs[KEY_LAST_BACKUP],
            autoBackupFailedAt = prefs[KEY_AUTO_BACKUP_FAILED],
            onboardingDone = prefs[KEY_ONBOARDING_DONE] ?: false,
            staffEnabled = prefs[KEY_STAFF_ENABLED] ?: false,
            recoveryHash = prefs[KEY_RECOVERY_HASH] ?: "",
            idleTimeoutMinutes = prefs[KEY_IDLE_TIMEOUT_MINUTES] ?: 10,
            lastStaffId = prefs[KEY_LAST_STAFF_ID],
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setLanguage(language: String) {
        dataStore.edit { it[KEY_LANGUAGE] = language }
    }

    suspend fun setShopName(name: String) {
        dataStore.edit { it[KEY_SHOP_NAME] = name }
    }

    suspend fun setOpenDayOnStart(open: Boolean) {
        dataStore.edit { it[KEY_OPEN_DAY_ON_START] = open }
    }

    suspend fun setCloseReminderTime(time: String) {
        dataStore.edit { it[KEY_CLOSE_REMINDER_TIME] = time }
    }

    suspend fun setAllowedDifference(diff: Long) {
        dataStore.edit { it[KEY_ALLOWED_DIFFERENCE] = diff }
    }

    suspend fun setCarryClosingToOpening(carry: Boolean) {
        dataStore.edit { it[KEY_CARRY_CLOSING] = carry }
    }

    suspend fun setPin(enabled: Boolean, hash: String) {
        dataStore.edit {
            it[KEY_PIN_ENABLED] = enabled
            it[KEY_PIN_HASH] = hash
        }
    }

    /** Null clears it (no backup yet). */
    suspend fun setLastBackupAt(millis: Long?) {
        dataStore.edit { if (millis == null) it.remove(KEY_LAST_BACKUP) else it[KEY_LAST_BACKUP] = millis }
    }

    suspend fun setAutoBackupFailedAt(millis: Long?) {
        dataStore.edit { if (millis == null) it.remove(KEY_AUTO_BACKUP_FAILED) else it[KEY_AUTO_BACKUP_FAILED] = millis }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun setStaffEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_STAFF_ENABLED] = enabled }
    }

    suspend fun setRecoveryHash(hash: String) {
        dataStore.edit { it[KEY_RECOVERY_HASH] = hash }
    }

    suspend fun setIdleTimeoutMinutes(minutes: Int) {
        dataStore.edit { it[KEY_IDLE_TIMEOUT_MINUTES] = minutes }
    }

    /** Null clears it (nobody has signed in on this device yet). */
    suspend fun setLastStaffId(id: Long?) {
        dataStore.edit { if (id == null) it.remove(KEY_LAST_STAFF_ID) else it[KEY_LAST_STAFF_ID] = id }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)
}
