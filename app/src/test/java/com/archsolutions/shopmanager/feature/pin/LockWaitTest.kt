package com.archsolutions.shopmanager.feature.pin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockWaitTest {
    @Test
    fun `seconds below a minute, minutes rounded up from 60 s`() {
        assertNull(lockWaitMinutes(1))
        assertNull(lockWaitMinutes(30))
        assertNull(lockWaitMinutes(59))
        assertEquals(1, lockWaitMinutes(60))
        assertEquals(2, lockWaitMinutes(61))
        assertEquals(15, lockWaitMinutes(900))
    }
}
