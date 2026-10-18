package com.archsolutions.shopmanager.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.core.util.AppRestart
import com.archsolutions.shopmanager.data.backup.BackupManager
import com.archsolutions.shopmanager.data.backup.RestoreResult
import com.archsolutions.shopmanager.data.repo.DayRepo
import com.archsolutions.shopmanager.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BackupMessage { SAVED, FAILED, NOT_A_BACKUP, TOO_NEW, RESTORED }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {
    private val _message = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = _message.asStateFlow()

    fun fileName(): String = backupManager.fileName()

    fun backUpTo(uri: Uri) = report {
        backupManager.backUpTo(uri)
        BackupMessage.SAVED
    }

    fun restoreFrom(uri: Uri) = report {
        when (backupManager.restoreFrom(uri)) {
            RestoreResult.OK -> BackupMessage.RESTORED
            RestoreResult.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
            RestoreResult.TOO_NEW -> BackupMessage.TOO_NEW
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun report(block: suspend () -> BackupMessage) {
        viewModelScope.launch { _message.value = runCatching { block() }.getOrDefault(BackupMessage.FAILED) }
    }
}

/** "Back up now" and "Restore", each through the system file picker (Drive shows up there too). */
class BackupActions internal constructor(val backUp: () -> Unit, val restore: () -> Unit)

@Composable
fun rememberBackupActions(snackbarHostState: SnackbarHostState, viewModel: BackupViewModel = hiltViewModel()): BackupActions {
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val texts = mapOf(
        BackupMessage.SAVED to stringResource(R.string.backup_saved),
        BackupMessage.FAILED to stringResource(R.string.backup_failed),
        BackupMessage.NOT_A_BACKUP to stringResource(R.string.backup_not_a_backup),
        BackupMessage.TOO_NEW to stringResource(R.string.backup_too_new),
    )
    LaunchedEffect(message) {
        when (val shown = message) {
            null -> Unit
            // The restore is staged; it is swapped in while the app starts again.
            BackupMessage.RESTORED -> AppRestart.restart(context)
            else -> {
                snackbarHostState.showSnackbar(texts.getValue(shown))
                viewModel.messageShown()
            }
        }
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::backUpTo)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreFrom)
    }
    return remember(create, open) {
        BackupActions(
            backUp = { create.launch(viewModel.fileName()) },
            restore = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        )
    }
}

/** Home's "Last backup was N days ago" (section 11): from 7 days after the last backup, or after the first day if never. */
@HiltViewModel
class BackupReminderViewModel @Inject constructor(
    settingsStore: SettingsStore,
    dayRepo: DayRepo,
    clock: AppClock,
) : ViewModel() {
    val reminder: StateFlow<BackupReminder?> = combine(settingsStore.settings, dayRepo.getFirstOpenedAt()) { settings, firstOpened ->
        val since = settings.lastBackupAt ?: firstOpened ?: return@combine null
        val days = ((clock.nowMillis() - since) / DAY_MILLIS).toInt()
        BackupReminder(days = days, never = settings.lastBackupAt == null).takeIf { days >= REMIND_AFTER_DAYS }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val REMIND_AFTER_DAYS = 7
    }
}

data class BackupReminder(val days: Int, val never: Boolean)
