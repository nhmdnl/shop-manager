package com.archsolutions.shopmanager.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.data.repo.ImportResult
import com.archsolutions.shopmanager.data.repo.ProductImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The outcome of one import; [result] is null when the file couldn't be read. */
data class ImportOutcome(val result: ImportResult?)

@HiltViewModel
class ProductImportViewModel @Inject constructor(
    private val importer: ProductImporter,
) : ViewModel() {
    private val _outcome = MutableStateFlow<ImportOutcome?>(null)
    val outcome: StateFlow<ImportOutcome?> = _outcome.asStateFlow()

    fun import(uri: Uri) {
        viewModelScope.launch { _outcome.value = ImportOutcome(importer.importFrom(uri)) }
    }

    fun outcomeShown() {
        _outcome.value = null
    }
}

/** "Import products" in Settings (7.8 / States board "No products yet"): pick a CSV, then a snackbar says how it went. */
@Composable
fun rememberProductImport(snackbarHostState: SnackbarHostState, viewModel: ProductImportViewModel = hiltViewModel()): () -> Unit {
    val resources = LocalContext.current.resources
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(outcome) {
        val shown = outcome ?: return@LaunchedEffect
        val result = shown.result
        val text = if (result == null) {
            resources.getString(R.string.import_failed)
        } else {
            listOfNotNull(
                resources.getQuantityString(R.plurals.plurals_imported, result.added, result.added),
                result.skipped.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.plurals_skipped, it, it) },
            ).joinToString(" · ")
        }
        snackbarHostState.showSnackbar(text)
        viewModel.outcomeShown()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    return { pick.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }
}
