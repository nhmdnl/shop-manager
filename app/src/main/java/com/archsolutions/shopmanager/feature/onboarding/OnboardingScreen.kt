package com.archsolutions.shopmanager.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DarkShopColors
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LightShopColors
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.ShopColors
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.locale.LanguageManager
import com.archsolutions.shopmanager.feature.stock.FieldLabel

/** First run (7.8, design boards "First run 1–3"); full screen on every layout. */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    // The close-day reminder needs this on Android 13+; asking here, when the first day starts, explains why.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.startDay() }
    val startDay = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.startDay()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                if (state.step > 1) {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                }
                StepDots(step = state.step)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.step_of, state.step, OnboardingViewModel.LAST_STEP),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.step) {
                    1 -> ShopAndLanguageStep(state, viewModel)
                    2 -> ThemeStep(state, viewModel)
                    else -> OpeningMoneyStep(state, viewModel)
                }
            }
            if (state.step < OnboardingViewModel.LAST_STEP) {
                PrimaryButton(
                    text = stringResource(R.string.action_next),
                    onClick = viewModel::next,
                    enabled = state.canContinue,
                    leadingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.action_start_day),
                    onClick = startDay,
                    enabled = !state.starting && !state.importing,
                    leadingIcon = Icons.Default.Check,
                )
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    val colors = LocalShopColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 4.dp)) {
        repeat(OnboardingViewModel.LAST_STEP) { index ->
            val current = index + 1 == step
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (current) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (index + 1 <= step) colors.green else colors.line),
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    val colors = LocalShopColors.current
    Text(title, fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, color = colors.ink)
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = colors.muted)
}

@Composable
private fun ShopAndLanguageStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val colors = LocalShopColors.current
    StepTitle(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_sub))
    Spacer(modifier = Modifier.height(4.dp))
    FieldLabel(stringResource(R.string.settings_shop_name))
    ShopTextField(
        value = state.shopName,
        onValueChange = viewModel::setShopName,
        placeholder = stringResource(R.string.shop_name_placeholder),
        capitalization = KeyboardCapitalization.Words,
    )
    Spacer(modifier = Modifier.height(4.dp))
    SectionLabel(text = stringResource(R.string.label_language))
    val glosses = mapOf(
        "sw" to stringResource(R.string.gloss_swahili),
        "ti" to stringResource(R.string.gloss_tigrinya),
        "pt" to stringResource(R.string.gloss_portuguese),
    )
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
        Column {
            LanguageManager.supportedLanguages.forEachIndexed { index, (code, name) ->
                if (index > 0) HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setLanguage(code) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.language == code,
                        onClick = { viewModel.setLanguage(code) },
                        colors = RadioButtonDefaults.colors(selectedColor = colors.green),
                    )
                    // Names stay in their own language (endonyms) so anyone can find theirs.
                    Text(name.substringBefore(" ("), style = MaterialTheme.typography.bodyLarge, color = colors.ink, modifier = Modifier.weight(1f))
                    glosses[code]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted, modifier = Modifier.padding(end = 12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ThemeStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepTitle(stringResource(R.string.onboarding_theme_title), stringResource(R.string.onboarding_theme_sub))
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(ThemeMode.LIGHT, R.string.theme_light, LightShopColors),
            Triple(ThemeMode.DARK, R.string.theme_dark, DarkShopColors),
            Triple(ThemeMode.SYSTEM, R.string.theme_system, null),
        ).forEach { (mode, label, preview) ->
            ThemeTile(
                label = stringResource(label),
                preview = preview,
                selected = state.theme == mode,
                onClick = { viewModel.setTheme(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A tiny screen drawn in that theme's colours; System shows a light card on the paper tone. */
@Composable
private fun ThemeTile(label: String, preview: ShopColors?, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalShopColors.current
    val shown = preview ?: LightShopColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.green else colors.line),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (preview == null) shown.paper else shown.surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PreviewBar(shown.ink, widthFraction = 0.45f, height = 6.dp)
                PreviewBar(shown.green, widthFraction = 1f, height = 18.dp)
                PreviewBar(if (preview == null) shown.surface else shown.segmentTrack, widthFraction = 1f, height = 14.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = colors.green))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PreviewBar(color: Color, widthFraction: Float, height: Dp) {
    Box(modifier = Modifier.fillMaxWidth(widthFraction).height(height).clip(RoundedCornerShape(4.dp)).background(color))
}

@Composable
private fun OpeningMoneyStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    val colors = LocalShopColors.current
    val chooseCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importProducts)
    }
    StepTitle(stringResource(R.string.onboarding_money_title), stringResource(R.string.onboarding_money_sub))
    Spacer(modifier = Modifier.height(4.dp))
    FieldLabel(stringResource(R.string.label_cash_in_drawer))
    MoneyField(value = state.openingCash, onValueChange = viewModel::setOpeningCash)
    FieldLabel(stringResource(R.string.label_momo_balance))
    MoneyField(value = state.openingMobile, onValueChange = viewModel::setOpeningMobile)
    Spacer(modifier = Modifier.height(4.dp))
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.segmentTrack), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = colors.muted)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
                    Text(stringResource(R.string.import_sub), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
            SecondaryButton(
                text = stringResource(R.string.import_choose),
                onClick = { chooseCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
                enabled = !state.importing,
                leadingIcon = Icons.Outlined.Description,
                modifier = Modifier.fillMaxWidth(),
            )
            ImportOutcome(state)
        }
    }
}

@Composable
private fun ImportOutcome(state: OnboardingUiState) {
    val colors = LocalShopColors.current
    val text = when {
        state.importFailed -> stringResource(R.string.import_failed)
        state.imported != null -> listOfNotNull(
            pluralStringResource(R.plurals.plurals_imported, state.imported.added, state.imported.added),
            state.imported.skipped.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plurals_skipped, it, it) },
        ).joinToString(" · ")
        else -> null
    }
    text?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = if (state.importFailed) colors.rust else colors.green, fontWeight = FontWeight.SemiBold)
    }
}
