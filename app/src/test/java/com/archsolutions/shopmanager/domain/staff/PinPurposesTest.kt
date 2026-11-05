package com.archsolutions.shopmanager.domain.staff

import com.archsolutions.shopmanager.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPurposesTest {
    private val purposes = R.string::class.java.fields.filter { it.name.startsWith("pin_for_") }

    @Test
    fun `every pin_for string is mapped and none is SELL`() {
        assertTrue(purposes.size >= 7)
        purposes.forEach {
            assertNotEquals(it.name, Permission.SELL, permissionFor(it.getInt(null)))
        }
    }

    @Test
    fun `purposes map to their permissions`() {
        assertEquals(Permission.VOID_SALE, permissionFor(R.string.pin_for_void))
        assertEquals(Permission.CORRECT_STOCK, permissionFor(R.string.pin_for_stock))
        assertEquals(Permission.CLOSE_OR_REOPEN_DAY, permissionFor(R.string.pin_for_close))
        assertEquals(Permission.CLOSE_OR_REOPEN_DAY, permissionFor(R.string.pin_for_reopen))
        assertEquals(Permission.ADMIN_SETTINGS, permissionFor(R.string.pin_for_restore))
        assertEquals(Permission.ADMIN_SETTINGS, permissionFor(R.string.pin_for_settings))
        assertEquals(Permission.ADMIN_SETTINGS, permissionFor(R.string.pin_for_cloud))
        assertEquals(Permission.MANAGE_STAFF, permissionFor(R.string.pin_for_staff))
    }
}
