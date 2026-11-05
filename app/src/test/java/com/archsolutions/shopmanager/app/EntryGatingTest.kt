package com.archsolutions.shopmanager.app

import com.archsolutions.shopmanager.domain.staff.Role
import com.archsolutions.shopmanager.feature.staff.canOpenSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryGatingTest {
    @Test
    fun `every screen reached only through an Owner-gated tap is dropped on sign-out`() {
        assertEquals(
            setOf(CloudRestoreRoute::class, CloudSignInRoute::class, CloudPayRoute::class, StaffRoute::class),
            entryGatedRoutes.toSet(),
        )
        assertFalse(HomeRoute::class in entryGatedRoutes)
        assertFalse(SettingsRoute::class in entryGatedRoutes)
    }

    @Test
    fun `settings is open to anyone with staff off, to an Owner with staff on, closed when unknown`() {
        assertTrue(canOpenSettings(null, false))
        assertTrue(canOpenSettings(Role.CASHIER, false))
        assertTrue(canOpenSettings(Role.OWNER, true))
        assertFalse(canOpenSettings(Role.CASHIER, true))
        assertFalse(canOpenSettings(null, true))
        assertFalse(canOpenSettings(Role.OWNER, null))
        assertFalse(canOpenSettings(null, null))
    }
}
