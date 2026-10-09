package com.archsolutions.shopmanager.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R

val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val BricolageGrotesqueFont = GoogleFont("Bricolage Grotesque")
val DmSansFont = GoogleFont("DM Sans")
val NotoSansEthiopicFont = GoogleFont("Noto Sans Ethiopic")

val DisplayFontFamily = FontFamily(
    Font(googleFont = BricolageGrotesqueFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = BricolageGrotesqueFont, fontProvider = fontProvider, weight = FontWeight.Bold),
)

val BodyFontFamily = FontFamily(
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = DmSansFont, fontProvider = fontProvider, weight = FontWeight.Bold),
)

val EthiopicFontFamily = FontFamily(
    Font(googleFont = NotoSansEthiopicFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = NotoSansEthiopicFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = NotoSansEthiopicFont, fontProvider = fontProvider, weight = FontWeight.Bold),
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
