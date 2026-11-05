package com.archsolutions.shopmanager.feature.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinEntryTest {
    @Test
    fun `the fourth digit hands back the PIN once and starts checking`() {
        val e = PinEntry()
        assertNull(e.digit("1"))
        assertNull(e.digit("2"))
        assertNull(e.digit("3"))
        assertEquals("1234", e.digit("4"))
        assertTrue(e.checking)
    }

    @Test
    fun `digits and backspace are ignored while a check runs`() {
        val e = PinEntry()
        "123".forEach { e.digit(it.toString()) }
        e.digit("4")
        assertNull(e.digit("4"))
        assertNull(e.digit("9"))
        e.backspace()
        assertEquals("1234", e.digits)
    }

    @Test
    fun `finishing a failed check clears the digits and accepts taps again`() {
        val e = PinEntry()
        "1234".forEach { e.digit(it.toString()) }
        e.finish(clear = true)
        assertFalse(e.checking)
        assertEquals("", e.digits)
        assertNull(e.digit("5"))
        assertEquals("5", e.digits)
    }
}
