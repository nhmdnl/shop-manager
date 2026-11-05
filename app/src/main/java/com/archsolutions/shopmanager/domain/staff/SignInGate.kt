package com.archsolutions.shopmanager.domain.staff

/** Sign-in covers the app while staff is on and nobody is signed in; onboarding comes first and is never covered. */
fun shouldShowSignIn(staffEnabled: Boolean, signedIn: Boolean, onboarding: Boolean): Boolean =
    staffEnabled && !signedIn && !onboarding
