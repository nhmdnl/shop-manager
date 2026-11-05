package com.archsolutions.shopmanager.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    private val fast = 1_000

    @Test
    fun `hash then matches round trips`() {
        val stored = PinHasher.hash("1234", iterations = fast)
        assertTrue(stored.startsWith("pbkdf2\$$fast\$"))
        assertEquals(4, stored.split("$").size)
        assertTrue(PinHasher.matches("1234", stored))
    }

    @Test
    fun `wrong pin does not match`() {
        assertFalse(PinHasher.matches("4321", PinHasher.hash("1234", iterations = fast)))
    }

    @Test
    fun `two hashes of one pin differ (salt)`() {
        assertNotEquals(PinHasher.hash("1234", fast), PinHasher.hash("1234", fast))
    }

    @Test
    fun `garbage stored value is rejected`() {
        listOf("", "garbage", "pbkdf2\$x\$zz\$yy", "pbkdf2\$1000\$abcd", "pbkdf2\$0\$ab\$cd", "salt:hash")
            .forEach { assertFalse(it, PinHasher.matches("1234", it)) }
    }

    @Test
    fun `pin with leading zeros 0042 round trips`() {
        val stored = PinHasher.hash("0042", iterations = fast)
        assertTrue(PinHasher.matches("0042", stored))
        assertFalse(PinHasher.matches("42", stored))
    }
}
