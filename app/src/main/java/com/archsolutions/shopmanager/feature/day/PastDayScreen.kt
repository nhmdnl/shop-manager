package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.settings.ShopSettings
import com.archsolutions.shopmanager.domain.BalanceCalculator
import com.archsolutions.shopmanager.domain.BalanceStatus
import com.archsolutions.shopmanager.domain.ChannelBalance
import java.time.ZoneId
import kotlin.math.abs

/**
 * A closed day, read-only (design board "Past day"), from Reports › Days. Corrections never happen
 * here: they are recorded on today's open day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastDayScreen(
    isExpanded: Boolean,
    zoneId: ZoneId,
    onBack: () -> Unit,
    onSales: (dayId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayRecordViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val storedSettings by viewModel.settings.collectAsStateWithLifecycle()
    val settings = storedSettings ?: ShopSettings()
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val day = record?.day
    val balance = record?.balance
    val summary = rememberDaySummary(
        shopName = settings.shopName,
        date = day?.date,
        balance = balance,
        countedCash = day?.countedCash ?: 0L,
        countedMobile = day?.countedMobile ?: 0L,
        allowedDifference = settings.allowedDifference,
        reason = day?.diffReason,
    )
    val sendSummary = rememberSendSummary()

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
                            text = day?.let { DayFormat.date(it.date, "EEE d MMM", locale) }.orEmpty(),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 30.sp else 26.sp,
                            color = colors.ink,
                        )
                        day?.closedAt?.let { closedAt ->
                            Text(
                                text = stringResource(R.string.past_day_closed_at, DayFormat.time(closedAt, zoneId)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        }
                    }
                },
                actions = {
                    Surface(shape = RoundedCornerShape(50), color = colors.segmentTrack, modifier = Modifier.padding(end = 16.dp)) {
                        Text(
                            text = stringResource(R.string.past_day_locked),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.muted,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        if (day == null || balance == null) return@Scaffold
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                    Column {
                        AmountRow(label = "", cash = stringResource(R.string.label_cash), mobile = stringResource(R.string.label_momo), header = true)
                        ChannelRows(cash = balance.cash, mobile = balance.mobile)
                        HorizontalDivider(color = colors.line)
                        AmountRow(
                            label = stringResource(R.string.label_counted),
                            cash = day.countedCash?.let { Money.format(it, withCode = false) }.orEmpty(),
                            mobile = day.countedMobile?.let { Money.format(it, withCode = false) }.orEmpty(),
                            bold = true,
                        )
                        HorizontalDivider(color = colors.line)
                        Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.label_difference), style = MaterialTheme.typography.bodyLarge, color = colors.muted, modifier = Modifier.weight(1f))
                            Box(modifier = Modifier.width(COLUMN), contentAlignment = Alignment.CenterEnd) { DifferenceBadge(day.diffCash ?: 0L, settings.allowedDifference) }
                            Box(modifier = Modifier.width(COLUMN), contentAlignment = Alignment.CenterEnd) { DifferenceBadge(day.diffMobile ?: 0L, settings.allowedDifference) }
                        }
                    }
                }
                ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                    Column {
                        TotalRow(stringResource(R.string.total_sales_incl_credit), Money.format(balance.totalSales), colors.ink)
                        HorizontalDivider(color = colors.line)
                        TotalRow(stringResource(R.string.sold_on_credit), Money.format(balance.creditSales), colors.ink)
                        HorizontalDivider(color = colors.line)
                        TotalRow(stringResource(R.string.profit_est), Money.format(balance.profit), colors.green)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.label_sales),
                        onClick = { onSales(day.id) },
                        leadingIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        height = 60.dp,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_share),
                        onClick = { summary?.let(sendSummary) },
                        enabled = summary != null,
                        leadingIcon = Icons.Outlined.Share,
                        height = 60.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.past_day_footer), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
    }
}

private val COLUMN = 104.dp

/** Opening, money in and out, then what the drawer should have held (6.1). */
@Composable
private fun ChannelRows(cash: ChannelBalance, mobile: ChannelBalance) {
    @Composable
    fun signed(label: Int, cashAmount: Long, mobileAmount: Long, sign: String, always: Boolean = true) {
        if (!always && cashAmount == 0L && mobileAmount == 0L) return
        AmountRow(
            label = stringResource(label),
            cash = "$sign ${Money.format(cashAmount, withCode = false)}",
            mobile = "$sign ${Money.format(mobileAmount, withCode = false)}",
        )
    }
    AmountRow(stringResource(R.string.label_opening_balance), Money.format(cash.opening, withCode = false), Money.format(mobile.opening, withCode = false))
    signed(R.string.label_sales, cash.sales, mobile.sales, "+")
    signed(R.string.credit_paid_back, cash.creditRepaid, mobile.creditRepaid, "+")
    signed(R.string.cash_in, cash.cashIn, mobile.cashIn, "+", always = false)
    signed(R.string.label_expenses, cash.expenses, mobile.expenses, "−")
    signed(R.string.cash_taken_out, cash.cashOut, mobile.cashOut, "−", always = false)
    signed(R.string.purchases_paid, cash.purchasesPaid, mobile.purchasesPaid, "−")
    HorizontalDivider(color = LocalShopColors.current.line)
    AmountRow(stringResource(R.string.label_should_have), Money.format(cash.expected, withCode = false), Money.format(mobile.expected, withCode = false), bold = true)
}

@Composable
private fun AmountRow(label: String, cash: String, mobile: String, header: Boolean = false, bold: Boolean = false) {
    val colors = LocalShopColors.current
    val weight = if (bold || header) FontWeight.Bold else FontWeight.SemiBold
    Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) colors.ink else colors.muted,
            modifier = Modifier.weight(1f),
        )
        listOf(cash, mobile).forEach { value ->
            Text(
                text = value,
                style = if (header) MaterialTheme.typography.bodyMedium else amountTextStyle(16.sp),
                fontWeight = weight,
                color = if (header) colors.muted else colors.ink,
                textAlign = TextAlign.End,
                modifier = Modifier.width(COLUMN),
            )
        }
    }
}

@Composable
private fun DifferenceBadge(difference: Long, allowed: Long) {
    val (text, kind) = when (BalanceCalculator.status(difference, allowed)) {
        BalanceStatus.BALANCED -> stringResource(R.string.status_balanced) to BadgeKind.Ok
        BalanceStatus.SHORT -> stringResource(R.string.short_amount, Money.format(abs(difference), withCode = false)) to BadgeKind.Warn
        BalanceStatus.OVER -> stringResource(R.string.over_amount, Money.format(difference, withCode = false)) to BadgeKind.Warn
    }
    Badge(text = text, kind = kind)
}

@Composable
private fun TotalRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = LocalShopColors.current.ink, modifier = Modifier.weight(1f))
        Text(value, style = amountTextStyle(17.sp), color = valueColor)
    }
}
