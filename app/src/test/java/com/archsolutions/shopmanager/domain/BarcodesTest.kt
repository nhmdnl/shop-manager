package com.archsolutions.shopmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodesTest {
    @Test
    fun `12-digit code is padded to 13`() {
        assertEquals("0123456789012", Barcodes.key("123456789012"))
    }

    @Test
    fun `13-digit code is unchanged`() {
        assertEquals("5449000000996", Barcodes.key("5449000000996"))
    }

    @Test
    fun `text code is trimmed and unchanged`() {
        assertEquals("AB-123", Barcodes.key("  AB-123 "))
    }

    @Test
    fun `candidates of an EAN starting with 0 include the 12-digit UPC form`() {
        assertEquals(listOf("0123456789012", "123456789012"), Barcodes.candidates("0123456789012"))
    }

    @Test
    fun `candidates of a 12-digit UPC include the 13-digit form`() {
        assertEquals(listOf("123456789012", "0123456789012"), Barcodes.candidates("123456789012"))
    }

    @Test
    fun `blank gives an empty candidate list`() {
        assertTrue(Barcodes.candidates("   ").isEmpty())
    }
}
