package com.archsolutions.shopmanager.domain.staff

/** What covers the app: nothing, an opaque blank while the staff state is unknown, or the sign-in screen. */
enum class SignInGate { NONE, COVER, SIGN_IN }

/**
 * Unknown (null) fails closed: until the staff setting is read, and, with staff on, until onboarding is known,
 * the app is covered. Once known: staff off or onboarding = no gate; staff on and nobody signed in = sign-in.
 */
fun signInGate(staffEnabled: Boolean?, signedIn: Boolean, onboarding: Boolean?): SignInGate = when {
    staffEnabled == null -> SignInGate.COVER
    !staffEnabled -> SignInGate.NONE
    onboarding == null -> SignInGate.COVER
    onboarding || signedIn -> SignInGate.NONE
    else -> SignInGate.SIGN_IN
}

/** Convenience for known inputs. */
fun shouldShowSignIn(staffEnabled: Boolean, signedIn: Boolean, onboarding: Boolean): Boolean =
    signInGate(staffEnabled, signedIn, onboarding) == SignInGate.SIGN_IN
