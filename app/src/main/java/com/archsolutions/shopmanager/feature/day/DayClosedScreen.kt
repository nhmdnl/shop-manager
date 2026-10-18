package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.archsolutions.shopmanager.core.designsystem.components.InkButton
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.domain.BalanceCalculator
import com.archsolutions.shopmanager.domain.BalanceStatus
import java.time.ZoneId

/** Day closed (addendum DayClosed): results per channel, reason, tomorrow's opening, share again. */
@Composable
fun DayClosedScreen(
    summarySent: Boolean,
    zoneId: ZoneId,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayRecordViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val day = record?.day
    val allowed = settings?.allowedDifference ?: 500L
    val summary = rememberDaySummary(
        shopName = settings?.shopName.orEmpty(),
        date = day?.date,
        balance = record?.balance,
        countedCash = day?.countedCash ?: 0L,
        countedMobile = day?.countedMobile ?: 0L,
        allowedDifference = allowed,
        reason = day?.diffReason,
    )
    val sendSummary = rememberSendSummary()

    CenteredPage(modifier = modifier) {
        IconCircle(icon = Icons.Default.Check, tint = colors.green, background = colors.greenTint)
        Text(stringResource(R.string.day_closed_title), fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = colors.ink)
        if (day != null) {
            Text(
                text = stringResource(
                    R.string.closed_on_at,
                    DayFormat.date(day.date, "EEEE d MMM", locale),
                    day.closedAt?.let { DayFormat.time(it, zoneId) }.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
            )
            ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChannelResult(stringResource(R.string.label_cash), day.expectedCash ?: 0L, day.countedCash ?: 0L, allowed)
                    HorizontalDivider(color = colors.line)
                    ChannelResult(stringResource(R.string.label_mobile_money), day.expectedMobile ?: 0L, day.countedMobile ?: 0L, allowed)
                    day.diffReason?.let { reason ->
                        HorizontalDivider(color = colors.line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.reason_given), style = MaterialTheme.typography.bodyLarge, color = colors.muted, modifier = Modifier.weight(1f))
                            Text(reason, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
                        }
                    }
                }
            }
            ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(text = stringResource(R.string.tomorrow_opens_with))
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Amount(stringResource(R.string.label_cash), day.countedCash ?: 0L)
                        Amount(stringResource(R.string.label_mobile_money), day.countedMobile ?: 0L)
                    }
                }
            }
            if (summarySent) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.summary_sent), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.green)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        SecondaryButton(
            text = stringResource(R.string.share_summary_again),
            onClick = { summary?.let(sendSummary) },
            enabled = summary != null,
            leadingIcon = Icons.Outlined.Share,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(text = stringResource(R.string.action_done), onClick = onDone)
    }
}

@Composable
private fun ChannelResult(label: String, expected: Long, counted: Long, allowed: Long) {
    val colors = LocalShopColors.current
    val difference = counted - expected
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                text = stringResource(R.string.expected_counted, Money.format(expected, withCode = false), Money.format(counted, withCode = false)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        when (BalanceCalculator.status(difference, allowed)) {
            BalanceStatus.BALANCED -> Badge(text = stringResource(R.string.status_balanced), kind = BadgeKind.Ok)
            BalanceStatus.SHORT -> Badge(text = stringResource(R.string.short_amount, Money.format(-difference, withCode = false)), kind = BadgeKind.Warn)
            BalanceStatus.OVER -> Badge(text = stringResource(R.string.over_amount, Money.format(difference, withCode = false)), kind = BadgeKind.Warn)
        }
    }
}

@Composable
private fun Amount(label: String, amount: Long) {
    val colors = LocalShopColors.current
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        Text(Money.format(amount), style = amountTextStyle(24.sp), color = colors.ink)
    }
}

/** Yesterday is still open (6.3-1, addendum YesterdayOpen): today stays locked until it is closed. */
@Composable
fun YesterdayOpenScreen(
    zoneId: ZoneId,
    onCloseDay: (date: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayRecordViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val day = record?.day

    CenteredPage(modifier = modifier) {
        IconCircle(icon = Icons.Outlined.WarningAmber, tint = colors.rust, background = colors.rustTint)
        Text(stringResource(R.string.yesterday_open_title), fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = colors.ink)
        if (day != null) {
            val balance = record?.balance
            Text(
                text = stringResource(R.string.yesterday_never_closed, DayFormat.date(day.date, "EEEE d MMM", locale)),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
            )
            ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                Column {
                    SummaryLine(stringResource(R.string.label_opened), DayFormat.time(day.openedAt, zoneId))
                    SummaryLine(stringResource(R.string.label_sales), Money.format(balance?.totalSales ?: 0L))
                    record?.lastSaleAt?.let { SummaryLine(stringResource(R.string.last_sale), DayFormat.time(it, zoneId)) }
                    SummaryLine(stringResource(R.string.expected_cash), Money.format(balance?.cash?.expected ?: 0L), bold = true, last = true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.locked_until_sorted), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (day != null) {
            InkButton(
                text = stringResource(R.string.close_date, DayFormat.date(day.date, "EEE d MMM", locale)),
                onClick = { onCloseDay(day.date) },
                height = 64.dp,
                leadingIcon = Icons.Outlined.Lock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, bold: Boolean = false, last: Boolean = false) {
    val colors = LocalShopColors.current
    Column {
        Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                color = if (bold) colors.ink else colors.muted,
                modifier = Modifier.weight(1f),
            )
            Text(value, style = amountTextStyle(17.sp), color = colors.ink)
        }
        if (!last) HorizontalDivider(color = colors.line)
    }
}

// Full-screen status pages: a centred 560 dp column on tablets, the full width on phones.
@Composable
private fun CenteredPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalShopColors.current
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun IconCircle(
    icon: ImageVector,
    tint: Color,
    background: Color,
) {
    Box(
        modifier = Modifier
            .padding(top = 24.dp)
            .size(72.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
    }
    Spacer(modifier = Modifier.height(4.dp))
}
