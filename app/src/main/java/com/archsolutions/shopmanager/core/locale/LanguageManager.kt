package com.archsolutions.shopmanager.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageManager {
    val supportedLanguages = listOf(
        "en" to "English",
        "lg" to "Luganda",
        "sw" to "Kiswahili",
        "ti" to "ትግርኛ (Tigrinya)",
        "pt" to "Português",
    )

    fun setAppLocale(langCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getCurrentLocale(): String {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        return if (!currentLocales.isEmpty) {
            currentLocales[0]?.language ?: "en"
        } else {
            "en"
        }
    }
}
