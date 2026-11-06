package com.archsolutions.shopmanager.core.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class CameraPermissionState { Granted, Denied, NotAsked }

/** Camera permission as the scan screen sees it; an interface so tests can pass a fake. */
interface CameraPermission {
    val state: CameraPermissionState
    fun request()
    fun openSettings()
}

/**
 * Runtime permission through the Activity Result API. [CameraPermissionState.Denied] covers "never ask again"
 * (the system answers a request at once). The state is re-read on resume, since the user may grant it in system settings.
 */
@Composable
fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var state by remember { mutableStateOf(readState(context, asked)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked = true
        state = if (granted) CameraPermissionState.Granted else CameraPermissionState.Denied
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state = readState(context, asked) }

    val current = { state }
    return remember(launcher) {
        object : CameraPermission {
            override val state: CameraPermissionState get() = current()
            override fun request() = launcher.launch(Manifest.permission.CAMERA)
            override fun openSettings() {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        }
    }
}

private fun readState(context: Context, asked: Boolean): CameraPermissionState {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        return CameraPermissionState.Granted
    }
    val activity = context.findActivity()
    val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
    return if (asked || rationale) CameraPermissionState.Denied else CameraPermissionState.NotAsked
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** True when the device reports any camera (front, back or external). */
@Composable
fun rememberHasCamera(): Boolean {
    val context = LocalContext.current
    return remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
}
