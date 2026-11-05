package com.archsolutions.shopmanager.feature.staff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.domain.staff.shouldShowSignIn
import kotlinx.coroutines.delay

private const val IDLE_CHECK_MILLIS = 15_000L

/**
 * Covers the whole app with sign-in while staff is on and nobody is signed in (never during onboarding), and runs the
 * idle check. Put it above the nav host; it holds no state that outlives the process.
 */
@Composable
fun StaffGate(onboarding: Boolean, viewModel: StaffGateViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            delay(IDLE_CHECK_MILLIS)
            viewModel.checkIdle()
        }
    }

    if (shouldShowSignIn(settings.staffEnabled, signedIn = current != null, onboarding = onboarding)) {
        // Nothing underneath may be tapped or backed into while signed out.
        BackHandler {}
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
        ) {
            StaffSignInScreen()
        }
    }
}
