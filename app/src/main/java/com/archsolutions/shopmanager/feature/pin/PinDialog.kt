package com.archsolutions.shopmanager.feature.pin

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.data.settings.PinGuard
import com.archsolutions.shopmanager.data.settings.PinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class PinViewModel @Inject constructor(
    private val pinGuard: PinGuard,
) : ViewModel() {
    val required = pinGuard.required

    suspend fun check(pin: String): PinResult = pinGuard.check(pin)
}

/** Asks for the PIN before an action when one is set (addendum C4); otherwise runs it straight away. */
@Stable
class PinGate internal constructor(private val required: State<Boolean>) {
    internal var request by mutableStateOf<PinRequest?>(null)

    fun run(@StringRes purpose: Int, action: () -> Unit) {
        if (required.value) request = PinRequest(purpose, action) else action()
    }
}

internal data class PinRequest(@StringRes val purpose: Int, val onSuccess: () -> Unit)

/** A [PinGate] for this screen; the PIN dialog shows here while a request is waiting. */
@Composable
fun rememberPinGate(viewModel: PinViewModel = hiltViewModel()): PinGate {
    val required = viewModel.required.collectAsStateWithLifecycle()
    val gate = remember(required) { PinGate(required) }
    gate.request?.let { request ->
        PinCheckDialog(
            purpose = stringResource(request.purpose),
            check = viewModel::check,
            onDismiss = { gate.request = null },
            onSuccess = {
                gate.request = null
                request.onSuccess()
            },
        )
    }
    return gate
}

@Composable
private fun PinCheckDialog(purpose: String, check: suspend (String) -> PinResult, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    PinKeypadDialog(
        title = stringResource(R.string.pin_enter),
        subtitle = purpose,
        digits = digits,
        message = message,
        onDigit = { digit ->
            if (digits.length < Pin.LENGTH) digits += digit
            if (digits.length == Pin.LENGTH) {
                val entered = digits
                scope.launch {
                    when (val result = check(entered)) {
                        PinResult.Ok -> onSuccess()
                        is PinResult.Wrong -> {
                            message = resources.getQuantityString(R.plurals.plurals_pin_tries_left, result.triesLeft, result.triesLeft)
                            digits = ""
                        }
                        is PinResult.Locked -> {
                            val seconds = ceil(result.waitMillis / 1000.0).toInt()
                            message = resources.getQuantityString(R.plurals.plurals_pin_wait, seconds, seconds)
                            digits = ""
                        }
                    }
                }
            }
        },
        onBackspace = { digits = digits.dropLast(1) },
        onDismiss = onDismiss,
    )
}

/** Choosing a new PIN: enter it, then enter it again (Settings). */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.pin_mismatch)
    PinKeypadDialog(
        title = stringResource(if (first == null) R.string.pin_set_title else R.string.pin_confirm_title),
        subtitle = stringResource(R.string.pin_set_hint),
        digits = digits,
        message = message,
        onDigit = { digit ->
            if (digits.length < Pin.LENGTH) digits += digit
            if (digits.length == Pin.LENGTH) {
                when (first) {
                    null -> {
                        first = digits
                        message = null
                    }
                    digits -> onSet(digits)
                    else -> {
                        first = null
                        message = mismatch
                    }
                }
                digits = ""
            }
        },
        onBackspace = { digits = digits.dropLast(1) },
        onDismiss = onDismiss,
    )
}

/** The PIN check board: lock, title, what the PIN is for, four dots, keypad, Cancel. */
@Composable
private fun PinKeypadDialog(
    title: String,
    subtitle: String,
    digits: String,
    message: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalShopColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surface, modifier = Modifier.widthIn(max = 400.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.greenTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.green)
                }
                Text(title, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colors.ink, textAlign = TextAlign.Center)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(Pin.LENGTH) { index ->
                        val filled = index < digits.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (filled) colors.ink else colors.surface)
                                .border(BorderStroke(1.5.dp, if (filled) colors.ink else colors.muted), CircleShape),
                        )
                    }
                }
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.rust,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(20.dp),
                )
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", BACKSPACE)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "" -> Spacer(modifier = Modifier.weight(1f))
                                BACKSPACE -> KeypadKey(onClick = onBackspace, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.pin_backspace), tint = colors.ink)
                                }
                                else -> KeypadKey(onClick = { onDigit(key) }, modifier = Modifier.weight(1f)) {
                                    Text(key, fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.ink)
                                }
                            }
                        }
                    }
                }
                SecondaryButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun KeypadKey(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = LocalShopColors.current.segmentTrack, modifier = modifier.height(56.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private const val BACKSPACE = "⌫"
