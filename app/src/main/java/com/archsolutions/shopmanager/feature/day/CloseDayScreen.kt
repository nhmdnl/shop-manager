package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
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
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.Badge
import com.archsolutions.shopmanager.core.designsystem.components.BadgeKind
import com.archsolutions.shopmanager.core.designsystem.components.ChipRow
import com.archsolutions.shopmanager.core.designsystem.components.InkButton
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.SegmentedControl
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.designsystem.components.StepIndicator
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.core.util.WhatsApp
import com.archsolutions.shopmanager.domain.BalanceStatus
import com.archsolutions.shopmanager.domain.ChannelBalance
import com.archsolutions.shopmanager.domain.DayBalance
import com.archsolutions.shopmanager.domain.DaySummary
import com.archsolutions.shopmanager.domain.SummaryTemplates
import kotlinx.coroutines.launch

private enum class Channel { CASH, MOBILE_MONEY }

/** Close the day (6.6, 7.4). On success: opens the WhatsApp summary if chosen, then [onClosed]. */
@Composable
fun CloseDayScreen(
    isExpanded: Boolean,
    onBack: () -> Unit,
    onDebtors: () -> Unit,
    onClosed: (dayId: Long, summarySent: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloseDayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closeFailed by viewModel.closeFailed.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.error_could_not_save)
    val summary = rememberDaySummary(
        shopName = state.shopName,
        date = state.day?.date,
        balance = state.balance,
        countedCash = state.countedCash ?: 0L,
        countedMobile = state.countedMobile ?: 0L,
        allowedDifference = state.allowedDifference,
        reason = listOfNotNull(state.reason, state.reasonNote.trim().ifEmpty { null }).joinToString(" — ").ifEmpty { null },
    )
    val sendSummary = rememberSendSummary()

    LaunchedEffect(state.closed) {
        val day = state.day
        if (state.closed && day != null) {
            val sent = state.sendSummary && summary != null && sendSummary(summary)
            onClosed(day.id, sent)
        }
    }
    LaunchedEffect(closeFailed) {
        if (closeFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.closeFailedShown()
        }
    }

    if (isExpanded) {
        Row(modifier = modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                containerColor = colors.paper,
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CloseDayHeader(state = state, onBack = onBack)
                    CheckTable(state = state, viewModel = viewModel)
                    InfoCards(state = state, onDebtors = onDebtors)
                }
            }
            VerticalDivider(color = colors.line)
            Column(
                modifier = Modifier
                    .width(440.dp)
                    .fillMaxHeight()
                    .background(colors.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    NoteCounterPanel(
                        notes = state.notes,
                        coins = state.coins,
                        total = state.notesTotal,
                        onNoteCountChange = viewModel::setNoteCount,
                        onCoinsChange = viewModel::setCoins,
                        fillHeight = true,
                        title = stringResource(R.string.count_cash),
                        optional = false,
                        showHint = false,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ReasonAndSend(state = state, viewModel = viewModel, label = stringResource(R.string.reason_for_difference))
                PrimaryButton(
                    text = stringResource(R.string.action_close_day),
                    onClick = viewModel::closeDay,
                    enabled = state.canClose,
                    height = 64.dp,
                    leadingIcon = Icons.Outlined.Lock,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    } else {
        CloseDayCompact(
            state = state,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onDebtors = onDebtors,
            modifier = modifier,
        )
    }
}

@Composable
private fun CloseDayHeader(state: CloseDayUiState, onBack: () -> Unit) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            state.day?.let {
                Text(DayFormat.date(it.date, "EEEE, d MMMM", locale), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Text(
                text = stringResource(R.string.close_the_day),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = colors.ink,
            )
        }
        StepIndicator(currentStep = state.step, labels = stepLabels())
    }
}

@Composable
private fun stepLabels(): List<String> = listOf(
    stringResource(R.string.step_check),
    stringResource(R.string.step_count),
    stringResource(R.string.step_close),
)

// Tablet "Check" table: both channels side by side, then counted fields and differences (6.2).
@Composable
private fun CheckTable(state: CloseDayUiState, viewModel: CloseDayViewModel) {
    val colors = LocalShopColors.current
    val balance = state.balance ?: return
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 24.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TableRow(label = "", cash = stringResource(R.string.label_cash).uppercase(), mobile = stringResource(R.string.label_mobile_money).uppercase(), header = true)
            TableAmountRow(stringResource(R.string.label_opening_balance), balance.cash.opening, balance.mobile.opening, sign = 0)
            TableAmountRow(stringResource(R.string.label_sales), balance.cash.sales, balance.mobile.sales, sign = 1)
            TableAmountRow(stringResource(R.string.credit_paid_back), balance.cash.creditRepaid, balance.mobile.creditRepaid, sign = 1)
            if (balance.cash.cashIn + balance.mobile.cashIn > 0) {
                TableAmountRow(stringResource(R.string.cash_in), balance.cash.cashIn, balance.mobile.cashIn, sign = 1)
            }
            TableAmountRow(stringResource(R.string.label_expenses), balance.cash.expenses, balance.mobile.expenses, sign = -1)
            if (balance.cash.cashOut + balance.mobile.cashOut > 0) {
                TableAmountRow(stringResource(R.string.cash_taken_out), balance.cash.cashOut, balance.mobile.cashOut, sign = -1)
            }
            TableAmountRow(stringResource(R.string.purchases_paid), balance.cash.purchasesPaid, balance.mobile.purchasesPaid, sign = -1)
            HorizontalDivider(color = colors.ink, thickness = 1.5.dp, modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_should_have), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.ink, modifier = Modifier.weight(1f))
                Text(Money.format(balance.cash.expected, withCode = false), style = amountTextStyle(24.sp), color = colors.ink, modifier = Modifier.width(190.dp), textAlign = TextAlign.End)
                Text(Money.format(balance.mobile.expected, withCode = false), style = amountTextStyle(24.sp), color = colors.ink, modifier = Modifier.width(190.dp), textAlign = TextAlign.End)
            }
            HorizontalDivider(color = colors.line)
            Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_counted), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink, modifier = Modifier.weight(1f))
                MoneyField(value = state.countedCash, onValueChange = viewModel::setCountedCash, modifier = Modifier.width(182.dp))
                Spacer(modifier = Modifier.width(8.dp))
                MoneyField(value = state.countedMobile, onValueChange = viewModel::setCountedMobile, modifier = Modifier.width(182.dp))
            }
            HorizontalDivider(color = colors.line)
            Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_difference), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink, modifier = Modifier.weight(1f))
                Row(modifier = Modifier.width(190.dp), horizontalArrangement = Arrangement.End) { DifferenceBadge(state.cashDifference, state.cashStatus) }
                Row(modifier = Modifier.width(190.dp), horizontalArrangement = Arrangement.End) { DifferenceBadge(state.mobileDifference, state.mobileStatus) }
            }
        }
    }
}

@Composable
private fun TableRow(label: String, cash: String, mobile: String, header: Boolean = false) {
    val colors = LocalShopColors.current
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.muted, modifier = Modifier.weight(1f))
        val style = if (header) MaterialTheme.typography.labelMedium else amountTextStyle(18.sp)
        Text(cash, style = style, color = if (header) colors.muted else colors.ink, modifier = Modifier.width(190.dp), textAlign = TextAlign.End)
        Text(mobile, style = style, color = if (header) colors.muted else colors.ink, modifier = Modifier.width(190.dp), textAlign = TextAlign.End)
    }
}

/** sign: 1 = money in (green "+"), -1 = money out ("−"), 0 = plain. */
@Composable
private fun TableAmountRow(label: String, cash: Long, mobile: Long, sign: Int) {
    val colors = LocalShopColors.current
    Column {
        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.ink, modifier = Modifier.weight(1f))
            listOf(cash, mobile).forEach { amount ->
                Text(
                    text = signedAmount(amount, sign),
                    style = amountTextStyle(18.sp),
                    color = if (sign > 0) colors.green else colors.ink,
                    modifier = Modifier.width(190.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
        HorizontalDivider(color = colors.line)
    }
}

private fun signedAmount(amount: Long, sign: Int): String = when (sign) {
    1 -> Money.signed(amount)
    -1 -> Money.signed(-amount)
    else -> Money.format(amount, withCode = false)
}

@Composable
private fun DifferenceBadge(difference: Long?, status: BalanceStatus?) {
    if (difference == null || status == null) return
    when (status) {
        BalanceStatus.BALANCED -> Badge(text = stringResource(R.string.status_balanced), kind = BadgeKind.Ok)
        BalanceStatus.SHORT -> Badge(text = stringResource(R.string.short_amount, Money.format(-difference, withCode = false)), kind = BadgeKind.Warn)
        BalanceStatus.OVER -> Badge(text = stringResource(R.string.over_amount, Money.format(difference, withCode = false)), kind = BadgeKind.Warn)
    }
}

// Total sales today, Owed to the shop, Tomorrow opens with (6.6).
@Composable
private fun InfoCards(state: CloseDayUiState, onDebtors: () -> Unit) {
    val colors = LocalShopColors.current
    val balance = state.balance ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoCard(
            title = stringResource(R.string.total_sales_today),
            amount = Money.format(balance.totalSales),
            caption = stringResource(R.string.includes_credit, Money.format(balance.creditSales, withCode = false)),
            modifier = Modifier.weight(1f),
        )
        InfoCard(
            title = stringResource(R.string.label_owed_to_shop),
            amount = Money.format(state.debtorsTotal),
            amountColor = colors.rust,
            caption = listOfNotNull(
                pluralStringResource(R.plurals.plurals_customers, state.debtorsCount, state.debtorsCount),
                state.firstDebtorName,
            ).joinToString(" · "),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onDebtors),
        )
        InfoCard(
            title = stringResource(R.string.tomorrow_opens_with),
            amount = Money.format(state.countedCash ?: balance.cash.expected),
            caption = stringResource(R.string.tomorrow_detail, Money.format(state.countedMobile ?: balance.mobile.expected, withCode = false)),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    amount: String,
    caption: String,
    modifier: Modifier = Modifier,
    amountColor: Color? = null,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Text(amount, style = amountTextStyle(22.sp), color = amountColor ?: colors.ink)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

// Reason chips (required when a channel is Short or Over), an "Other" note, and the WhatsApp checkbox.
@Composable
private fun ReasonAndSend(state: CloseDayUiState, viewModel: CloseDayViewModel, label: String) {
    val colors = LocalShopColors.current
    val reasons = listOf(
        stringResource(R.string.reason_change_error),
        stringResource(R.string.reason_not_recorded),
        stringResource(R.string.reason_other),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.needsReason) {
            SectionLabel(text = label)
            ChipRow(items = reasons, selectedItem = state.reason.orEmpty(), onItemSelected = viewModel::setReason)
            if (state.reason == reasons.last()) {
                ShopTextField(value = state.reasonNote, onValueChange = viewModel::setReasonNote, placeholder = stringResource(R.string.note_placeholder))
            }
        }
        Row(
            modifier = Modifier.clickable { viewModel.setSendSummary(!state.sendSummary) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.sendSummary,
                onCheckedChange = viewModel::setSendSummary,
                colors = CheckboxDefaults.colors(checkedColor = colors.green, checkmarkColor = colors.onGreen),
            )
            Text(stringResource(R.string.send_summary_whatsapp), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseDayCompact(
    state: CloseDayUiState,
    viewModel: CloseDayViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onDebtors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    var channel by rememberSaveable { mutableStateOf(Channel.CASH) }
    var showCounter by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val channelLabels = mapOf(
        Channel.CASH to stringResource(R.string.label_cash),
        Channel.MOBILE_MONEY to stringResource(R.string.label_mobile_money),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.close_the_day), fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = colors.ink)
                        state.day?.let {
                            Text(DayFormat.date(it.date, "EEEE, d MMM", locale), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
        bottomBar = {
            InkButton(
                text = stringResource(R.string.action_close_day),
                onClick = viewModel::closeDay,
                enabled = state.canClose,
                height = 64.dp,
                leadingIcon = Icons.Outlined.Lock,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        },
    ) { paddingValues ->
        val balance = state.balance
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepIndicator(currentStep = state.step, labels = stepLabels())
            SegmentedControl(
                items = Channel.entries,
                selectedItem = channel,
                onItemSelected = { channel = it },
                labelProvider = { channelLabels.getValue(it) },
            )
            if (balance != null) {
                val isCash = channel == Channel.CASH
                ChannelCheckCard(channel = if (isCash) balance.cash else balance.mobile, isCash = isCash)
                Text(
                    text = stringResource(if (isCash) R.string.cash_counted else R.string.momo_counted),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                )
                MoneyField(
                    value = if (isCash) state.countedCash else state.countedMobile,
                    onValueChange = if (isCash) viewModel::setCountedCash else viewModel::setCountedMobile,
                )
                if (isCash) {
                    TextButton(onClick = {
                        focusManager.clearFocus()
                        showCounter = true
                    }) {
                        Text(stringResource(R.string.count_notes_one_by_one), fontWeight = FontWeight.Bold, color = colors.green)
                    }
                }
                val difference = if (isCash) state.cashDifference else state.mobileDifference
                val status = if (isCash) state.cashStatus else state.mobileStatus
                if (difference != null && status != null) StatusRow(difference = difference, status = status)
                ReasonAndSend(state = state, viewModel = viewModel, label = stringResource(R.string.label_reason))
                if (balance.creditSales > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.sold_on_credit_today, Money.format(balance.creditSales)) + " · ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                    Text(
                        text = stringResource(R.string.label_owed_to_shop),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.green,
                        modifier = Modifier.clickable(onClick = onDebtors),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showCounter) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { showCounter = false }, sheetState = sheetState, containerColor = colors.surface) {
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
                    onNoteCountChange = viewModel::setNoteCount,
                    onCoinsChange = viewModel::setCoins,
                    fillHeight = false,
                    title = stringResource(R.string.count_cash),
                    optional = false,
                    showHint = false,
                )
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = stringResource(R.string.action_done),
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { showCounter = false } },
                )
            }
        }
    }
}

@Composable
private fun ChannelCheckCard(channel: ChannelBalance, isCash: Boolean) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column {
            CheckLine(stringResource(if (isCash) R.string.opening_cash else R.string.opening_momo), Money.format(channel.opening, withCode = false))
            CheckLine(stringResource(if (isCash) R.string.cash_sales else R.string.momo_sales), Money.signed(channel.sales))
            CheckLine(stringResource(R.string.credit_paid_back), Money.signed(channel.creditRepaid))
            if (channel.cashIn > 0) CheckLine(stringResource(R.string.cash_in), Money.signed(channel.cashIn))
            CheckLine(stringResource(R.string.label_expenses), Money.signed(-channel.expenses))
            if (channel.cashOut > 0) CheckLine(stringResource(R.string.cash_taken_out), Money.signed(-channel.cashOut))
            CheckLine(stringResource(R.string.purchases_paid), Money.signed(-channel.purchasesPaid))
            Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (isCash) R.string.should_be_in_drawer else R.string.should_be_on_momo),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(channel.expected), style = amountTextStyle(22.sp), color = colors.ink)
            }
        }
    }
}

@Composable
private fun CheckLine(label: String, value: String) {
    val colors = LocalShopColors.current
    Column {
        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.muted, modifier = Modifier.weight(1f))
            Text(value, style = amountTextStyle(16.sp), color = colors.ink)
        }
        HorizontalDivider(color = colors.line)
    }
}

@Composable
private fun StatusRow(difference: Long, status: BalanceStatus) {
    val colors = LocalShopColors.current
    val (label, tint, background) = when (status) {
        BalanceStatus.BALANCED -> Triple(stringResource(R.string.status_balanced), colors.green, colors.greenTint)
        BalanceStatus.SHORT -> Triple(stringResource(R.string.short_by), colors.rust, colors.rustTint)
        BalanceStatus.OVER -> Triple(stringResource(R.string.over_by), colors.rust, colors.rustTint)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = tint, modifier = Modifier.weight(1f))
        if (status != BalanceStatus.BALANCED) {
            Text(Money.format(kotlin.math.abs(difference)), style = amountTextStyle(22.sp), color = tint)
        }
    }
}

/** The 6.6 summary text in the app language, or null until the day and balance are loaded. */
@Composable
internal fun rememberDaySummary(
    shopName: String,
    date: String?,
    balance: DayBalance?,
    countedCash: Long,
    countedMobile: Long,
    allowedDifference: Long,
    reason: String?,
): String? {
    val locale = LocalConfiguration.current.locales[0]
    val templates = SummaryTemplates(
        title = stringResource(R.string.summary_title),
        sales = stringResource(R.string.summary_sales),
        costs = stringResource(R.string.summary_costs),
        channel = stringResource(R.string.summary_channel),
        short = stringResource(R.string.summary_short),
        over = stringResource(R.string.summary_over),
        balanced = stringResource(R.string.summary_balanced),
        withReason = stringResource(R.string.summary_with_reason),
        profit = stringResource(R.string.summary_profit),
        cashLabel = stringResource(R.string.label_cash),
        mobileLabel = stringResource(R.string.label_mobile_money),
    )
    if (date == null || balance == null) return null
    return DaySummary.build(
        templates = templates,
        shopName = shopName,
        dateText = DayFormat.date(date, "EEE d MMM yyyy", locale),
        balance = balance,
        countedCash = countedCash,
        countedMobile = countedMobile,
        allowedDifference = allowedDifference,
        reason = reason,
    )
}

/**
 * Opens WhatsApp with the summary (the user picks the chat), or the share sheet without WhatsApp.
 * True only when WhatsApp opened.
 */
@Composable
internal fun rememberSendSummary(): (String) -> Boolean {
    val context = LocalContext.current
    return remember(context) { { text: String -> WhatsApp.send(context, phone = null, text = text) } }
}
