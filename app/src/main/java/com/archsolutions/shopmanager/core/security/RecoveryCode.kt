package com.archsolutions.shopmanager.core.security

import java.security.SecureRandom

/** Owner recovery code: 8 characters without look-alikes, shown as XXXX-XXXX. */
object RecoveryCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 8

    fun generate(random: SecureRandom = SecureRandom()): String {
        val chars = CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
        return String(chars, 0, 4) + "-" + String(chars, 4, 4)
    }

    fun normalize(input: String): String =
        input.filterNot { it == '-' || it.isWhitespace() }.uppercase()
}
