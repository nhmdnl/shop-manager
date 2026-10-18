package com.archsolutions.shopmanager.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SegmentedControl
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.StatTile
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.domain.BalanceCalculator
import com.archsolutions.shopmanager.domain.BalanceStatus
import com.archsolutions.shopmanager.domain.ClosedDayRow
import com.archsolutions.shopmanager.domain.DailyTotal
import com.archsolutions.shopmanager.domain.PaidBy
import com.archsolutions.shopmanager.domain.ReportData
import com.archsolutions.shopmanager.domain.ReportPreset
import com.archsolutions.shopmanager.domain.TopProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.ceil

/** Reports (7.6 + addendum): Overview with stats, daily chart, paid-by and top products; Days lists closed days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    isExpanded: Boolean,
    onOpenDay: (dayId: Long) -> Unit,
    onDebtors: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportFailed = stringResource(R.string.export_failed)
    var pickingDates by remember { mutableStateOf(false) }

    val export: (ReportExport.Kind) -> Unit = { kind ->
        scope.launch {
            val files = runCatching {
                withContext(Dispatchers.IO) { ReportExport.write(context, kind, state, viewModel.zoneId, System.currentTimeMillis()) }
            }.getOrNull()
            if (files.isNullOrEmpty()) snackbarHostState.showSnackbar(exportFailed) else ReportExport.share(context, files, kind)
        }
    }
    val onPreset: (ReportPreset) -> Unit = { preset ->
        if (preset == ReportPreset.CUSTOM) pickingDates = true else viewModel.setPreset(preset)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isExpanded) {
                TopAppBar(
                    title = { ReportsTitle() },
                    actions = { ShareMenu(compact = true, onExport = export, modifier = Modifier.padding(end = 12.dp)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = if (isExpanded) 20.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportsTitle()
                    Spacer(modifier = Modifier.width(12.dp))
                    TabSwitch(state.tab, viewModel::setTab, Modifier.width(240.dp))
                    PeriodChips(state, onPreset, Modifier.weight(1f))
                    ShareMenu(compact = false, onExport = export)
                }
            } else {
                TabSwitch(state.tab, viewModel::setTab, Modifier.fillMaxWidth())
                PeriodChips(state, onPreset, Modifier.fillMaxWidth())
            }
            val report = state.report
            when {
                report == null -> Unit
                state.tab == ReportTab.DAYS -> DaysContent(state, viewModel.zoneId, onOpenDay)
                isExpanded -> TabletOverview(report, state.today, onDebtors)
                else -> PhoneOverview(report, state.today, onDebtors)
            }
        }
    }

    if (pickingDates) {
        CustomRangeDialog(
            state = state,
            onDismiss = { pickingDates = false },
            onPick = { from, to ->
                viewModel.setCustom(from, to)
                pickingDates = false
            },
        )
    }
}

@Composable
private fun ReportsTitle() {
    Text(
        text = stringResource(R.string.nav_reports),
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = LocalShopColors.current.ink,
    )
}

@Composable
private fun TabSwitch(tab: ReportTab, onTab: (ReportTab) -> Unit, modifier: Modifier) {
    val overview = stringResource(R.string.tab_overview)
    val days = stringResource(R.string.tab_days)
    SegmentedControl(
        items = ReportTab.entries,
        selectedItem = tab,
        onItemSelected = onTab,
        labelProvider = { if (it == ReportTab.OVERVIEW) overview else days },
        modifier = modifier,
    )
}

@Composable
private fun PeriodChips(state: ReportsUiState, onPreset: (ReportPreset) -> Unit, modifier: Modifier) {
    val labels = mapOf(
        ReportPreset.TODAY to stringResource(R.string.period_today),
        ReportPreset.WEEK to stringResource(R.string.period_week),
        ReportPreset.MONTH to stringResource(R.string.period_month),
        ReportPreset.CUSTOM to stringResource(R.string.period_custom),
    )
    // Days lists closed days, so it has no "Today" chip.
    val presets = if (state.tab == ReportTab.DAYS) ReportPreset.entries - ReportPreset.TODAY else ReportPreset.entries
    ChipRow(
        items = presets,
        selectedItem = state.preset,
        onItemSelected = onPreset,
        labelProvider = { labels.getValue(it) },
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    )
}

@Composable
private fun ShareMenu(compact: Boolean, onExport: (ReportExport.Kind) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (compact) {
            OutlinedIconButton(onClick = { open = true }, border = BorderStroke(1.dp, colors.line), modifier = Modifier.size(52.dp)) {
                Icon(Icons.Outlined.IosShare, contentDescription = stringResource(R.string.share_report), tint = colors.ink)
            }
        } else {
            SecondaryButton(text = stringResource(R.string.share_pdf_csv), onClick = { open = true }, leadingIcon = Icons.Outlined.IosShare)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_pdf), color = colors.ink) },
                onClick = {
                    open = false
                    onExport(ReportExport.Kind.PDF)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_csv), color = colors.ink) },
                onClick = {
                    open = false
                    onExport(ReportExport.Kind.CSV)
                },
            )
        }
    }
}

@Composable
private fun TabletOverview(report: ReportData, today: LocalDate?, onDebtors: () -> Unit) {
    val colors = LocalShopColors.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatTile(stringResource(R.string.report_sales_ugx), Money.format(report.stats.sales, withCode = false), Modifier.weight(1f))
            StatTile(
                stringResource(R.string.report_profit_ugx),
                Money.format(report.stats.profit, withCode = false),
                Modifier.weight(1f),
                amountColor = colors.green,
            )
            StatTile(stringResource(R.string.report_number_of_sales), report.stats.count.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.report_average_ugx), Money.format(report.stats.average, withCode = false), Modifier.weight(1f))
        }
        if (report.stats.count == 0) {
            NoSalesCard()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DailySalesCard(report, today, chartHeight = 280.dp)
                    PaidByCard(report.paidBy, wide = true, onDebtors = onDebtors)
                }
                TopProductsCard(report.top, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PhoneOverview(report: ReportData, today: LocalDate?, onDebtors: () -> Unit) {
    val colors = LocalShopColors.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Short amounts ("1.28M", "214k") so three stats fit a phone row.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.label_sales), Money.short(report.stats.sales), Modifier.weight(1f))
            StatTile(stringResource(R.string.label_profit), Money.short(report.stats.profit), Modifier.weight(1f), amountColor = colors.green)
            StatTile(stringResource(R.string.report_sales_count), report.stats.count.toString(), Modifier.weight(1f))
        }
        if (report.stats.count == 0) {
            NoSalesCard()
        } else {
            DailySalesCard(report, today, chartHeight = 200.dp)
            PaidByCard(report.paidBy, wide = false, onDebtors = onDebtors)
            TopProductsCard(report.top, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NoSalesCard() {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 28.dp) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.empty_reports), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(stringResource(R.string.empty_reports_hint), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}

@Composable
private fun DailySalesCard(report: ReportData, today: LocalDate?, chartHeight: Dp) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = stringResource(R.string.daily_sales_ugx), modifier = Modifier.weight(1f))
                Text(
                    text = DayFormat.range(report.period.from, report.period.to, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            BarChart(days = report.daily, today = today, modifier = Modifier.fillMaxWidth().height(chartHeight))
        }
    }
}

/** Daily totals as bars: today green, other days chartSoft; value labels while the bars are wide enough. */
@Composable
private fun BarChart(days: List<DailyTotal>, today: LocalDate?, modifier: Modifier) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val max = days.maxOfOrNull { it.total }?.takeIf { it > 0L } ?: 1L
    val few = days.size <= 10
    val labelEvery = if (few) 1 else ceil(days.size / 8.0).toInt()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (few) 12.dp else 3.dp)) {
        days.forEachIndexed { index, day ->
            val isToday = day.date == today
            val fraction = (day.total.toFloat() / max).coerceIn(0.02f, 1f)
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                // The weighted spacer pushes the label and bar down so bar heights stay proportional.
                Spacer(modifier = Modifier.weight((1f - fraction).coerceAtLeast(0.0001f)))
                if (few) {
                    Text(
                        text = Money.short(day.total),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) colors.ink else colors.muted,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (isToday) colors.green else colors.chartSoft),
                )
                Spacer(modifier = Modifier.height(6.dp))
                val label = when {
                    days.size <= 7 -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    index % labelEvery == 0 || isToday -> day.date.dayOfMonth.toString()
                    else -> ""
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) colors.ink else colors.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PaidByCard(paidBy: PaidBy, wide: Boolean, onDebtors: () -> Unit) {
    val colors = LocalShopColors.current
    val percentages = paidBy.percentages()
    val parts = listOf(
        Triple(stringResource(R.string.label_cash), paidBy.cash, colors.green),
        Triple(stringResource(if (wide) R.string.label_mobile_money else R.string.label_momo), paidBy.mobile, colors.mobileMoney),
        Triple(stringResource(R.string.label_credit), paidBy.credit, colors.rust),
    )
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val legend: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    parts.forEachIndexed { index, (label, _, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                            Text(stringResource(R.string.legend_percent, label, percentages[index]), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                        }
                    }
                }
            }
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionLabel(text = stringResource(R.string.paid_by))
                    SplitBar(parts.map { it.second to it.third }, Modifier.weight(1f))
                    legend()
                }
            } else {
                SectionLabel(text = stringResource(R.string.paid_by))
                SplitBar(parts.map { it.second to it.third }, Modifier.fillMaxWidth())
                legend()
            }
            // Credit is money still owed; the addendum links Debtors from here.
            if (paidBy.credit > 0L) {
                Row(
                    modifier = Modifier.clickable(onClick = onDebtors),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.label_owed_to_shop), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.green)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.green)
                }
            }
        }
    }
}

@Composable
private fun SplitBar(parts: List<Pair<Long, Color>>, modifier: Modifier) {
    val colors = LocalShopColors.current
    Row(
        modifier = modifier
            .height(12.dp)
            .clip(CircleShape)
            .background(colors.segmentTrack),
    ) {
        parts.filter { it.first > 0L }.forEach { (amount, color) ->
            Box(modifier = Modifier.weight(amount.toFloat()).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun TopProductsCard(top: List<TopProduct>, modifier: Modifier) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier, padding = 20.dp) {
        Column {
            SectionLabel(text = stringResource(R.string.top_products))
            top.forEachIndexed { index, product ->
                if (index > 0) HorizontalDivider(color = colors.line)
                Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "${index + 1}", style = MaterialTheme.typography.bodyLarge, color = colors.muted, modifier = Modifier.width(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 1)
                        Text(stringResource(R.string.qty_sold, Qty.format(product.qty)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    }
                    Text(
                        text = Money.format(product.amount, withCode = false),
                        style = amountTextStyle(17.sp),
                        color = colors.ink,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun DaysContent(state: ReportsUiState, zoneId: ZoneId, onOpenDay: (Long) -> Unit) {
    val colors = LocalShopColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShopCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                if (state.closedDays.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_closed_days),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.muted,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                state.closedDays.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = colors.line, modifier = Modifier.padding(horizontal = 20.dp))
                    ClosedDayItem(row = row, allowed = state.allowedDifference, zoneId = zoneId, onClick = { onOpenDay(row.day.id) })
                }
            }
        }
        Text(
            text = stringResource(R.string.days_balanced_note, Money.format(state.allowedDifference)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
    }
}

@Composable
private fun ClosedDayItem(row: ClosedDayRow, allowed: Long, zoneId: ZoneId, onClick: () -> Unit) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val day = row.day
    val closing = if (day.reopenedCount > 0) {
        pluralStringResource(R.plurals.plurals_reopened, day.reopenedCount, day.reopenedCount)
    } else {
        day.closedAt?.let { stringResource(R.string.closed_at, DayFormat.time(it, zoneId)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(DayFormat.date(day.date, "EEE d MMM", locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                text = listOfNotNull(stringResource(R.string.day_row_sales, Money.format(row.sales, withCode = false)), closing).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChannelBadge(stringResource(R.string.label_cash), day.diffCash ?: 0L, allowed)
            ChannelBadge(stringResource(R.string.label_momo), day.diffMobile ?: 0L, allowed)
        }
    }
}

/** "Cash short 900", "MoMo balanced", or "Cash balanced (−200)" when within the allowed difference. */
@Composable
private fun ChannelBadge(channel: String, difference: Long, allowed: Long) {
    val (text, kind) = when (BalanceCalculator.status(difference, allowed)) {
        BalanceStatus.SHORT -> stringResource(R.string.channel_short, channel, Money.format(-difference, withCode = false)) to BadgeKind.Warn
        BalanceStatus.OVER -> stringResource(R.string.channel_over, channel, Money.format(difference, withCode = false)) to BadgeKind.Warn
        BalanceStatus.BALANCED -> if (difference == 0L) {
            stringResource(R.string.channel_balanced, channel) to BadgeKind.Ok
        } else {
            val signed = (if (difference < 0) "−" else "+") + Money.format(abs(difference), withCode = false)
            stringResource(R.string.channel_balanced_diff, channel, signed) to BadgeKind.Ok
        }
    }
    Badge(text = text, kind = kind)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(state: ReportsUiState, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val today = state.today ?: LocalDate.now()
    // The picker works in UTC midnights, whatever the device zone.
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val period = state.report?.period
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = period?.from?.utcMillis(),
        initialSelectedEndDateMillis = period?.to?.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.utcMillis()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = pickerState.selectedStartDateMillis ?: return@TextButton
                    onPick(start.utcDate(), (pickerState.selectedEndDateMillis ?: start).utcDate())
                },
                enabled = pickerState.selectedStartDateMillis != null,
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DateRangePicker(
            state = pickerState,
            title = { Text(stringResource(R.string.pick_dates), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.weight(1f),
        )
    }
}
