package com.archsolutions.shopmanager.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a WhatsApp chat with a ready message, or the share sheet when WhatsApp is missing (spec 5.6). */
object WhatsApp {
    private val packages = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Digits for wa.me in E.164 without "+": "0772 123 456" → "256772123456" (Uganda). */
    fun toWaNumber(phone: String): String? {
        val digits = phone.filter(Char::isDigit)
        return when {
            digits.isEmpty() -> null
            digits.startsWith("256") -> digits
            digits.startsWith("0") -> "256" + digits.drop(1)
            digits.length == 9 -> "256$digits"
            else -> digits
        }
    }

    /**
     * Opens [phone]'s chat, or WhatsApp's own "send to" list when there is no number. Returns false when
     * WhatsApp is missing and the share sheet opened instead, so callers don't claim it was sent.
     */
    fun send(context: Context, phone: String?, text: String): Boolean {
        val number = phone?.let(::toWaNumber)
        for (pkg in packages) {
            val intent = if (number != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=${Uri.encode(text)}"))
            } else {
                textIntent(text)
            }
            try {
                context.startActivity(intent.setPackage(pkg))
                return true
            } catch (e: ActivityNotFoundException) {
                // Try the next WhatsApp flavour, then fall back to sharing.
            }
        }
        share(context, text)
        return false
    }

    fun share(context: Context, text: String) {
        context.startActivity(Intent.createChooser(textIntent(text), null))
    }

    private fun textIntent(text: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
}
