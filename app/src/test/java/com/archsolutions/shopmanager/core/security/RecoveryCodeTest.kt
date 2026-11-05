package com.archsolutions.shopmanager.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    @Test
    fun `generate gives 8 chars from the alphabet, none ambiguous (0 O 1 I)`() {
        repeat(200) {
            val code = RecoveryCode.generate()
            assertEquals(9, code.length)
            assertEquals('-', code[4])
            val chars = code.replace("-", "")
            assertEquals(8, chars.length)
            assertTrue(chars.all { it in alphabet })
            assertTrue(chars.none { it in "0O1I" })
        }
    }

    @Test
    fun `normalize accepts lower case and dashes`() {
        assertEquals("ABCD2345", RecoveryCode.normalize("abcd-2345"))
        assertEquals("ABCD2345", RecoveryCode.normalize(" ab cd - 23 45 "))
    }
}
