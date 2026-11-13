package com.archsolutions.shopmanager.feature.staff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Counts sign-outs (idle or Switch user). Dialog windows float above the sign-in cover, so their hosts react to it. */
val LocalSignOutTick = compositionLocalOf { 0 }

/** Runs [action] each time somebody signs out while this is composed; use it to close a dialog or sheet. */
@Composable
fun OnSignOut(action: () -> Unit) {
    val tick = LocalSignOutTick.current
    val first = remember { tick }
    val latest = rememberUpdatedState(action)
    LaunchedEffect(tick) { if (tick != first) latest.value() }
}
