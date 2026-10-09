package com.archsolutions.shopmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.archsolutions.shopmanager.core.designsystem.ShopManagerTheme
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.settings.ShopSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var seedDataLoader: com.archsolutions.shopmanager.data.db.SeedDataLoader

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            seedDataLoader.loadSeedDataIfEmpty()
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val settings by settingsStore.settings.collectAsState(initial = ShopSettings())
            val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
            val isTigrinya = settings.language == "ti"

            ShopManagerTheme(
                themeMode = settings.theme,
                isTigrinya = isTigrinya,
            ) {
                AppNavHost(
                    isExpanded = isExpanded,
                )
            }
        }
    }
}
