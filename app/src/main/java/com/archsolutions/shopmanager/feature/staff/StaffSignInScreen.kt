package com.archsolutions.shopmanager.feature.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.data.settings.PinResult
import com.archsolutions.shopmanager.domain.staff.Role
import com.archsolutions.shopmanager.feature.pin.PinEntry
import com.archsolutions.shopmanager.feature.pin.PinPad
import com.archsolutions.shopmanager.feature.pin.pinFailureMessage
import kotlinx.coroutines.launch

/** Full-screen sign-in: pick who is working, then type their PIN. Leaving it is the session's job, not this screen's. */
@Composable
fun StaffSignInScreen(viewModel: StaffSignInViewModel = hiltViewModel(), modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    val people by viewModel.people.collectAsStateWithLifecycle()
    val pickedId by viewModel.picked.collectAsStateWithLifecycle()
    val picked = people.firstOrNull { it.id == pickedId }
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val entry = remember(pickedId) { PinEntry() }
    var message by remember(pickedId) { mutableStateOf<String?>(null) }
    var recovering by remember { mutableStateOf(false) }
    val shownCode by viewModel.shownCode.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(colors.paper), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = 360.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (picked == null) {
                Text(
                    stringResource(R.string.staff_signin_title),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                people.forEach { person ->
                    SecondaryButton(text = person.name, onClick = { viewModel.pick(person.id) }, modifier = Modifier.fillMaxWidth())
                }
                val owners = people.filter { it.role == Role.OWNER.key }
                if (owners.isNotEmpty()) {
                    SecondaryButton(
                        text = stringResource(R.string.staff_forgot_pin),
                        onClick = { recovering = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                PinPad(
                    title = picked.name,
                    subtitle = stringResource(R.string.staff_signin_pin_hint),
                    digits = entry.digits,
                    message = message,
                    onDigit = { digit ->
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
                    },
                    onBackspace = entry::backspace,
                )
                SecondaryButton(
                    text = stringResource(R.string.staff_signin_someone_else),
                    onClick = viewModel::back,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (recovering) {
        OwnerRecoveryFlow(
            owners = people.filter { it.role == Role.OWNER.key },
            recover = viewModel::recover,
            onClose = { recovering = false },
        )
    }
    shownCode?.let { code -> RecoveryCodeDialog(code = code, onWritten = viewModel::codeWritten) }
}
