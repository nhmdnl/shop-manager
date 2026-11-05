package com.archsolutions.shopmanager.feature.staff

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.LocalUserActivity
import com.archsolutions.shopmanager.domain.staff.SignInGate
import com.archsolutions.shopmanager.domain.staff.signInGate
import kotlinx.coroutines.delay

private const val IDLE_CHECK_MILLIS = 15_000L

/**
 * Wraps the app shell. While [onboarding] or the staff setting is unknown the shell is covered (fail closed); with
 * staff on and nobody signed in the sign-in screen covers it. The shell stays composed (it holds the back stack and the
 * cart) but is taken out of touch, accessibility and focus while covered. Also runs the idle checks.
 */
@Composable
fun StaffGate(onboarding: Boolean?, viewModel: StaffGateViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val staffActive by viewModel.staffActive.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val tick by viewModel.signOutTick.collectAsStateWithLifecycle()
    val gate = signInGate(staffActive, signedIn = current != null, onboarding = onboarding)
    val covered = gate != SignInGate.NONE
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        while (true) {
            delay(IDLE_CHECK_MILLIS)
            viewModel.checkIdle()
        }
    }
    // A resumed app must not show stale signed-in state for up to a tick.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkIdle() }
    LaunchedEffect(covered) { if (covered) focusManager.clearFocus(force = true) }

    StaffGateLayer(gate, tick, onActivity = viewModel::touch, content = content)
}

/** The layering for a known [gate] state; stateless so it can be tested without Hilt. */
@Composable
internal fun StaffGateLayer(
    gate: SignInGate,
    signOutTick: Int,
    onActivity: () -> Unit = {},
    signIn: @Composable () -> Unit = { StaffSignInScreen() },
    content: @Composable () -> Unit,
) {
    val covered = gate != SignInGate.NONE
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSignOutTick provides signOutTick, LocalUserActivity provides onActivity) {
            Box(
                if (covered) Modifier.fillMaxSize().clearAndSetSemantics {}.focusProperties { canFocus = false }
                else Modifier.fillMaxSize(),
            ) { content() }
        }
        if (covered) {
            val activity = LocalContext.current.findActivity()
            // Back sends the app to the background; it never dismisses the gate or reaches the shell underneath.
            BackHandler { activity?.moveTaskToBack(true) }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(LocalShopColors.current.paper)
                    .semantics { isTraversalGroup = true }
                    .pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    },
            ) {
                if (gate == SignInGate.SIGN_IN) signIn()
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
