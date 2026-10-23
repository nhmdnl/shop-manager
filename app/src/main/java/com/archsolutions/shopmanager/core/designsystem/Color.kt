package com.archsolutions.shopmanager.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ShopColors(
    val paper: Color, val surface: Color, val ink: Color, val muted: Color, val line: Color,
    /** Outline of inputs and toggles: at least 3:1 on paper and surface, unlike the hairline [line]. */
    val fieldBorder: Color,
    val green: Color, val onGreen: Color, val greenTint: Color,
    val rust: Color, val rustTint: Color, val segmentTrack: Color,
    val chartSoft: Color, val mobileMoney: Color,
)

val LightShopColors = ShopColors(
    paper = Color(0xFFF5F3EE), surface = Color(0xFFFFFFFF), ink = Color(0xFF1B1A17),
    muted = Color(0xFF5E5A52), line = Color(0xFFE4E0D6), fieldBorder = Color(0xFF8F8A7E), green = Color(0xFF1E6B47),
    onGreen = Color(0xFFFFFFFF), greenTint = Color(0xFFE3EFE8), rust = Color(0xFF9A3412),
    rustTint = Color(0xFFFBE9DF), segmentTrack = Color(0xFFEAE6DC),
    chartSoft = Color(0xFF6E9E84), mobileMoney = Color(0xFFD98B3A),
)

val DarkShopColors = ShopColors(
    paper = Color(0xFF141412), surface = Color(0xFF1E1D1A), ink = Color(0xFFF2EFE8),
    muted = Color(0xFFA8A395), line = Color(0xFF34322D), fieldBorder = Color(0xFF6E6A60), green = Color(0xFF5BC08F),
    onGreen = Color(0xFF0E1F16), greenTint = Color(0xFF1D3A2B), rust = Color(0xFFF0A070),
    rustTint = Color(0xFF3A2418), segmentTrack = Color(0xFF2A2925),
    chartSoft = Color(0xFF4A8064), mobileMoney = Color(0xFFE0A060),
)

val LocalShopColors = staticCompositionLocalOf { LightShopColors }

/**
 * Every Material 3 role mapped to the shop palette. Unmapped roles fall back to Material's lavender
 * defaults, which showed up in the date picker, dialogs and menus.
 */
fun ShopColors.toMaterial(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = green, onPrimary = onGreen, primaryContainer = greenTint, onPrimaryContainer = green,
        secondary = green, onSecondary = onGreen, secondaryContainer = greenTint, onSecondaryContainer = green,
        tertiary = rust, onTertiary = surface, tertiaryContainer = rustTint, onTertiaryContainer = rust,
        error = rust, onError = surface, errorContainer = rustTint, onErrorContainer = rust,
        background = paper, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = segmentTrack, onSurfaceVariant = muted, outline = line, outlineVariant = line,
        surfaceTint = surface, // no green wash on raised surfaces
        surfaceBright = surface, surfaceDim = paper,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = segmentTrack,
        inverseSurface = ink, inverseOnSurface = surface, inversePrimary = greenTint,
    )
}
