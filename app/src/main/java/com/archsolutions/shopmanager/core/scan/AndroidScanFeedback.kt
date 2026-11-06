package com.archsolutions.shopmanager.core.scan

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * A short beep and vibration on success, a lower tone on failure. The tone follows the device's media
 * volume (the generator volume is relative to it); vibration is skipped when there is no vibrator.
 * Call [release] when done.
 */
class AndroidScanFeedback(context: Context) : ScanFeedback {
    private val tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
    } catch (_: RuntimeException) {
        null
    }
    private val vibrator: Vibrator? = vibratorOf(context)?.takeIf { it.hasVibrator() }

    override fun success() {
        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun failure() {
        tones?.startTone(ToneGenerator.TONE_PROP_NACK, 250)
    }

    fun release() {
        tones?.release()
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

@Composable
fun rememberAndroidScanFeedback(): ScanFeedback {
    val context = LocalContext.current
    val feedback = remember { AndroidScanFeedback(context.applicationContext) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    return feedback
}
