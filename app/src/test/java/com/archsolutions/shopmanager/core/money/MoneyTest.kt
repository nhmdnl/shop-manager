package com.archsolutions.shopmanager.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `format returns whole UGX formatted with commas`() {
        assertEquals("UGX 195,900", Money.format(195_900L))
        assertEquals("UGX 0", Money.format(0L))
        assertEquals("UGX 1,500", Money.format(1500L))
        assertEquals("UGX 3,420,000", Money.format(3_420_000L))
    }

    @Test
    fun `format withCode false omits UGX prefix`() {
        assertEquals("195,900", Money.format(195_900L, withCode = false))
        assertEquals("0", Money.format(0L, withCode = false))
    }

    @Test
    fun `signed returns plus or minus with unicode minus`() {
        assertEquals("+ 195,900", Money.signed(195_900L))
        assertEquals("− 900", Money.signed(-900L))
        assertEquals("+ 0", Money.signed(0L))
    }

    @Test
    fun `short formats k and M for charts`() {
        assertEquals("500", Money.short(500L))
        assertEquals("186k", Money.short(186_000L))
        assertEquals("3.42M", Money.short(3_420_000L))
    }

    @Test
    fun `parse extracts digits correctly`() {
        assertEquals(195_900L, Money.parse("UGX 195,900"))
        assertEquals(1500L, Money.parse("1500"))
        assertEquals(0L, Money.parse("0"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
    }
}
