package com.archsolutions.shopmanager.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.feature.pin.rememberPinGate

/** Settings: the cloud link, the plan and the last cloud backup. Not drawn when the build has no cloud. */
@Composable
fun CloudSettingsCard(
    onSignIn: () -> Unit,
    onPay: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudViewModel = hiltViewModel(),
) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    if (!card.visible) return
    val pinGate = rememberPinGate()
    var confirmingUnlink by remember { mutableStateOf(false) }
    // The lines are written in the app language; write them again when it changes.
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { viewModel.refreshTexts() }

    CloudSettingsCardContent(
        card = card,
        onSignIn = { pinGate.run(R.string.pin_for_cloud, onSignIn) },
        onPay = { pinGate.run(R.string.pin_for_cloud, onPay) },
        onRestore = { pinGate.run(R.string.pin_for_restore, onRestore) },
        onBackUpNow = viewModel::backUpNow,
        onUnlink = { pinGate.run(R.string.pin_for_cloud) { confirmingUnlink = true } },
        modifier = modifier,
    )

    if (confirmingUnlink) {
        AlertDialog(
            onDismissRequest = { confirmingUnlink = false },
            title = { Text(stringResource(R.string.cloud_unlink_title)) },
            text = { Text(stringResource(R.string.cloud_unlink_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUnlink = false
                        viewModel.unlink()
                    },
                ) { Text(stringResource(R.string.cloud_unlink)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnlink = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The card itself, from plain state: the buttons' actions arrive already behind the PIN gate. */
@Composable
fun CloudSettingsCardContent(
    card: CloudCardState,
    onSignIn: () -> Unit,
    onPay: () -> Unit,
    onRestore: () -> Unit,
    onBackUpNow: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(text = stringResource(R.string.cloud_card_title))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                card.planLine?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                }
                Text(card.backupLine, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            if (!card.signedIn) {
                SecondaryButton(
                    text = stringResource(R.string.cloud_sign_in),
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.cloud_back_up_now),
                        onClick = onBackUpNow,
                        enabled = card.canBackUpNow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.cloud_pay),
                        onClick = onPay,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.cloud_restore),
                        onClick = onRestore,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.cloud_unlink),
                        onClick = onUnlink,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

}
