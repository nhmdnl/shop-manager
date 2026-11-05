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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.LocalUserActivity
import com.archsolutions.shopmanager.core.designsystem.fontScaled
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.security.Pin
import com.archsolutions.shopmanager.data.repo.StaffRepo
import com.archsolutions.shopmanager.data.settings.PinGuard
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.data.settings.SettingsStore
import com.archsolutions.shopmanager.data.staff.StaffGuard
import com.archsolutions.shopmanager.data.staff.StaffSession
import com.archsolutions.shopmanager.feature.staff.LocalSignOutTick
import com.archsolutions.shopmanager.feature.staff.OnSignOut
import com.archsolutions.shopmanager.domain.staff.GateDecision
import com.archsolutions.shopmanager.domain.staff.GateInputs
import com.archsolutions.shopmanager.domain.staff.decide
import com.archsolutions.shopmanager.domain.staff.permissionFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

@HiltViewModel
class PinViewModel @Inject constructor(
    private val pinGuard: PinGuard,
    private val staffSession: StaffSession,
    private val staffGuard: StaffGuard,
    settingsStore: SettingsStore,
    staffRepo: StaffRepo,
) : ViewModel() {
    /** Null until the first real emission; an unknown state must never decide RUN. */
    internal val gateState: StateFlow<GateInputs?> = gateInputsFlow(settingsStore.settings, staffSession.current, staffRepo.staffActive)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun check(pin: String): PinResult = pinGuard.check(pin)

    suspend fun checkOwner(pin: String): PinResult = staffGuard.approveAsOwner(pin)

    /** Runs [action] with the approving Owner (if any) visible as [StaffSession.actingId], then clears it. */
    fun runApproved(ownerApproved: Boolean, action: () -> Unit) {
        // An action that reads actingId must do so synchronously, before launching a coroutine (ruling R1).
        staffSession.approvedBy = if (ownerApproved) staffGuard.lastApproverId else null
        try {
            action()
        } finally {
            staffSession.approvedBy = null
        }
    }
}

/** Asks for the PIN before an action: the shared PIN when staff is off (addendum C4), an Owner's PIN when the signed-in role can't do it; otherwise runs it straight away. */
@Stable
class PinGate internal constructor(
    private val state: StateFlow<GateInputs?>,
    private val scope: CoroutineScope,
    private val runApproved: (Boolean, () -> Unit) -> Unit,
    private val signOutTick: () -> Int = { 0 },
) {
    internal var request by mutableStateOf<PinRequest?>(null)

    fun run(@StringRes purpose: Int, action: () -> Unit) {
        scope.launch {
            val tickAtStart = signOutTick()
            val decision = state.decide(permissionFor(purpose))
            // Somebody signed out while this waited for the first real state: it was asked for by someone no longer here.
            if (signOutTick() != tickAtStart) return@launch
            when (decision) {
                GateDecision.RUN -> runApproved(false, action)
                GateDecision.ASK_SHARED_PIN -> request = PinRequest(purpose, ownerPin = false, action)
                GateDecision.ASK_OWNER_PIN -> request = PinRequest(purpose, ownerPin = true, action)
            }
        }
    }
}

internal data class PinRequest(@StringRes val purpose: Int, val ownerPin: Boolean, val onSuccess: () -> Unit)

/** A [PinGate] for this screen; the PIN dialog shows here while a request is waiting. */
@Composable
fun rememberPinGate(viewModel: PinViewModel = hiltViewModel()): PinGate {
    val scope = rememberCoroutineScope()
    val tick by rememberUpdatedState(LocalSignOutTick.current)
    val gate = remember(viewModel, scope) { PinGate(viewModel.gateState, scope, viewModel::runApproved) { tick } }
    OnSignOut { gate.request = null }
    gate.request?.let { request ->
        PinCheckDialog(
            title = stringResource(if (request.ownerPin) R.string.pin_owner_needed else R.string.pin_enter),
            purpose = stringResource(request.purpose),
            check = if (request.ownerPin) viewModel::checkOwner else viewModel::check,
            onDismiss = { gate.request = null },
            onSuccess = {
                gate.request = null
                viewModel.runApproved(request.ownerPin, request.onSuccess)
            },
        )
    }
    return gate
}

/** One PIN check on a keypad dialog; [check] decides. Reused by the staff setup's shared-PIN confirm step. */
@Composable
internal fun PinCheckDialog(title: String, purpose: String, check: suspend (String) -> PinResult, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val entry = remember { PinEntry() }
    var message by remember { mutableStateOf<String?>(null) }
    PinKeypadDialog(
        title = title,
        subtitle = purpose,
        digits = entry.digits,
        message = message,
        onDigit = { digit ->
            entry.digit(digit)?.let { entered ->
                scope.launch {
                    var failed = false
                    try {
                        val result = check(entered)
                        if (result == PinResult.Ok) {
                            onSuccess()
                        } else {
                            failed = true
                            message = pinFailureMessage(resources, result)
                        }
                    } finally {
                        entry.finish(clear = failed)
                    }
                }
            }
        },
        onBackspace = entry::backspace,
        onDismiss = onDismiss,
    )
}

/** Choosing a new PIN: enter it, then enter it again (Settings). */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit, hint: String = stringResource(R.string.pin_set_hint)) {
    var first by remember { mutableStateOf<String?>(null) }
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.pin_mismatch)
    PinKeypadDialog(
        title = stringResource(if (first == null) R.string.pin_set_title else R.string.pin_confirm_title),
        subtitle = hint,
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
                PinPad(title, subtitle, digits, message, onDigit, onBackspace)
                SecondaryButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The PIN wording for a failed try: tries left, or how long to wait. */
internal fun pinFailureMessage(resources: android.content.res.Resources, result: PinResult): String? = when (result) {
    PinResult.Ok -> null
    is PinResult.Wrong -> resources.getQuantityString(R.plurals.plurals_pin_tries_left, result.triesLeft, result.triesLeft)
    is PinResult.Locked -> {
        val seconds = ceil(result.waitMillis / 1000.0).toInt()
        resources.getQuantityString(R.plurals.plurals_pin_wait, seconds, seconds)
    }
}

@Composable
internal fun PinPad(
    title: String,
    subtitle: String,
    digits: String,
    message: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val colors = LocalShopColors.current
    val activity = LocalUserActivity.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            modifier = Modifier.height(fontScaled(20.dp)),
        )
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", BACKSPACE)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.weight(1f))
                        BACKSPACE -> KeypadKey(onClick = { activity(); onBackspace() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.pin_backspace), tint = colors.ink)
                        }
                        else -> KeypadKey(onClick = { activity(); onDigit(key) }, modifier = Modifier.weight(1f)) {
                            Text(key, fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = LocalShopColors.current.segmentTrack, modifier = modifier.height(fontScaled(56.dp))) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private const val BACKSPACE = "⌫"
