package com.archsolutions.shopmanager.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

@Composable
fun ShopManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    isTigrinya: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colors = if (darkTheme) DarkShopColors else LightShopColors
    val materialColors = colors.toMaterial(darkTheme)
    val typography = getShopTypography(isTigrinya)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.paper.toArgb()
                window.navigationBarColor = colors.paper.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalShopColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = typography,
            shapes = ShopShapes,
            content = content,
        )
    }
}
