package com.archsolutions.shopmanager.core.scan

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
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
import androidx.lifecycle.Observer
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

    private val _unavailable = MutableStateFlow(false)
    override val unavailable: StateFlow<Boolean> = _unavailable.asStateFlow()

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
    private var stateObserver: Observer<CameraState>? = null

    override fun setTorch(on: Boolean) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(on)
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        val activityContext = LocalContext.current
        val view = remember { PreviewView(activityContext).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
        DisposableEffect(view) {
            var active = true // a listener that runs after this effect left must not bind a detached view
            _unavailable.value = false
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (active && !closed) {
                    runCatching { bind(future.get(), view) }.onFailure { _unavailable.value = true }
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                active = false
                unbind()
            }
        }
        AndroidView(factory = { view }, modifier = modifier)
    }

    private fun bind(cameraProvider: ProcessCameraProvider, view: PreviewView) {
        unbind()
        val selector = when {
            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> {
                _unavailable.value = true
                return
            }
        }
        val portrait = context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val bound = if (portrait) Size(720, 1280) else Size(1280, 720) // the bound is read in the display's rotation
        val newPreview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val newAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(bound, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
            .also { it.setAnalyzer(executor, ::analyze) }
        try {
            val cam = cameraProvider.bindToLifecycle(lifecycleOwner, selector, newPreview, newAnalysis)
            provider = cameraProvider
            preview = newPreview
            analysis = newAnalysis
            camera = cam
            _hasTorch.value = cam.cameraInfo.hasFlashUnit()
            // bindToLifecycle does not throw for a camera that is in use or disabled; it reports it here.
            val observer = Observer<CameraState> { st ->
                val error = st.error
                when {
                    error == null -> {
                        cancelGiveUp()
                        if (st.type == CameraState.Type.OPEN) _unavailable.value = false
                    }
                    isFatal(error) -> {
                        cancelGiveUp()
                        _unavailable.value = true
                    }
                    else -> scheduleGiveUp() // in use, too many cameras, other recoverable: CameraX retries, so wait
                }
            }
            stateObserver = observer
            cam.cameraInfo.cameraState.observeForever(observer)
        } catch (_: Exception) {
            newAnalysis.clearAnalyzer()
            _hasTorch.value = false
            _unavailable.value = true
        }
    }

    /** Only genuinely fatal errors; in-use, max-cameras and other recoverable codes are retried by CameraX. */
    private fun isFatal(error: CameraState.StateError) = error.type == CameraState.ErrorType.CRITICAL ||
        error.code == CameraState.ERROR_CAMERA_DISABLED || error.code == CameraState.ERROR_CAMERA_FATAL_ERROR ||
        error.code == CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED

    private val main = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { _unavailable.value = true }
    private var giveUpScheduled = false

    /** A recoverable error that is still there after [GIVE_UP_MILLIS] counts as unavailable; the first one starts the clock. */
    private fun scheduleGiveUp() {
        if (giveUpScheduled) return
        giveUpScheduled = true
        main.postDelayed(giveUp, GIVE_UP_MILLIS)
    }

    private fun cancelGiveUp() {
        giveUpScheduled = false
        main.removeCallbacks(giveUp)
    }

    override fun retry() {
        cancelGiveUp()
        _unavailable.value = false
    }

    private fun unbind() {
        stateObserver?.let { camera?.cameraInfo?.cameraState?.removeObserver(it) }
        stateObserver = null
        cancelGiveUp()
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
        val rotation = image.imageInfo.rotationDegrees
        val sideways = rotation == 90 || rotation == 270
        val uprightW = if (sideways) image.height else image.width
        val uprightH = if (sideways) image.width else image.height
        try {
            scanner.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener { barcodes ->
                    val usable = barcodes.filter { !it.rawValue.isNullOrBlank() }
                    val centres = usable.map { b -> b.boundingBox?.let { Centre(it.exactCenterX(), it.exactCenterY()) } }
                    val chosen = usable.getOrNull(pickCentralBarcode(centres, uprightW, uprightH)) ?: return@addOnSuccessListener
                    _codes.tryEmit(ScannedCode(chosen.rawValue.orEmpty(), formatName(chosen.format)))
                }
                .addOnCompleteListener { image.close() }
        } catch (_: Exception) {
            image.close() // process() threw before it could take ownership of the frame
        }
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

private const val GIVE_UP_MILLIS = 8_000L
