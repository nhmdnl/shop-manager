package com.archsolutions.shopmanager.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.SegmentedControl
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopSwitch
import com.archsolutions.shopmanager.core.locale.LanguageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isExpanded: Boolean,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = colors.ink,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        if (isExpanded) {
            // Tablet Two-Column Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 28.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Left Column: Appearance & Language
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ThemeSettingsCard(
                        currentTheme = settings.theme,
                        onThemeSelected = viewModel::setTheme,
                        isExpanded = true,
                    )
                    LanguageSettingsCard(
                        currentLanguage = settings.language,
                        onLanguageSelected = viewModel::setLanguage,
                    )
                }

                // Right Column: Daily Balance & Shop Data
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    DailyBalanceSettingsCard(
                        openDayOnStart = settings.openDayOnStart,
                        onOpenDayChange = viewModel::setOpenDayOnStart,
                        carryClosing = settings.carryClosingToOpening,
                        onCarryClosingChange = viewModel::setCarryClosingToOpening,
                        allowedDiff = settings.allowedDifference,
                        onAllowedDiffChange = viewModel::setAllowedDifference,
                    )
                    ShopDataSettingsCard(
                        shopName = settings.shopName,
                        currency = settings.currencyCode,
                    )
                }
            }
        } else {
            // Phone Stacked Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ThemeSettingsCard(
                    currentTheme = settings.theme,
                    onThemeSelected = viewModel::setTheme,
                    isExpanded = false,
                )
                LanguageSettingsCard(
                    currentLanguage = settings.language,
                    onLanguageSelected = viewModel::setLanguage,
                )
                DailyBalanceSettingsCard(
                    openDayOnStart = settings.openDayOnStart,
                    onOpenDayChange = viewModel::setOpenDayOnStart,
                    carryClosing = settings.carryClosingToOpening,
                    onCarryClosingChange = viewModel::setCarryClosingToOpening,
                    allowedDiff = settings.allowedDifference,
                    onAllowedDiffChange = viewModel::setAllowedDifference,
                )
                ShopDataSettingsCard(
                    shopName = settings.shopName,
                    currency = settings.currencyCode,
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsCard(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    isExpanded: Boolean,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(text = stringResource(R.string.label_theme))

            if (isExpanded) {
                // Tablet preview tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = mode == currentTheme
                        val title = when (mode) {
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) colors.greenTint else colors.segmentTrack)
                                .border(
                                    BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) colors.green else colors.line,
                                    ),
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { onThemeSelected(mode) }
                                .padding(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.ink,
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onThemeSelected(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colors.green,
                                        unselectedColor = colors.muted,
                                    ),
                                )
                            }
                        }
                    }
                }
            } else {
                // Phone segmented control
                SegmentedControl(
                    items = ThemeMode.entries,
                    selectedItem = currentTheme,
                    onItemSelected = onThemeSelected,
                    labelProvider = { mode ->
                        when (mode) {
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.SYSTEM -> "System"
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageSettingsCard(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(text = stringResource(R.string.label_language))

            LanguageManager.supportedLanguages.forEach { (code, name) ->
                val isSelected = code == currentLanguage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onLanguageSelected(code) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.ink,
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = { onLanguageSelected(code) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.green,
                            unselectedColor = colors.muted,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_amounts_note),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}

@Composable
private fun DailyBalanceSettingsCard(
    openDayOnStart: Boolean,
    onOpenDayChange: (Boolean) -> Unit,
    carryClosing: Boolean,
    onCarryClosingChange: (Boolean) -> Unit,
    allowedDiff: Long,
    onAllowedDiffChange: (Long) -> Unit,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionLabel(text = stringResource(R.string.settings_daily_balance))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_open_day_on_start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                ShopSwitch(
                    checked = openDayOnStart,
                    onCheckedChange = onOpenDayChange,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_carry_closing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                ShopSwitch(
                    checked = carryClosing,
                    onCheckedChange = onCarryClosingChange,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.settings_allowed_difference),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
                MoneyField(
                    value = allowedDiff,
                    onValueChange = { onAllowedDiffChange(it ?: 0L) },
                )
            }
        }
    }
}

@Composable
private fun ShopDataSettingsCard(
    shopName: String,
    currency: String,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(text = stringResource(R.string.settings_shop_and_data))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_shop_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
                Text(
                    text = "$currency (whole shillings)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                )
            }

            SecondaryButton(
                text = stringResource(R.string.settings_backup_now),
                onClick = {},
                leadingIcon = Icons.Outlined.CloudUpload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
