package com.archsolutions.shopmanager.core.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** A camera (or test double) that yields decoded barcodes and draws its own preview. */
interface ScanSource {
    val codes: Flow<ScannedCode>
    val hasTorch: StateFlow<Boolean>

    /**
     * True when the camera cannot be used (no usable camera, start failure, a fatal camera error, or a recoverable
     * error that persisted about 8 s). It clears only when the camera reports OPEN again or [retry] is called.
     */
    val unavailable: StateFlow<Boolean>
    fun setTorch(on: Boolean)

    /** Clears [unavailable] so the screen shows the preview again, which makes a new bind attempt. */
    fun retry()

    @Composable
    fun Preview(modifier: Modifier)
}
