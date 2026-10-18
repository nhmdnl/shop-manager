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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.Badge
import com.archsolutions.shopmanager.core.designsystem.components.BadgeKind
import com.archsolutions.shopmanager.core.designsystem.components.ChipRow
import com.archsolutions.shopmanager.core.designsystem.components.InkButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.StatTile
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.db.entities.DayStatus
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import java.time.ZoneId

/** Day — "Today's money" (6.5, 7.4): stats, every money movement, and Record money on tablet. */
@Composable
fun DayScreen(
    isExpanded: Boolean,
    gateState: DayGateState,
    zoneId: ZoneId,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    onRecordMoney: (MoneyEntryType) -> Unit,
    onDebtors: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val day = state.day
    // Records of a closed day are read-only (6.7); only an open day takes new money.
    val canRecord = gateState is DayGateState.Ready
    val canClose = day != null && day.status == DayStatus.OPEN

    if (state.loaded && day == null) {
        Scaffold(modifier = modifier.fillMaxSize(), containerColor = colors.paper) { paddingValues ->
            DayGateLockCard(
                gateState = gateState,
                zoneId = zoneId,
                onOpenDay = onOpenDay,
                onCloseDay = onCloseDay,
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 12.dp),
            )
        }
        return
    }

    if (isExpanded) {
        Row(modifier = modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                containerColor = colors.paper,
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(horizontal = 28.dp)
                        .padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DayHeader(state = state, zoneId = zoneId, canClose = canClose, onCloseDay = onCloseDay)
                    DayStats(state = state, columns = 4, onDebtors = onDebtors)
                    MovementsCard(state = state, viewModel = viewModel, modifier = Modifier.weight(1f).padding(bottom = 20.dp))
                }
            }
            if (canRecord) {
                VerticalDivider(color = colors.line)
                RecordMoneyPanel(zoneId = zoneId, modifier = Modifier.width(440.dp).fillMaxHeight())
            }
        }
    } else {
        DayCompact(
            state = state,
            viewModel = viewModel,
            zoneId = zoneId,
            canRecord = canRecord,
            canClose = canClose,
            onCloseDay = onCloseDay,
            onRecordMoney = onRecordMoney,
            onDebtors = onDebtors,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun DayHeader(state: DayUiState, zoneId: ZoneId, canClose: Boolean, onCloseDay: () -> Unit) {
    val colors = LocalShopColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            state.day?.let { day ->
                Text(
                    text = stringResource(R.string.day_open_since, DayFormat.time(day.openedAt, zoneId)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
            Text(
                text = stringResource(R.string.todays_money),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = colors.ink,
            )
        }
        if (canClose) {
            InkButton(
                text = stringResource(R.string.close_the_day),
                onClick = onCloseDay,
                leadingIcon = Icons.Outlined.Lock,
            )
        }
    }
}

// Sales (incl. credit), Expenses, Purchases, Sold on credit (rust, opens Owed to the shop).
@Composable
private fun DayStats(state: DayUiState, columns: Int, onDebtors: () -> Unit) {
    val colors = LocalShopColors.current
    val balance = state.balance
    val tiles = listOf(
        Triple(stringResource(R.string.label_sales), balance?.totalSales ?: 0L, false),
        Triple(stringResource(R.string.label_expenses), balance?.expensesTotal ?: 0L, false),
        Triple(stringResource(R.string.label_purchases), balance?.purchasesTotal ?: 0L, false),
        Triple(stringResource(R.string.sold_on_credit), balance?.creditSales ?: 0L, true),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, amount, credit) ->
                    StatTile(
                        label = label,
                        amountFormatted = Money.format(amount, withCode = false),
                        amountColor = if (credit) colors.rust else null,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (credit) Modifier.clickable(onClick = onDebtors) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementsCard(state: DayUiState, viewModel: DayViewModel, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    val filterLabels = mapOf(
        ChannelFilter.ALL to stringResource(R.string.filter_all),
        ChannelFilter.CASH to stringResource(R.string.label_cash),
        ChannelFilter.MOBILE_MONEY to stringResource(R.string.label_mobile_money),
    )
    ShopCard(modifier = modifier.fillMaxWidth(), padding = 0.dp) {
        Column {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ChipRow fills the width it gets, so it takes the weight and the label keeps its own width.
                SectionLabel(text = stringResource(R.string.money_in_out_today), modifier = Modifier.padding(end = 12.dp))
                ChipRow(
                    items = ChannelFilter.entries,
                    selectedItem = state.filter,
                    onItemSelected = viewModel::setFilter,
                    labelProvider = { filterLabels.getValue(it) },
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                )
            }
            if (state.movements.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_reports),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                    modifier = Modifier.padding(20.dp),
                )
            }
            LazyColumn {
                itemsIndexed(state.movements, key = { _, movement -> movement.key }) { index, movement ->
                    if (index > 0) HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 20.dp))
                    MovementRow(movement = movement)
                }
            }
        }
    }
}

@Composable
private fun MovementRow(movement: MoneyMovement) {
    val colors = LocalShopColors.current
    val (kindLabel, kindBadge) = movementKindBadge(movement.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(movement.time, style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.width(48.dp))
        Row(modifier = Modifier.width(112.dp)) { Badge(text = kindLabel, kind = kindBadge) }
        Text(
            text = movementDescription(movement),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = movementChannel(movement),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.width(110.dp),
        )
        // Money in: green "+"; money out: ink "−"; credit sales and unpaid purchases: plain (6.5).
        Text(
            text = when {
                movement.isMoneyIn -> Money.signed(movement.amount)
                movement.isMoneyOut -> Money.signed(-movement.amount)
                else -> Money.format(movement.amount, withCode = false)
            },
            style = amountTextStyle(16.sp),
            color = if (movement.isMoneyIn) colors.green else colors.ink,
        )
    }
}

@Composable
private fun movementKindBadge(kind: MovementKind): Pair<String, BadgeKind> = when (kind) {
    MovementKind.SALE -> stringResource(R.string.kind_sale) to BadgeKind.Ok
    MovementKind.CREDIT_SALE -> stringResource(R.string.kind_credit_sale) to BadgeKind.Muted
    MovementKind.CREDIT_PAID -> stringResource(R.string.kind_credit_paid) to BadgeKind.Ok
    MovementKind.EXPENSE -> stringResource(R.string.kind_expense) to BadgeKind.Warn
    MovementKind.PURCHASE -> stringResource(R.string.kind_purchase) to BadgeKind.Muted
    MovementKind.CASH_OUT -> stringResource(R.string.kind_cash_out) to BadgeKind.Warn
    MovementKind.CASH_IN -> stringResource(R.string.cash_in) to BadgeKind.Ok
}

@Composable
private fun movementDescription(movement: MoneyMovement): String = when (movement.kind) {
    MovementKind.SALE, MovementKind.CREDIT_SALE -> movement.detail.orEmpty()
    MovementKind.CREDIT_PAID -> stringResource(R.string.paid_back_desc, movement.detail.orEmpty())
    MovementKind.EXPENSE -> {
        val category = ExpenseCategory.entries.firstOrNull { it.key == movement.category }?.let { expenseCategoryLabel(it) }
            ?: movement.category
        listOfNotNull(category, movement.detail).joinToString(" · ")
    }
    MovementKind.PURCHASE -> stringResource(
        R.string.purchase_desc,
        movement.detail ?: stringResource(R.string.no_supplier),
        pluralStringResource(R.plurals.plurals_products, movement.itemCount, movement.itemCount),
    )
    MovementKind.CASH_OUT -> listOfNotNull(stringResource(R.string.cash_taken_out), movement.detail).joinToString(" · ")
    MovementKind.CASH_IN -> listOfNotNull(stringResource(R.string.cash_in), movement.detail).joinToString(" · ")
}

@Composable
private fun movementChannel(movement: MoneyMovement): String = when {
    movement.channel == MoneyMethod.CASH -> stringResource(R.string.label_cash)
    movement.channel == MoneyMethod.MOBILE_MONEY -> stringResource(R.string.label_mobile_money)
    movement.kind == MovementKind.CREDIT_SALE -> stringResource(R.string.label_credit)
    else -> stringResource(R.string.not_paid_yet)
}

// Tablet right panel; the "+" opens Cash in (addendum CashIn), the "×" goes back to the other kinds.
@Composable
private fun RecordMoneyPanel(
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    viewModel: RecordMoneyViewModel = hiltViewModel(),
) {
    val colors = LocalShopColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    RecordMoneySnackbarEffects(viewModel = viewModel, hostState = snackbarHostState)

    Scaffold(
        modifier = modifier,
        containerColor = colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        RecordMoneyForm(
            state = state,
            viewModel = viewModel,
            zoneId = zoneId,
            modifier = Modifier
                .padding(paddingValues)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (state.type == MoneyEntryType.CASH_IN) R.string.cash_in else R.string.record_money),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = colors.ink,
                        )
                        if (state.type == MoneyEntryType.CASH_IN) {
                            Text(stringResource(R.string.cash_in_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                    }
                    if (state.type == MoneyEntryType.CASH_IN) {
                        IconButton(onClick = { viewModel.setType(MoneyEntryType.EXPENSE) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel), tint = colors.ink)
                        }
                    } else {
                        IconButton(onClick = { viewModel.setType(MoneyEntryType.CASH_IN) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cash_in), tint = colors.ink)
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayCompact(
    state: DayUiState,
    viewModel: DayViewModel,
    zoneId: ZoneId,
    canRecord: Boolean,
    canClose: Boolean,
    onCloseDay: () -> Unit,
    onRecordMoney: (MoneyEntryType) -> Unit,
    onDebtors: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.todays_money),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = colors.ink,
                        )
                        state.day?.let {
                            Text(
                                text = stringResource(R.string.day_open_since, DayFormat.time(it.openedAt, zoneId)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
        bottomBar = {
            if (canRecord || canClose) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (canRecord) {
                        SecondaryButton(
                            text = stringResource(R.string.record_money),
                            onClick = { onRecordMoney(MoneyEntryType.EXPENSE) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (canClose) {
                        InkButton(
                            text = stringResource(R.string.close_the_day),
                            onClick = onCloseDay,
                            leadingIcon = Icons.Outlined.Lock,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DayStats(state = state, columns = 2, onDebtors = onDebtors)
            MovementsCardCompact(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.heightIn(min = 4.dp))
        }
    }
}

// Phone rows stack the description over the channel to fit 390 dp.
@Composable
private fun MovementsCardCompact(state: DayUiState, viewModel: DayViewModel, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    val filterLabels = mapOf(
        ChannelFilter.ALL to stringResource(R.string.filter_all),
        ChannelFilter.CASH to stringResource(R.string.label_cash),
        ChannelFilter.MOBILE_MONEY to stringResource(R.string.label_mobile_money),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipRow(
            items = ChannelFilter.entries,
            selectedItem = state.filter,
            onItemSelected = viewModel::setFilter,
            labelProvider = { filterLabels.getValue(it) },
        )
        ShopCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false), padding = 0.dp) {
            LazyColumn {
                if (state.movements.isEmpty()) {
                    item {
                        Text(stringResource(R.string.empty_reports), style = MaterialTheme.typography.bodyLarge, color = colors.muted, modifier = Modifier.padding(16.dp))
                    }
                }
                itemsIndexed(state.movements, key = { _, movement -> movement.key }) { index, movement ->
                    if (index > 0) HorizontalDivider(color = colors.line)
                    val (kindLabel, kindBadge) = movementKindBadge(movement.kind)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(movement.time, style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.width(44.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = movementDescription(movement),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Badge(text = kindLabel, kind = kindBadge)
                                Text(movementChannel(movement), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            }
                        }
                        Text(
                            text = when {
                                movement.isMoneyIn -> Money.signed(movement.amount)
                                movement.isMoneyOut -> Money.signed(-movement.amount)
                                else -> Money.format(movement.amount, withCode = false)
                            },
                            style = amountTextStyle(16.sp),
                            color = if (movement.isMoneyIn) colors.green else colors.ink,
                        )
                    }
                }
            }
        }
    }
}
