package com.archsolutions.shopmanager.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.settings.ShopSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// AppCompatActivity, not ComponentActivity: below Android 13 only AppCompat activities pick up
// AppCompatDelegate.setApplicationLocales, so the language setting did nothing on those devices.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var closeReminder: CloseReminder

    /** Set when the close-day reminder was tapped; AppNavHost clears it once handled. */
    private var openCloseDay by mutableStateOf(false)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            openCloseDay = intent?.action == CloseReminder.ACTION_CLOSE_DAY
            // New installs are asked on onboarding's last step; installs that were set up before that
            // step existed are asked here, until they answer (the reminder needs it on API 33+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                lifecycleScope.launch {
                    if (settingsStore.settings.first().onboardingDone) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        // Plans the reminder on every launch and again whenever its time changes in Settings.
        lifecycleScope.launch {
            settingsStore.settings.map { it.closeReminderTime }.distinctUntilChanged().collect { closeReminder.schedule(it) }
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val settings by settingsStore.settings.collectAsState(initial = ShopSettings())
            val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            val useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
            val isTigrinya = settings.language == "ti"

            ShopManagerTheme(
                themeMode = settings.theme,
                isTigrinya = isTigrinya,
            ) {
                AppNavHost(
                    isExpanded = isExpanded,
                    useRail = useRail,
                    openCloseDay = openCloseDay,
                    onCloseDayOpened = { openCloseDay = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == CloseReminder.ACTION_CLOSE_DAY) openCloseDay = true
    }
}
