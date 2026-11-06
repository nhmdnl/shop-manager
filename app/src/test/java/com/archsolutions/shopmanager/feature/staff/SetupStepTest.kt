package com.archsolutions.shopmanager.feature.staff

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupStepTest {
    @Test
    fun `nothing is shown until the existing-Owner check is back`() {
        assertEquals(SetupStep.LOADING, setupStep(null, true, false, false))
    }

    @Test
    fun `turning staff back on asks only the Owner PIN`() {
        assertEquals(SetupStep.REENABLE, setupStep(true, true, true, true))
    }

    @Test
    fun `with a shared PIN set it is confirmed before the name, then the name, then the new PIN`() {
        assertEquals(SetupStep.CONFIRM_SHARED_PIN, setupStep(false, true, false, false))
        assertEquals(SetupStep.NAME, setupStep(false, true, true, false))
        assertEquals(SetupStep.NEW_PIN, setupStep(false, true, true, true))
    }

    @Test
    fun `without a shared PIN the name comes first and the PIN last`() {
        assertEquals(SetupStep.NAME, setupStep(false, false, false, false))
        assertEquals(SetupStep.NEW_PIN, setupStep(false, false, false, true))
    }

    @Test
    fun `the name is never skipped and the shared PIN is never skipped`() {
        assertEquals(SetupStep.CONFIRM_SHARED_PIN, setupStep(false, true, false, true))
    }
}
