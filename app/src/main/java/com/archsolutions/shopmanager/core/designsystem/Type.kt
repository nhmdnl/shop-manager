package com.archsolutions.shopmanager.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R

// Bundled static fonts (Google Fonts, OFL): downloadable fonts need Play services and a network,
// so an offline shop tablet fell back to Roboto. Static files, not variable ones: Android 8/9 space
// variable fonts wrongly ("Mob ile").
val DisplayFontFamily = FontFamily(
    Font(R.font.bricolage_grotesque_600, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_700, FontWeight.Bold),
)

val BodyFontFamily = FontFamily(
    Font(R.font.dm_sans_400, FontWeight.Normal),
    Font(R.font.dm_sans_500, FontWeight.Medium),
    Font(R.font.dm_sans_600, FontWeight.SemiBold),
    Font(R.font.dm_sans_700, FontWeight.Bold),
)

val EthiopicFontFamily = FontFamily(
    Font(R.font.noto_sans_ethiopic_400, FontWeight.Normal),
    Font(R.font.noto_sans_ethiopic_600, FontWeight.SemiBold),
    Font(R.font.noto_sans_ethiopic_700, FontWeight.Bold),
)

fun getShopTypography(isTigrinya: Boolean = false): Typography {
    val display = if (isTigrinya) EthiopicFontFamily else DisplayFontFamily
    val body = if (isTigrinya) EthiopicFontFamily else BodyFontFamily

    return Typography(
        displayLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            lineHeight = 52.sp,
            fontFeatureSettings = "tnum",
        ),
        displayMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontFeatureSettings = "tnum",
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontFeatureSettings = "tnum",
        ),
        titleLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontFeatureSettings = "tnum",
        ),
        titleMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.04.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
    )
}

// Amount text style: always Bricolage Grotesque with tabular figures, even in Tigrinya
fun amountTextStyle(fontSize: androidx.compose.ui.unit.TextUnit = 24.sp, fontWeight: FontWeight = FontWeight.Bold): TextStyle {
    return TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = fontWeight,
        fontSize = fontSize,
        fontFeatureSettings = "tnum",
    )
}

/** Big digits grow at most this much, as with Android 14's non-linear font scaling, so amounts fit their boxes at 200%. */
const val MAX_AMOUNT_FONT_SCALE = 1.5f

/** [base] grown with the user's font scale (never shrunk, at most [maxScale]), for fixed-size boxes that hold text, so 200% fonts are not cut. */
@Composable
fun fontScaled(base: Dp, maxScale: Float = Float.MAX_VALUE): Dp = base * LocalDensity.current.fontScale.coerceIn(1f, maxScale)

/** Runs [content] with the font scale capped at [maxScale]; below the cap the platform's own scaling is kept. */
@Composable
fun CappedFontScale(maxScale: Float = MAX_AMOUNT_FONT_SCALE, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= maxScale) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, maxScale), content = content)
    }
}
