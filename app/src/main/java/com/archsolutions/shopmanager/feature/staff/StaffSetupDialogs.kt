package com.archsolutions.shopmanager.feature.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.CappedFontScale
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.security.RecoveryCode
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.staff.RecoveryOutcome
import com.archsolutions.shopmanager.feature.pin.PinCheckDialog
import com.archsolutions.shopmanager.feature.pin.SetPinDialog
import com.archsolutions.shopmanager.feature.pin.pinFailureMessage
import kotlinx.coroutines.launch

private const val MAX_NAME = 40
private const val CODE_CHARS = 8

/**
 * The recovery code, shown until the person confirms they wrote it down. It cannot be dismissed any other way (back
 * and outside taps do nothing); a sign-out discards it through the caller. Never saved, never logged.
 */
@Composable
fun RecoveryCodeDialog(code: String, onWritten: () -> Unit) {
    val colors = LocalShopColors.current
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surface, modifier = Modifier.widthIn(max = 400.dp)) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.staff_code_title),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.staff_code_body), style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
                CappedFontScale {
                    Text(
                        code,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = colors.ink,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
                PrimaryButton(text = stringResource(R.string.staff_code_written), onClick = onWritten, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A name box in a dialog; the confirm button stays off until there is a name (blank and spaces-only are refused). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    error: String? = null,
    extra: @Composable () -> Unit = {},
) {
    var name by remember { mutableStateOf(initial) }
    val blank = name.isNotEmpty() && name.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                ShopTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    placeholder = stringResource(R.string.staff_name_placeholder),
                    capitalization = KeyboardCapitalization.Words,
                )
                val message = if (blank) stringResource(R.string.staff_name_blank) else error
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = LocalShopColors.current.rust)
                extra()
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Turning staff on: the shared PIN again when there is one (it becomes the Owner PIN), else a new PIN; then the
 * Owner's name; then [StaffViewModel.setUp]. The recovery code is shown by the caller from [StaffViewModel.shownCode].
 * Everything typed lives only in this composable, so closing it (including on sign-out) forgets it.
 */
@Composable
fun StaffSetupFlow(sharedPinSet: Boolean, viewModel: StaffViewModel, onClose: () -> Unit) {
    var ownerPin by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OnSignOut {
        ownerPin = null
        typed = null
        onClose()
    }

    val pin = ownerPin
    if (pin == null) {
        if (sharedPinSet) {
            PinCheckDialog(
                title = stringResource(R.string.staff_setup_confirm_title),
                purpose = stringResource(R.string.staff_setup_confirm_hint),
                check = { entered ->
                    val result = viewModel.checkSharedPin(entered)
                    if (result == PinResult.Ok) typed = entered
                    result
                },
                onDismiss = onClose,
                onSuccess = {
                    ownerPin = typed
                    typed = null
                },
            )
        } else {
            SetPinDialog(onDismiss = onClose, onSet = { ownerPin = it }, hint = stringResource(R.string.pin_staff_hint))
        }
    } else {
        NameDialog(
            title = stringResource(R.string.staff_setup_name_title),
            initial = "",
            confirmText = stringResource(R.string.action_next),
            onDismiss = {
                ownerPin = null
                onClose()
            },
            error = if (failed) stringResource(R.string.staff_setup_failed) else null,
            onConfirm = { name ->
                if (busy) return@NameDialog
                busy = true
                scope.launch {
                    try {
                        viewModel.setUp(name, pin)
                        ownerPin = null
                        onClose()
                    } catch (_: IllegalArgumentException) {
                        failed = true
                        busy = false
                    } catch (_: IllegalStateException) {
                        failed = true
                        busy = false
                    }
                }
            },
        )
    }
}

/**
 * "Forgot the Owner PIN": which Owner (when there is more than one), the recovery code, a new PIN. A right code ends
 * with [recover] returning Ok, after which the caller shows the new code. A wrong or locked try goes back to the code.
 */
@Composable
fun OwnerRecoveryFlow(
    owners: List<StaffEntity>,
    recover: suspend (ownerId: Long, code: String, newPin: String) -> RecoveryOutcome,
    onClose: () -> Unit,
) {
    var ownerId by remember { mutableStateOf(owners.singleOrNull()?.id) }
    var code by remember { mutableStateOf("") }
    var choosingPin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val wrongCode = stringResource(R.string.staff_recovery_wrong)
    OnSignOut {
        code = ""
        onClose()
    }

    val id = ownerId
    when {
        id == null -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.staff_recovery_pick)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    owners.forEach { owner ->
                        SecondaryButton(text = owner.name, onClick = { ownerId = owner.id }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
        )
        !choosingPin -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.staff_recovery_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.staff_recovery_code_prompt), style = MaterialTheme.typography.bodyMedium)
                    ShopTextField(
                        value = code,
                        onValueChange = { code = it.filter { c -> c.isLetterOrDigit() || c == '-' }.take(CODE_CHARS + 1).uppercase() },
                        placeholder = stringResource(R.string.staff_recovery_code_placeholder),
                        capitalization = KeyboardCapitalization.Characters,
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalShopColors.current.rust) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        message = null
                        choosingPin = true
                    },
                    enabled = RecoveryCode.normalize(code).length == CODE_CHARS,
                ) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) } },
        )
        else -> SetPinDialog(
            onDismiss = { choosingPin = false },
            hint = stringResource(R.string.pin_staff_hint),
            onSet = { newPin ->
                scope.launch {
                    when (val outcome = recover(id, RecoveryCode.normalize(code), newPin)) {
                        is RecoveryOutcome.Ok -> {
                            code = ""
                            onClose()
                        }
                        RecoveryOutcome.WrongCode -> {
                            message = wrongCode
                            choosingPin = false
                        }
                        is RecoveryOutcome.Locked -> {
                            message = pinFailureMessage(resources, PinResult.Locked(outcome.waitMillis))
                            choosingPin = false
                        }
                    }
                }
            },
        )
    }
}
