package com.archsolutions.shopmanager.domain.staff

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
}
