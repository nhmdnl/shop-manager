package com.archsolutions.shopmanager.feature.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.fontScaled
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.domain.staff.Role
import com.archsolutions.shopmanager.feature.pin.PinEntry
import com.archsolutions.shopmanager.feature.pin.PinPad
import com.archsolutions.shopmanager.feature.pin.pinFailureMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SHOWN_CODE_MILLIS = 120_000L

/** From this width the names sit beside the PIN pad; narrower, the names sit above it in one column. */
private val WIDE_FROM = 600.dp

/**
 * Full-screen sign-in on one screen: tap who is working, then type their PIN. Leaving it is the session's job, not this
 * screen's.
 */
@Composable
fun StaffSignInScreen(viewModel: StaffSignInViewModel = hiltViewModel(), modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    val people by viewModel.people.collectAsStateWithLifecycle()
    val pickedId by viewModel.picked.collectAsStateWithLifecycle()
    val picked = people.firstOrNull { it.id == pickedId }
    val owners = people.filter { it.role == Role.OWNER.key }
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val entry = remember(pickedId) { PinEntry() }
    var message by remember(pickedId) { mutableStateOf<String?>(null) }
    var recovering by remember { mutableStateOf(false) }
    val shownCode by viewModel.shownCode.collectAsStateWithLifecycle()
    val onDigit: (String) -> Unit = { digit ->
        entry.digit(digit)?.let { entered ->
            scope.launch {
                var failed = false
                try {
                    val result: PinResult = viewModel.enter(entered)
                    if (result != PinResult.Ok) {
                        failed = true
                        message = pinFailureMessage(resources, result)
                    }
                } finally {
                    entry.finish(clear = failed)
                }
            }
        }
    }

    // Edge-to-edge app: keep clear of the status and navigation bars, as the other full-screen screens do.
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.paper).statusBarsPadding().navigationBarsPadding()) {
        // Read here: inside the Row the layout scope hides this scope's maxHeight.
        val screenHeight = maxHeight
        if (maxWidth >= WIDE_FROM) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Title, names and the forgot button sit as one block, centred on the screen height. The names wrap
                // their content and scroll inside when there are many people.
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    SignInTitle(textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    StaffNameGrid(
                        people = people,
                        pickedId = pickedId,
                        columns = GridCells.Adaptive(160.dp),
                        onPick = viewModel::pick,
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    )
                    if (owners.isNotEmpty()) ForgotPinButton(onClick = { recovering = true })
                }
                // The pad, centred on the screen height. Its minimum height is the pane's, so the centring holds; a
                // large font makes it taller than that and it scrolls.
                Column(
                    modifier = Modifier
                        .widthIn(min = 360.dp, max = 400.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .heightIn(min = (screenHeight - 48.dp).coerceAtLeast(0.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    SignInPad(
                        picked = picked,
                        digits = entry.digits,
                        message = message,
                        onDigit = onDigit,
                        onBackspace = entry::backspace,
                        showIcon = true,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 420.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SignInTitle(textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                // One row and a sliver of the next; more people scroll inside the grid, so the pad stays on screen.
                StaffNameGrid(
                    people = people,
                    pickedId = pickedId,
                    columns = GridCells.Fixed(2),
                    onPick = viewModel::pick,
                    modifier = Modifier.fillMaxWidth().heightIn(max = fontScaled(140.dp)),
                )
                SignInPad(
                    picked = picked,
                    digits = entry.digits,
                    message = message,
                    onDigit = onDigit,
                    onBackspace = entry::backspace,
                    showIcon = false,
                )
                if (owners.isNotEmpty()) ForgotPinButton(onClick = { recovering = true })
            }
        }
    }

    if (recovering) {
        OwnerRecoveryFlow(
            owners = owners,
            recover = viewModel::recover,
            onClose = { recovering = false },
        )
    }
    // Nobody is signed in here, so the new code must not wait for the next person: it goes when the app leaves the
    // foreground (not on a rotation or font-scale change, which recreate the Activity while the owner is still
    // writing it down), or after two minutes.
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && context.findActivity()?.isChangingConfigurations != true) {
                viewModel.codeWritten()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(shownCode) {
        if (shownCode != null) {
            delay(SHOWN_CODE_MILLIS)
            viewModel.codeWritten()
        }
    }
    shownCode?.let { code -> RecoveryCodeDialog(code = code, onWritten = viewModel::codeWritten) }
}

/**
 * "Forgot the Owner PIN": a text link under the names, not a full-width button. It wraps its text and sits where its
 * column puts it (left in the wide layout, centred in the narrow one).
 */
@Composable
private fun ForgotPinButton(onClick: () -> Unit) {
    val colors = LocalShopColors.current
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = colors.ink),
    ) {
        Text(
            stringResource(R.string.staff_forgot_pin),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
    }
}

@Composable
private fun SignInTitle(textAlign: TextAlign, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.staff_signin_title),
        modifier = modifier,
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = LocalShopColors.current.ink,
        textAlign = textAlign,
    )
}

@Composable
private fun StaffNameGrid(
    people: List<StaffEntity>,
    pickedId: Long?,
    columns: GridCells,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(people, key = { it.id }) { person ->
            StaffTile(
                name = person.name,
                roleLabel = roleLabel(person.role),
                selected = person.id == pickedId,
                onClick = { onPick(person.id) },
            )
        }
    }
}

/** The pad for [picked]; with nobody picked its keys are greyed out and the title asks for a name first. */
@Composable
private fun SignInPad(
    picked: StaffEntity?,
    digits: String,
    message: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    showIcon: Boolean,
) {
    PinPad(
        title = picked?.name ?: stringResource(R.string.staff_signin_pick_first),
        subtitle = if (picked != null) stringResource(R.string.staff_signin_pin_hint) else "",
        digits = digits,
        message = message,
        onDigit = onDigit,
        onBackspace = onBackspace,
        enabled = picked != null,
        keyHeight = 64.dp,
        showIcon = showIcon,
    )
}

@Composable
private fun roleLabel(roleKey: String): String = when (Role.fromKey(roleKey)) {
    Role.OWNER -> stringResource(R.string.staff_role_owner)
    Role.CASHIER -> stringResource(R.string.staff_role_cashier)
    null -> ""
}
