package com.archsolutions.shopmanager.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.archsolutions.shopmanager.core.designsystem.LocalUserActivity
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.scan.CameraPermission
import com.archsolutions.shopmanager.core.scan.CameraPermissionState
import com.archsolutions.shopmanager.core.scan.ScanFeedback
import com.archsolutions.shopmanager.core.scan.ScanSource
import com.archsolutions.shopmanager.core.scan.ScannedCode
import com.archsolutions.shopmanager.feature.staff.OnSignOut

private const val SINGLE_SHOT_UNLOCK_MILLIS = 1_500L
const val SCAN_PREVIEW_AREA_TAG = "scan_preview_area"

/**
 * Full-screen scanner. [continuous] keeps scanning with a running count (Sell, Purchases) and ends with Done;
 * single-shot has Cancel (the host closes the screen on the first known code). Leaving the composition, or a
 * sign-out, releases the camera because the preview leaves the tree.
 *
 * The permission is requested once on opening (the first Scan tap); "Allow camera" stays as the fallback.
 * In single-shot mode only the first code is forwarded to [onCode] until a not-found result arrives (or 1.5 s pass without any result), because
 * camera frames keep coming during the exit transition; the host's result callback must still be idempotent.
 */
@Composable
fun ScanScreen(
    source: ScanSource,
    feedback: ScanFeedback,
    permission: CameraPermission,
    continuous: Boolean,
    state: ScanUiState,
    onCode: (ScannedCode) -> Unit,
    onAddProduct: (String) -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalShopColors.current
    val granted = permission.state == CameraPermissionState.Granted
    val latestOnCode by rememberUpdatedState(onCode)
    val hasTorch by source.hasTorch.collectAsStateWithLifecycle()
    val unavailable by source.unavailable.collectAsStateWithLifecycle()
    val showPreview = granted && !unavailable
    var torchOn by remember { mutableStateOf(false) }
    val torchState = stringResource(if (torchOn) R.string.scan_torch_on else R.string.scan_torch_off)

    OnSignOut { onDone() }
    LaunchedEffect(hasTorch) { if (!hasTorch) torchOn = false } // an unbound camera has its torch off

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { torchOn = false } // CameraX turns the torch off when it unbinds

    var autoAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (permission.state == CameraPermissionState.NotAsked && !autoAsked) {
            autoAsked = true
            permission.request()
        }
    }

    // Only while the permission is there is anything decoded; the flow is hot, so no old code is replayed.
    // Single-shot: after a code goes out, nothing more until a not-found result, or 1.5 s, unlocks it (a known one ends the screen).
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(state.results) { if (state.notFound != null) locked = false }
    // A code that produced no result (the lookup or the host failed) must not lock the scanner for good.
    LaunchedEffect(locked) {
        if (locked) {
            delay(SINGLE_SHOT_UNLOCK_MILLIS)
            locked = false
        }
    }
    // Codes are taken only while this screen is resumed: not during the exit animation after Done, not in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val userActivity by rememberUpdatedState(LocalUserActivity.current)
    LaunchedEffect(source, granted, lifecycle) {
        if (granted) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                source.codes.collect {
                    if (continuous || !locked) {
                        if (!continuous) locked = true
                        userActivity() // a scan is activity: it keeps the staff idle timer from signing out mid-delivery
                        latestOnCode(it)
                    }
                }
            }
        }
    }
    // One beep or tone per new result (not on the first composition, e.g. after rotation).
    var seenResults by remember { mutableStateOf(state.results) }
    LaunchedEffect(state.results) {
        if (state.results != seenResults) {
            seenResults = state.results
            if (state.notFound != null) feedback.failure() else feedback.success()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.paper)) {
        val panelMax = maxHeight * 0.65f
        val previewMin = minOf(120.dp, maxHeight * 0.35f) // yields on very short screens so the panel can scroll to Done
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).heightIn(min = previewMin).fillMaxWidth().testTag(SCAN_PREVIEW_AREA_TAG)) {
                if (showPreview) {
                    source.Preview(Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight(0.4f)
                            .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)),
                    )
                    if (hasTorch) {
                        SecondaryButton(
                            text = stringResource(R.string.scan_torch),
                            onClick = {
                                torchOn = !torchOn
                                source.setTorch(torchOn)
                            },
                            leadingIcon = if (torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                            height = 48.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(colors.surface, RoundedCornerShape(14.dp))
                                .semantics { stateDescription = torchState },
                        )
                    }
                } else if (!granted) {
                    PermissionPanel(permission)
                } else {
                    Column(
                        Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.scan_camera_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.ink,
                            textAlign = TextAlign.Center,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.scan_try_again),
                            onClick = source::retry,
                            height = 48.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(max = panelMax).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (granted && continuous) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            pluralStringResource(R.plurals.scan_items_count, state.count, state.count),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        state.last?.let {
                            Text(
                                stringResource(R.string.scan_last_item, it, state.lastPrice?.let { p -> Money.format(p, withCode = false) }.orEmpty()).trim(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.muted,
                            )
                        }
                    }
                }
                if (granted && state.notFound == null && state.last == null) {
                    Text(stringResource(R.string.scan_aim_hint), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
                state.notFound?.let { code ->
                    Column(
                        Modifier.fillMaxWidth().background(colors.rustTint, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.scan_not_found, code),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.rust,
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
                        )
                        SecondaryButton(
                            text = stringResource(R.string.scan_add_product),
                            onClick = { onAddProduct(code) },
                            height = 48.dp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // One green primary action: Done while scanning in a list; otherwise the permission or Cancel buttons lead.
                if (continuous && granted) {
                    PrimaryButton(text = stringResource(R.string.scan_done), onClick = onDone, height = 56.dp)
                } else {
                    SecondaryButton(
                        text = stringResource(if (continuous) R.string.scan_done else R.string.scan_cancel),
                        onClick = onDone,
                        height = 56.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Shown instead of the preview when the camera permission is missing: one line and what to do about it. */
@Composable
private fun PermissionPanel(permission: CameraPermission) {
    val colors = LocalShopColors.current
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.scan_permission_needed),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            if (permission.state == CameraPermissionState.NotAsked) {
                PrimaryButton(text = stringResource(R.string.scan_permission_allow), onClick = permission::request, height = 56.dp)
            }
            SecondaryButton(
                text = stringResource(R.string.scan_open_settings),
                onClick = permission::openSettings,
                height = 48.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
