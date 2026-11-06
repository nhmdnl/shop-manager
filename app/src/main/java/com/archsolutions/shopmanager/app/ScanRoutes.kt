package com.archsolutions.shopmanager.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.archsolutions.shopmanager.core.scan.CameraPermission
import com.archsolutions.shopmanager.core.scan.ScanFeedback
import com.archsolutions.shopmanager.core.scan.ScanSource
import com.archsolutions.shopmanager.core.scan.rememberAndroidScanFeedback
import com.archsolutions.shopmanager.core.scan.rememberCameraPermission
import com.archsolutions.shopmanager.core.scan.rememberCameraScanSource
import com.archsolutions.shopmanager.feature.scan.ScanOnceHost
import com.archsolutions.shopmanager.feature.stock.SCANNED_BARCODE_KEY

/**
 * Pops [entry] only while it is the resumed destination. A second tap on Done (or a second frame) while the exit
 * animation runs must not pop the screen below as well.
 */
internal fun NavController.popIfResumed(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) popBackStack()
}

/**
 * The receiving end of the single-shot scanner: takes the code the scanner left on [entry]'s own savedStateHandle,
 * clears it so it cannot fire again, and passes it to [onCode]. It must read the entry's handle: a Hilt ViewModel's
 * SavedStateHandle is a different object, so the ViewModel cannot see what the scanner wrote.
 */
@Composable
internal fun ScannedCodeHandoff(entry: NavBackStackEntry, onCode: (String) -> Unit) {
    val latest by rememberUpdatedState(onCode)
    LaunchedEffect(entry) {
        val handle = entry.savedStateHandle
        handle.getStateFlow<String?>(SCANNED_BARCODE_KEY, null).collect { code ->
            if (code != null) {
                handle[SCANNED_BARCODE_KEY] = null
                latest(code)
            }
        }
    }
}

/** The ScanOnceRoute screen: hands the first code to the entry below it (see [ScannedCodeHandoff]) and leaves. */
@Composable
internal fun ScanOnceDestination(
    navController: NavController,
    entry: NavBackStackEntry,
    source: ScanSource = rememberCameraScanSource(),
    feedback: ScanFeedback = rememberAndroidScanFeedback(),
    permission: CameraPermission = rememberCameraPermission(),
) {
    ScanOnceHost(
        onCode = { code ->
            if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_BARCODE_KEY, code)
                navController.popBackStack()
            }
        },
        onDone = { navController.popIfResumed(entry) },
        source = source,
        feedback = feedback,
        permission = permission,
    )
}
