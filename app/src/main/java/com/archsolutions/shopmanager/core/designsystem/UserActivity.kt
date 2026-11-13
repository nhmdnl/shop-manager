package com.archsolutions.shopmanager.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Called when the person does something that never reaches the Activity's touch dispatch: a PIN key press or a typed
 * character inside a Dialog window or on the soft keyboard. The app shell wires it to the staff idle clock; cheap by design.
 */
val LocalUserActivity = staticCompositionLocalOf<() -> Unit> { {} }
