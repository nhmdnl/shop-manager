package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.Badge
import com.archsolutions.shopmanager.core.designsystem.components.BadgeKind
import com.archsolutions.shopmanager.core.designsystem.components.DenominationRow
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.domain.NoteCounter
import kotlinx.coroutines.launch
import kotlin.math.abs

private class OpenDayActions(
    val onCashChange: (Long?) -> Unit,
    val onMobileChange: (Long?) -> Unit,
    val onNoteCountChange: (Long, Int) -> Unit,
    val onCoinsChange: (Long?) -> Unit,
    val onStartDay: () -> Unit,
)

@Composable
fun OpenDayScreen(
    isExpanded: Boolean,
    onDayStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenDayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.error_could_not_save)
    val actions = remember(viewModel) {
        OpenDayActions(
            onCashChange = viewModel::setCash,
            onMobileChange = viewModel::setMobile,
            onNoteCountChange = viewModel::setNoteCount,
            onCoinsChange = viewModel::setCoins,
            onStartDay = viewModel::startDay,
        )
    }

    LaunchedEffect(state.started) {
        if (state.started) onDayStarted()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveError)
            viewModel.saveErrorShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (!state.loaded) return@Scaffold
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        if (isExpanded) {
            OpenDayExpanded(state = state, actions = actions, modifier = contentModifier)
        } else {
            OpenDayCompact(state = state, actions = actions, modifier = contentModifier)
        }
    }
}

// Tablet: form on the left, note counter card on the right.
@Composable
private fun OpenDayExpanded(
    state: OpenDayUiState,
    actions: OpenDayActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = modifier.padding(horizontal = 56.dp, vertical = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OpenDayHeader(
                    title = stringResource(R.string.greeting_morning_shop, state.shopName),
                    subtitle = stringResource(
                        R.string.open_day_subtitle,
                        DayFormat.date(state.date, "EEEE, d MMMM", locale),
                    ),
                    titleSize = 44.sp,
                )
                YesterdayCard(cash = state.yesterdayCash, mobile = state.yesterdayMobile)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OpeningField(
                        label = stringResource(R.string.label_cash_in_drawer),
                        value = state.cash,
                        yesterday = state.yesterdayCash,
                        onValueChange = actions.onCashChange,
                        modifier = Modifier.weight(1f),
                    )
                    OpeningField(
                        label = stringResource(R.string.label_momo_balance),
                        value = state.mobile,
                        yesterday = state.yesterdayMobile,
                        onValueChange = actions.onMobileChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                LockedUntilOpenHint()
            }
            StartDayButton(
                saving = state.saving,
                onClick = actions.onStartDay,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        ShopCard(
            modifier = Modifier
                .width(440.dp)
                .fillMaxHeight(),
            padding = 24.dp,
        ) {
            NoteCounterPanel(
                notes = state.notes,
                coins = state.coins,
                total = state.notesTotal,
                onNoteCountChange = actions.onNoteCountChange,
                onCoinsChange = actions.onCoinsChange,
                fillHeight = true,
            )
        }
    }
}

// Phone: stacked form; the note counter opens in a bottom sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenDayCompact(
    state: OpenDayUiState,
    actions: OpenDayActions,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    var showCounter by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OpenDayHeader(
                title = stringResource(R.string.greeting_morning),
                subtitle = stringResource(
                    R.string.open_day_subtitle,
                    DayFormat.date(state.date, "EEE d MMM", locale),
                ),
                titleSize = 34.sp,
            )
            YesterdayCard(cash = state.yesterdayCash, mobile = state.yesterdayMobile)
            SectionLabel(text = stringResource(R.string.label_count_now))
            OpeningField(
                label = stringResource(R.string.label_cash_in_drawer),
                value = state.cash,
                yesterday = state.yesterdayCash,
                onValueChange = actions.onCashChange,
            )
            OpeningField(
                label = stringResource(R.string.label_momo_balance),
                value = state.mobile,
                yesterday = state.yesterdayMobile,
                onValueChange = actions.onMobileChange,
            )
            TextButton(
                onClick = {
                    // A focused amount field would keep the keyboard up over the sheet and push it up.
                    focusManager.clearFocus()
                    showCounter = true
                },
            ) {
                Text(
                    text = stringResource(R.string.count_notes_one_by_one),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.green,
                )
            }
        }
        StartDayButton(
            saving = state.saving,
            onClick = actions.onStartDay,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }

    if (showCounter) {
        ModalBottomSheet(
            onDismissRequest = { showCounter = false },
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                NoteCounterPanel(
                    notes = state.notes,
                    coins = state.coins,
                    total = state.notesTotal,
                    onNoteCountChange = actions.onNoteCountChange,
                    onCoinsChange = actions.onCoinsChange,
                    fillHeight = false,
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = stringResource(R.string.action_done),
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showCounter = false }
                    },
                )
            }
        }
    }
}

@Composable
private fun OpenDayHeader(
    title: String,
    subtitle: String,
    titleSize: TextUnit,
) {
    val colors = LocalShopColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.greenTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.WbSunny,
                contentDescription = null,
                tint = colors.green,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = title,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = titleSize,
            lineHeight = titleSize * 1.1f,
            color = colors.ink,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )
    }
}

@Composable
private fun YesterdayCard(cash: Long?, mobile: Long?) {
    if (cash == null || mobile == null) return
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(text = stringResource(R.string.label_yesterday_closed_with))
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                YesterdayAmount(label = stringResource(R.string.label_cash), amount = cash)
                YesterdayAmount(label = stringResource(R.string.label_mobile_money), amount = mobile)
            }
        }
    }
}

@Composable
private fun YesterdayAmount(label: String, amount: Long) {
    val colors = LocalShopColors.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Text(
            text = Money.format(amount),
            style = amountTextStyle(24.sp),
            color = colors.ink,
        )
    }
}

// Green "Matches" badge when the amount equals yesterday's count, otherwise a rust line (6.4, not blocking).
@Composable
private fun OpeningField(
    label: String,
    value: Long?,
    yesterday: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        MoneyField(
            value = value,
            onValueChange = onValueChange,
            trailingBadge = if (value != null && value == yesterday) stringResource(R.string.matches) else null,
        )
        if (value != null && yesterday != null && value != yesterday) {
            Text(
                text = stringResource(R.string.different_from_yesterday, Money.format(abs(value - yesterday))),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.rust,
            )
        }
    }
}

@Composable
private fun LockedUntilOpenHint() {
    val colors = LocalShopColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.selling_locked_until_open),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
    }
}

@Composable
private fun StartDayButton(
    saving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryButton(
        text = stringResource(R.string.action_start_day),
        onClick = onClick,
        modifier = modifier,
        enabled = !saving,
        height = 68.dp,
        leadingIcon = Icons.Outlined.WbSunny,
    )
}
