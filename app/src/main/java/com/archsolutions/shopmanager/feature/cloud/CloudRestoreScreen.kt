package com.archsolutions.shopmanager.feature.cloud

import com.archsolutions.shopmanager.feature.staff.OnSignOut
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.cloud.contract.BackupDto
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.CappedFontScale
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.util.AppRestart
import com.archsolutions.shopmanager.data.backup.RestoreResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Lists the shop's cloud backups, newest first, and restores the one the owner picks. [onBack] goes back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudRestoreScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudRestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val context = LocalContext.current

    // The restore is staged; it is swapped in while the app starts again.
    LaunchedEffect(state.result) {
        if (state.result == RestoreResult.OK) AppRestart.restart(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = {
                    CappedFontScale {
                        Text(
                            text = stringResource(R.string.cloud_restore_title),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = colors.ink,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loading) CircularProgressIndicator(color = colors.green)
            state.result?.takeIf { it != RestoreResult.OK }?.let { result ->
                Text(
                    text = stringResource(if (result == RestoreResult.TOO_NEW) R.string.backup_too_new else R.string.backup_not_a_backup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.error) {
                Text(
                    text = stringResource(R.string.cloud_restore_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (state.backups.isEmpty()) {
                    PrimaryButton(text = stringResource(R.string.cloud_pay_retry), onClick = viewModel::load, enabled = !state.loading)
                }
            }
            if (!state.loading && !state.error && state.backups.isEmpty()) {
                Text(
                    text = stringResource(R.string.cloud_restore_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.backups, key = { it.backupId }) { backup ->
                    BackupRow(backup, viewModel.zoneId, enabled = !state.loading, onClick = { viewModel.pick(backup) })
                }
            }
        }
    }

    // The previous person's pending restore must not wait for the next one.
    OnSignOut(viewModel::cancel)
    state.confirm?.let { picked ->
        AlertDialog(
            onDismissRequest = viewModel::cancel,
            title = { Text(stringResource(R.string.cloud_restore_title)) },
            text = { Text(stringResource(R.string.cloud_restore_confirm, formatBackupTime(picked.backupTime, viewModel.zoneId))) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.cloud_restore_replace)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private val backupTimeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun formatBackupTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(backupTimeFormat)

@Composable
private fun BackupRow(backup: BackupDto, zone: ZoneId, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalShopColors.current
    val context = LocalContext.current
    ShopCard(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatBackupTime(backup.backupTime, zone),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Formatter.formatShortFileSize(context, backup.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}
