package com.archsolutions.shopmanager.core.scan

import android.content.Context
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real camera: CameraX preview plus an ML Kit analyzer (bundled model, works offline).
 *
 * [codes] is a hot flow without replay, so a late collector never sees an old code. The analyzer keeps only the
 * latest frame (no backlog). The camera is bound to [lifecycleOwner] and is unbound when the [Preview] composable
 * leaves the composition; call [close] when the source itself is no longer needed.
 */
class CameraXScanSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) : ScanSource {
    private val _codes = MutableSharedFlow<ScannedCode>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val codes: Flow<ScannedCode> = _codes.asSharedFlow()

    private val _hasTorch = MutableStateFlow(false)
    override val hasTorch: StateFlow<Boolean> = _hasTorch.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_QR_CODE,
            )
            .build(),
    )

    @Volatile private var closed = false
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null

    override fun setTorch(on: Boolean) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(on)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
        DisposableEffect(view) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!closed) bind(future.get(), view)
            }, ContextCompat.getMainExecutor(context))
            onDispose { unbind() }
        }
        AndroidView(factory = { view }, modifier = modifier)
    }

    private fun bind(cameraProvider: ProcessCameraProvider, view: PreviewView) {
        unbind()
        val newPreview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val newAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
            .also { it.setAnalyzer(executor, ::analyze) }
        try {
            val bound = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, newPreview, newAnalysis)
            provider = cameraProvider
            preview = newPreview
            analysis = newAnalysis
            camera = bound
            _hasTorch.value = bound.cameraInfo.hasFlashUnit()
        } catch (_: Exception) {
            // No back camera, or the camera is in use: stay without a preview; the screen still offers Done.
            newAnalysis.clearAnalyzer()
            _hasTorch.value = false
        }
    }

    private fun unbind() {
        analysis?.clearAnalyzer()
        listOfNotNull(preview, analysis).toTypedArray().takeIf { it.isNotEmpty() }?.let { provider?.unbind(*it) }
        preview = null
        analysis = null
        camera = null
        _hasTorch.value = false
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(image: ImageProxy) {
        val media = image.image
        if (closed || media == null) {
            image.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { b -> b.rawValue?.takeIf { it.isNotBlank() }?.let { ScannedCode(it, formatName(b.format)) } }
                    ?.let { _codes.tryEmit(it) }
            }
            .addOnCompleteListener { image.close() }
    }

    /** Releases the analyzer, the ML Kit client and the camera. Safe to call twice. */
    fun close() {
        closed = true
        unbind()
        scanner.close()
        executor.shutdown()
    }

    private fun formatName(format: Int) = when (format) {
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        else -> ""
    }
}

/** A camera source tied to the current lifecycle; closed when the composable leaves. */
@Composable
fun rememberCameraScanSource(): ScanSource {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val source = remember(owner) { CameraXScanSource(context, owner) }
    DisposableEffect(source) { onDispose { source.close() } }
    return source
}
