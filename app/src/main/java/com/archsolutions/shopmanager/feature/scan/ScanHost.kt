package com.archsolutions.shopmanager.feature.scan

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import java.util.concurrent.atomic.AtomicBoolean
import com.archsolutions.shopmanager.core.designsystem.LocalUserActivity
import com.archsolutions.shopmanager.core.scan.CameraPermission
import com.archsolutions.shopmanager.core.scan.ScanFeedback
import com.archsolutions.shopmanager.core.scan.ScanSource
import com.archsolutions.shopmanager.core.scan.rememberAndroidScanFeedback
import com.archsolutions.shopmanager.core.scan.rememberCameraPermission
import com.archsolutions.shopmanager.core.scan.rememberCameraScanSource
import com.archsolutions.shopmanager.data.db.entities.ProductEntity

/**
 * A scanner wired to its [ScanViewModel]: each accepted code is looked up, and a known product goes to [onKnown]
 * (which must be idempotent). The camera, permission and feedback default to the real ones; tests pass fakes.
 */
@Composable
fun ScanHost(
    continuous: Boolean,
    onKnown: suspend (ProductEntity) -> Unit,
    onAddProduct: (String) -> Unit,
    onDone: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
    source: ScanSource = rememberCameraScanSource(),
    feedback: ScanFeedback = rememberAndroidScanFeedback(),
    permission: CameraPermission = rememberCameraPermission(),
) {
    val state by viewModel.state.collectAsState()
    val userActivity = LocalUserActivity.current
    ScanScreen(
        source = source,
        feedback = feedback,
        permission = permission,
        continuous = continuous,
        state = state,
        onCode = { viewModel.onCode(it, userActivity, onKnown) },
        onAddProduct = { code ->
            viewModel.clearNotFound()
            onAddProduct(code)
        },
        onDone = onDone,
    )
}

/**
 * A single-shot scanner that hands back the first accepted code as it is read (no product lookup): Edit product
 * fills its barcode field, Stock looks the code up itself. [onCode] runs at most once, however many frames arrive
 * while the screen is leaving. Cancel and sign-out call [onDone].
 */
@Composable
fun ScanOnceHost(
    onCode: (String) -> Unit,
    onDone: () -> Unit,
    source: ScanSource = rememberCameraScanSource(),
    feedback: ScanFeedback = rememberAndroidScanFeedback(),
    permission: CameraPermission = rememberCameraPermission(),
) {
    val delivered = remember { AtomicBoolean(false) }
    val userActivity = LocalUserActivity.current
    ScanScreen(
        source = source,
        feedback = feedback,
        permission = permission,
        continuous = false,
        state = ScanUiState(),
        onCode = { scanned ->
            val text = scanned.text.trim()
            if (text.isNotEmpty() && delivered.compareAndSet(false, true)) {
                userActivity()
                feedback.success()
                onCode(text)
            }
        },
        onAddProduct = {},
        onDone = onDone,
    )
}
