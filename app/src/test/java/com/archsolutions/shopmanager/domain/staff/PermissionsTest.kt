package com.archsolutions.shopmanager.domain.staff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {
    @Test
    fun `owner can do everything`() {
        assertTrue(Permission.entries.all { Role.OWNER.can(it) })
    }

    @Test
    fun `cashier can only sell`() {
        assertEquals(listOf(Permission.SELL), Permission.entries.filter { Role.CASHIER.can(it) })
    }

    @Test
    fun `unknown or null role can do nothing`() {
        assertNull(Role.fromKey("MANAGER"))
        assertTrue(Permission.entries.none { Role.fromKey("MANAGER").can(it) })
        val none: Role? = null
        assertFalse(Permission.entries.any { none.can(it) })
        assertEquals(Role.CASHIER, Role.fromKey("CASHIER"))
    }
}
