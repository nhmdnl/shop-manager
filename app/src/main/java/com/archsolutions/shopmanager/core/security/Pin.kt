package com.archsolutions.shopmanager.core.security

import java.security.MessageDigest
import java.security.SecureRandom

/** The optional 4-digit PIN (addendum C4), stored as "salt:SHA-256(salt + PIN)" in settings. */
object Pin {
    const val LENGTH = 4
    const val MAX_TRIES = 5
    const val LOCKOUT_MILLIS = 30_000L

    fun hash(pin: String, salt: String = newSalt()): String = "$salt:${sha256(salt + pin)}"

    fun matches(pin: String, stored: String): Boolean {
        val salt = stored.substringBefore(':', missingDelimiterValue = "")
        if (salt.isEmpty()) return false
        // Constant-time, so a wrong guess takes as long as a nearly right one.
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
