package com.archsolutions.shopmanager.core.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A camera (or test double) that yields decoded barcodes and draws its own preview. */
interface ScanSource {
    val codes: Flow<ScannedCode>
    val hasTorch: StateFlow<Boolean>
    fun setTorch(on: Boolean)

    @Composable
    fun Preview(modifier: Modifier)
}
