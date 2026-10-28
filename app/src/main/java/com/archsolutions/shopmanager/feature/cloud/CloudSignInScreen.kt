package com.archsolutions.shopmanager.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.BuildConfig
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField

/** Sign in with an SMS code and link this tablet. [onDone] goes back; [onLinked] runs once the tablet is linked (default: go back). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSignInScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onLinked: () -> Unit = onDone,
    viewModel: CloudSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current

    LaunchedEffect(state.step, state.left) {
        if (state.step == SignInStep.DONE) onLinked() else if (state.left) onDone()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.cloud_signin_title),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = colors.ink,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                SignInStep.PHONE -> PhoneStep(state, viewModel)
                SignInStep.CODE -> CodeStep(state, viewModel)
                SignInStep.PROFILE -> ProfileStep(state, viewModel)
                SignInStep.REPLACE -> ReplaceStep(state, viewModel)
                SignInStep.DONE -> Unit
            }
            state.error?.let { ErrorText(it, state.retryMinutes) }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = LocalShopColors.current.ink,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = LocalShopColors.current.muted)
}

@Composable
private fun ErrorText(error: SignInError, minutes: Int) {
    val text = when (error) {
        SignInError.PHONE -> stringResource(R.string.cloud_error_phone)
        SignInError.CODE_WRONG -> stringResource(R.string.cloud_error_code_wrong)
        SignInError.CODE_EXPIRED -> stringResource(R.string.cloud_error_code_expired)
        SignInError.TOO_MANY -> stringResource(R.string.cloud_error_too_many, minutes)
        SignInError.OFFLINE -> stringResource(R.string.cloud_error_offline)
        SignInError.OTHER -> stringResource(R.string.cloud_error_other)
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PhoneStep(state: SignInUiState, vm: CloudSignInViewModel) {
    Title(stringResource(R.string.cloud_phone_title))
    Hint(stringResource(R.string.cloud_phone_hint))
    ShopTextField(
        value = state.phone,
        onValueChange = vm::setPhone,
        placeholder = stringResource(R.string.cloud_phone_placeholder),
        keyboardType = KeyboardType.Phone,
    )
    PrimaryButton(
        text = stringResource(R.string.cloud_send_code),
        onClick = vm::sendCode,
        enabled = state.phone.isNotEmpty() && !state.busy,
    )
}

@Composable
private fun CodeStep(state: SignInUiState, vm: CloudSignInViewModel) {
    Title(stringResource(R.string.cloud_code_title))
    Hint(stringResource(R.string.cloud_code_hint, state.phone))
    ShopTextField(
        value = state.code,
        onValueChange = vm::setCode,
        placeholder = stringResource(R.string.cloud_code_placeholder),
        keyboardType = KeyboardType.Number,
    )
    PrimaryButton(
        text = stringResource(R.string.cloud_confirm_code),
        onClick = vm::verify,
        enabled = state.code.isNotEmpty() && !state.busy,
    )
    SecondaryButton(
        text = stringResource(R.string.cloud_resend),
        onClick = vm::sendCode,
        enabled = state.canResend && !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileStep(state: SignInUiState, vm: CloudSignInViewModel) {
    val colors = LocalShopColors.current
    val uriHandler = LocalUriHandler.current
    Title(stringResource(R.string.cloud_profile_title))
    ShopTextField(
        value = state.name,
        onValueChange = vm::setName,
        placeholder = stringResource(R.string.cloud_name_placeholder),
        capitalization = KeyboardCapitalization.Words,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.consent,
            onCheckedChange = vm::setConsent,
            colors = CheckboxDefaults.colors(checkedColor = colors.green),
        )
        Text(
            text = stringResource(R.string.cloud_consent),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { uriHandler.openUri("${BuildConfig.CLOUD_BASE_URL}/privacy") },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.cloud_privacy_link)) }
        TextButton(
            onClick = { uriHandler.openUri("${BuildConfig.CLOUD_BASE_URL}/terms") },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.cloud_terms_link)) }
    }
    PrimaryButton(
        text = stringResource(R.string.cloud_link_tablet),
        onClick = vm::submitProfile,
        enabled = state.consent && !state.busy,
    )
}

@Composable
private fun ReplaceStep(state: SignInUiState, vm: CloudSignInViewModel) {
    Title(stringResource(R.string.cloud_replace_title))
    Hint(stringResource(R.string.cloud_replace_body))
    PrimaryButton(
        text = stringResource(R.string.cloud_replace_yes),
        onClick = vm::replaceOldTablet,
        enabled = !state.busy,
    )
    SecondaryButton(
        text = stringResource(R.string.cloud_replace_no),
        onClick = vm::keepOldTablet,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}
