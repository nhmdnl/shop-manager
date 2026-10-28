package com.archsolutions.shopmanager.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.cloud.contract.BackupDto
import com.archsolutions.shopmanager.core.util.AppClock
import com.archsolutions.shopmanager.data.backup.RestoreResult
import com.archsolutions.shopmanager.data.cloud.CloudBackups
import com.archsolutions.shopmanager.data.cloud.CloudException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject

data class RestoreUiState(
    /** Newest first. */
    val backups: List<BackupDto> = emptyList(),
    val loading: Boolean = true,
    /** The backup the owner tapped, waiting for "Replace everything?". */
    val confirm: BackupDto? = null,
    /** [RestoreResult.OK] restarts the app; the other two show their existing messages. */
    val result: RestoreResult? = null,
    /** The list or the download failed; nothing was staged and this tablet's data is as it was. */
    val error: Boolean = false,
)

@HiltViewModel
class CloudRestoreViewModel @Inject constructor(
    private val backups: CloudBackups,
    clock: AppClock,
) : ViewModel() {
    /** Dates on this screen are in the shop's zone. */
    val zoneId: ZoneId = clock.zoneId

    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            try {
                val list = backups.list().sortedByDescending { it.backupTime }
                _state.update { it.copy(backups = list, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isCloudFailure()) throw e
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun pick(b: BackupDto) = _state.update { if (it.loading) it else it.copy(confirm = b, error = false) }

    fun cancel() = _state.update { it.copy(confirm = null) }

    fun messageShown() = _state.update { it.copy(result = null) }

    fun confirmRestore() {
        val picked = _state.value.confirm ?: return
        if (_state.value.loading) return
        _state.update { it.copy(confirm = null, loading = true, error = false) }
        viewModelScope.launch {
            val temp = backups.tempFile()
            try {
                backups.download(picked.backupId, temp)
                val result = backups.restore(temp)
                _state.update { it.copy(result = result, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isCloudFailure()) throw e
                _state.update { it.copy(loading = false, error = true) }
            } finally {
                temp.delete()
            }
        }
    }

    private fun Exception.isCloudFailure() = this is IOException || this is CloudException
}
