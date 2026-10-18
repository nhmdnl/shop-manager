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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.app.parseReminderTime
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.ThemeMode
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.SegmentedControl
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopSwitch
import com.archsolutions.shopmanager.core.locale.LanguageManager
import java.util.Locale
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalConfiguration
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.settings.ShopSettings
import com.archsolutions.shopmanager.feature.backup.rememberBackupActions
import com.archsolutions.shopmanager.feature.pin.SetPinDialog
import com.archsolutions.shopmanager.feature.pin.rememberPinGate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        reminderTime = settings.closeReminderTime,
                        onReminderTimeChange = viewModel::setCloseReminderTime,
                        carryClosing = settings.carryClosingToOpening,
                        onCarryClosingChange = viewModel::setCarryClosingToOpening,
                        allowedDiff = settings.allowedDifference,
                        onAllowedDiffChange = viewModel::setAllowedDifference,
                    )
                    ShopDataSettingsCard(
                        settings = settings,
                        zoneId = viewModel.zoneId,
                        snackbarHostState = snackbarHostState,
                        onShopNameChange = viewModel::setShopName,
                        onPinSet = viewModel::setPin,
                        onPinOff = viewModel::disablePin,
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
                    reminderTime = settings.closeReminderTime,
                    onReminderTimeChange = viewModel::setCloseReminderTime,
                    carryClosing = settings.carryClosingToOpening,
                    onCarryClosingChange = viewModel::setCarryClosingToOpening,
                    allowedDiff = settings.allowedDifference,
                    onAllowedDiffChange = viewModel::setAllowedDifference,
                )
                ShopDataSettingsCard(
                    settings = settings,
                    zoneId = viewModel.zoneId,
                    snackbarHostState = snackbarHostState,
                    onShopNameChange = viewModel::setShopName,
                    onPinSet = viewModel::setPin,
                    onPinOff = viewModel::disablePin,
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
    reminderTime: String,
    onReminderTimeChange: (String) -> Unit,
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

            ReminderTimeRow(time = reminderTime, onTimeChange = onReminderTimeChange)

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

// 6.7: the close-day reminder time; MainActivity re-plans the reminder when it changes.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeRow(time: String, onTimeChange: (String) -> Unit) {
    val colors = LocalShopColors.current
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_remind_close_day),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = { picking = true },
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.line),
        ) {
            Text(
                text = time,
                style = amountTextStyle(16.sp),
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
    if (picking) {
        val initial = remember(time) { parseReminderTime(time) }
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.settings_remind_close_day)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange("%02d:%02d".format(Locale.US, state.hour, state.minute))
                        picking = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ShopDataSettingsCard(
    settings: ShopSettings,
    zoneId: ZoneId,
    snackbarHostState: SnackbarHostState,
    onShopNameChange: (String) -> Unit,
    onPinSet: (String) -> Unit,
    onPinOff: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val pinGate = rememberPinGate()
    val backup = rememberBackupActions(snackbarHostState)
    var editingName by remember { mutableStateOf(false) }
    var choosingPin by remember { mutableStateOf(false) }
    var confirmingRestore by remember { mutableStateOf(false) }
    val lastBackup = settings.lastBackupAt?.let { at ->
        val time = DayFormat.time(at, zoneId)
        val sameDay = Instant.ofEpochMilli(at).atZone(zoneId).toLocalDate() == LocalDate.now(zoneId)
        val whenText = if (sameDay) stringResource(R.string.backup_today, time) else "${DayFormat.dateOf(at, zoneId, "d MMM", locale)} $time"
        stringResource(R.string.settings_last_backup, whenText)
    } ?: stringResource(R.string.settings_no_backup)

    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel(text = stringResource(R.string.settings_shop_and_data))
            SettingRow(title = stringResource(R.string.settings_shop_name)) {
                RowChip(text = settings.shopName, onClick = { editingName = true })
            }
            SettingRow(title = stringResource(R.string.settings_currency), subtitle = stringResource(R.string.settings_currency_value, settings.currencyCode)) {
                RowChip(text = stringResource(R.string.settings_fixed_v1), onClick = null)
            }
            SettingRow(title = stringResource(R.string.settings_backup), subtitle = lastBackup) {
                RowChip(text = stringResource(R.string.settings_backup_now), onClick = backup.backUp)
            }
            SettingRow(title = stringResource(R.string.settings_restore), subtitle = stringResource(R.string.settings_restore_desc)) {
                RowChip(text = stringResource(R.string.action_restore), onClick = { pinGate.run(R.string.pin_for_restore) { confirmingRestore = true } })
            }
            SettingRow(title = stringResource(R.string.settings_pin), subtitle = stringResource(R.string.settings_pin_desc)) {
                ShopSwitch(
                    checked = settings.pinEnabled,
                    onCheckedChange = { on -> if (on) choosingPin = true else pinGate.run(R.string.pin_for_settings, onPinOff) },
                )
            }
            if (settings.pinEnabled) {
                SettingRow(title = stringResource(R.string.settings_change_pin)) {
                    RowChip(text = stringResource(R.string.action_change), onClick = { pinGate.run(R.string.pin_for_settings) { choosingPin = true } })
                }
            }
        }
    }

    if (editingName) {
        var name by remember { mutableStateOf(settings.shopName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.settings_shop_name)) },
            text = {
                ShopTextField(value = name, onValueChange = { name = it.take(40) }, placeholder = "", capitalization = KeyboardCapitalization.Words)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onShopNameChange(name.trim())
                        editingName = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmingRestore) {
        AlertDialog(
            onDismissRequest = { confirmingRestore = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingRestore = false
                        backup.restore()
                    },
                ) { Text(stringResource(R.string.restore_choose)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRestore = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (choosingPin) {
        SetPinDialog(
            onDismiss = { choosingPin = false },
            onSet = { pin ->
                choosingPin = false
                onPinSet(pin)
            },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
        trailing()
    }
}

/** The pill buttons on the right of Settings rows; without [onClick] it is a quiet label ("Fixed in v1"). */
@Composable
private fun RowChip(text: String, onClick: (() -> Unit)?) {
    val colors = LocalShopColors.current
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (onClick != null) colors.ink else colors.muted,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(50), color = colors.segmentTrack) { label() }
    } else {
        Surface(shape = RoundedCornerShape(50), color = colors.segmentTrack.copy(alpha = 0.6f)) { label() }
    }
}
