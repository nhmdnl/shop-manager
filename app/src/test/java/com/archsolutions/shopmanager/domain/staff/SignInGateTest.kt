package com.archsolutions.shopmanager.domain.staff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInGateTest {
    @Test
    fun `shows only when staff is on, nobody is signed in and onboarding is over`() {
        assertTrue(shouldShowSignIn(staffEnabled = true, signedIn = false, onboarding = false))
        assertFalse(shouldShowSignIn(staffEnabled = false, signedIn = false, onboarding = false))
        assertFalse(shouldShowSignIn(staffEnabled = true, signedIn = true, onboarding = false))
        assertFalse(shouldShowSignIn(staffEnabled = true, signedIn = false, onboarding = true))
    }

    @Test
    fun `unknown staff setting covers the app whoever is signed in`() {
        for (signedIn in listOf(true, false)) for (onboarding in listOf(true, false, null)) {
            assertEquals(SignInGate.COVER, signInGate(null, signedIn, onboarding))
        }
    }

    @Test
    fun `staff on with unknown onboarding covers the app`() {
        assertEquals(SignInGate.COVER, signInGate(true, signedIn = false, onboarding = null))
        assertEquals(SignInGate.COVER, signInGate(true, signedIn = true, onboarding = null))
    }

    @Test
    fun `known staff off has no gate even while onboarding is unknown`() {
        assertEquals(SignInGate.NONE, signInGate(false, signedIn = false, onboarding = null))
        assertEquals(SignInGate.NONE, signInGate(false, signedIn = false, onboarding = false))
    }

    @Test
    fun `known staff on follows the usual rules`() {
        assertEquals(SignInGate.SIGN_IN, signInGate(true, signedIn = false, onboarding = false))
        assertEquals(SignInGate.NONE, signInGate(true, signedIn = true, onboarding = false))
        assertEquals(SignInGate.NONE, signInGate(true, signedIn = false, onboarding = true))
    }
}
