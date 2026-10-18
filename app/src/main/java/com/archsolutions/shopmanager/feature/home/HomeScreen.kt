package com.archsolutions.shopmanager.feature.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.DayBar
import com.archsolutions.shopmanager.core.designsystem.components.InkButton
import com.archsolutions.shopmanager.core.designsystem.components.LockedActionButton
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.dao.LowStockItem
import com.archsolutions.shopmanager.data.db.entities.BusinessDayEntity
import com.archsolutions.shopmanager.feature.day.DayGateLockCard
import com.archsolutions.shopmanager.feature.sales.SaleListRow
import com.archsolutions.shopmanager.feature.sales.SaleRow
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isExpanded: Boolean,
    shopName: String,
    today: String,
    gateState: DayGateState,
    zoneId: ZoneId,
    onNavigateToSell: () -> Unit,
    onNavigateToStock: () -> Unit,
    onAddExpense: () -> Unit,
    onCloseDay: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenDay: () -> Unit,
    recentSales: List<SaleRow>,
    lowStock: List<LowStockItem>,
    onSeeAllSales: () -> Unit,
    onOpenSale: (Long) -> Unit,
    todaySummary: TodaySummary?,
    onDaySummary: (dayId: Long) -> Unit,
    onReopenDay: (dayId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val tradingOpen = gateState is DayGateState.Ready
    val dayClosed = gateState is DayGateState.TodayClosed
    var confirmReopen by remember { mutableStateOf(false) }
    val newSaleLocked = stringResource(if (dayClosed) R.string.selling_opens_tomorrow else R.string.action_new_sale)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = shopName,
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 34.sp else 24.sp,
                            color = colors.ink,
                        )
                        Text(
                            text = DayFormat.date(today, "EEEE d MMM yyyy", locale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                },
                actions = {
                    if (!isExpanded) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                                tint = colors.ink,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (gateState is DayGateState.Ready) {
                DayBar(
                    openedSince = stringResource(R.string.day_open_since, DayFormat.time(gateState.day.openedAt, zoneId)),
                    balancesSummary = stringResource(
                        R.string.opening_cash_momo,
                        Money.format(gateState.day.openingCash),
                        Money.format(gateState.day.openingMobile),
                    ),
                    onAddExpense = onAddExpense,
                    onCloseDay = onCloseDay,
                    isCompact = !isExpanded,
                )
            } else if (gateState is DayGateState.TodayClosed) {
                DayClosedBar(
                    day = gateState.day,
                    zoneId = zoneId,
                    isCompact = !isExpanded,
                    onSummary = { onDaySummary(gateState.day.id) },
                    onReopen = { confirmReopen = true },
                )
            } else {
                DayGateLockCard(
                    gateState = gateState,
                    zoneId = zoneId,
                    onOpenDay = onOpenDay,
                    onCloseDay = onCloseDay,
                )
            }

            if (isExpanded) {
                // Tablet: two big tiles (150 dp tall, radius 24)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (tradingOpen) {
                        HomeTile(
                            title = stringResource(R.string.action_new_sale),
                            subtitle = stringResource(R.string.home_new_sale_hint),
                            icon = Icons.Default.Add,
                            primary = true,
                            onClick = onNavigateToSell,
                            modifier = Modifier.weight(1f),
                        )
                        HomeTile(
                            title = stringResource(R.string.action_add_purchase),
                            subtitle = stringResource(R.string.home_add_purchase_hint),
                            icon = Icons.Outlined.Inventory2,
                            primary = false,
                            onClick = onNavigateToStock,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LockedActionButton(
                            text = newSaleLocked,
                            modifier = Modifier.weight(1f),
                            height = 150.dp,
                            cornerRadius = 24.dp,
                        )
                        LockedActionButton(
                            text = stringResource(R.string.action_add_purchase),
                            modifier = Modifier.weight(1f),
                            height = 150.dp,
                            cornerRadius = 24.dp,
                        )
                    }
                }

                // Tablet 2-column cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    LowStockCard(
                        items = lowStock,
                        onAddPurchase = onNavigateToStock,
                        modifier = Modifier.weight(1f),
                    )
                    RecentSalesCard(
                        sales = recentSales,
                        onSeeAll = onSeeAllSales,
                        onOpenSale = onOpenSale,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // Phone: New sale primary button (72 dp), then Row of Purchase and Expense, then Cards
                if (tradingOpen) {
                    PrimaryButton(
                        text = stringResource(R.string.action_new_sale),
                        onClick = onNavigateToSell,
                        height = 72.dp,
                        leadingIcon = Icons.Default.Add,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SecondaryButton(
                            text = stringResource(R.string.action_add_purchase),
                            onClick = onNavigateToStock,
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Outlined.Inventory2,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.action_add_expense),
                            onClick = onAddExpense,
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Outlined.ReceiptLong,
                        )
                    }
                } else {
                    LockedActionButton(
                        text = newSaleLocked,
                        modifier = Modifier.fillMaxWidth(),
                        height = 72.dp,
                        cornerRadius = 20.dp,
                    )

                    if (dayClosed) {
                        Text(
                            text = stringResource(R.string.closed_read_only_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                        todaySummary?.let { TodayCard(summary = it, onSeeSales = onSeeAllSales) }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LockedActionButton(
                                text = stringResource(R.string.action_add_purchase),
                                modifier = Modifier.weight(1f),
                            )
                            LockedActionButton(
                                text = stringResource(R.string.action_add_expense),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                LowStockCard(
                    items = lowStock,
                    onAddPurchase = onNavigateToStock,
                    modifier = Modifier.fillMaxWidth(),
                )
                RecentSalesCard(
                    sales = recentSales,
                    onSeeAll = onSeeAllSales,
                    onOpenSale = onOpenSale,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    val closedDay = (gateState as? DayGateState.TodayClosed)?.day
    if (confirmReopen && closedDay != null) {
        AlertDialog(
            onDismissRequest = { confirmReopen = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.reopen_confirm_title)) },
            text = { Text(stringResource(R.string.reopen_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReopen = false
                    onReopenDay(closedDay.id)
                }) {
                    Text(stringResource(R.string.action_reopen_day), color = colors.green, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReopen = false }) {
                    Text(stringResource(R.string.action_cancel), color = colors.ink)
                }
            },
        )
    }
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val contentColor = if (primary) colors.onGreen else colors.ink
    Surface(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        color = if (primary) colors.green else colors.surface,
        shape = RoundedCornerShape(24.dp),
        border = if (primary) null else BorderStroke(1.dp, colors.line),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (primary) colors.onGreen.copy(alpha = 0.2f) else colors.segmentTrack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (primary) colors.onGreen.copy(alpha = 0.85f) else colors.muted,
                )
            }
        }
    }
}

@Composable
private fun LowStockCard(
    items: List<LowStockItem>,
    onAddPurchase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = stringResource(R.string.label_low_stock))
                Text(
                    text = stringResource(R.string.action_add_purchase),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onAddPurchase() },
                )
            }

            if (items.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.green,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.empty_low_stock),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = colors.line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                        )
                        item.supplierName?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                    }
                    Text(
                        text = if (item.stockQty < 0) {
                            stringResource(R.string.check_stock)
                        } else {
                            stringResource(R.string.stock_left, Qty.format(item.stockQty))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.rust,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSalesCard(
    sales: List<SaleRow>,
    onSeeAll: () -> Unit,
    onOpenSale: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier, padding = 0.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = stringResource(R.string.recent_sales))
                Text(
                    text = stringResource(R.string.action_see_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSeeAll() },
                )
            }

            if (sales.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_reports),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
            sales.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = colors.line)
                SaleListRow(row = row, onClick = { onOpenSale(row.saleId) })
            }
        }
    }
}

/** 6.3-3: the closed day's counts, Summary and Reopen day, in place of the DayBar. */
@Composable
private fun DayClosedBar(
    day: BusinessDayEntity,
    zoneId: ZoneId,
    isCompact: Boolean,
    onSummary: () -> Unit,
    onReopen: () -> Unit,
) {
    val colors = LocalShopColors.current
    val counted = stringResource(
        R.string.counted_summary,
        Money.format(day.countedCash ?: 0L),
        Money.format(day.countedMobile ?: 0L),
    )
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        val info: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.greenTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.green)
                }
                Column {
                    Text(
                        text = stringResource(R.string.day_closed_at, day.closedAt?.let { DayFormat.time(it, zoneId) }.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                    )
                    Text(
                        text = if (isCompact) counted else counted + " · " + stringResource(R.string.read_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }
        }
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                info()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.action_summary),
                        onClick = onSummary,
                        leadingIcon = Icons.Outlined.ReceiptLong,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_reopen_day),
                        onClick = onReopen,
                        leadingIcon = Icons.Outlined.Lock,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { info() }
                SecondaryButton(
                    text = stringResource(R.string.action_summary),
                    onClick = onSummary,
                    leadingIcon = Icons.Outlined.ReceiptLong,
                    height = 48.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                InkButton(
                    text = stringResource(R.string.action_reopen_day),
                    onClick = onReopen,
                    leadingIcon = Icons.Outlined.Lock,
                    height = 48.dp,
                )
            }
        }
    }
}

/** Phone Home after closing: today's sales, estimated profit and what customers owe. */
@Composable
private fun TodayCard(summary: TodaySummary, onSeeSales: () -> Unit) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = stringResource(R.string.today_label), modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.see_sales),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onSeeSales),
                )
            }
            TodayLine(stringResource(R.string.label_sales), Money.format(summary.sales), colors.ink)
            HorizontalDivider(color = colors.line)
            TodayLine(stringResource(R.string.profit_est), Money.format(summary.profit), colors.green)
            HorizontalDivider(color = colors.line)
            TodayLine(stringResource(R.string.label_owed_to_shop), Money.format(summary.owed), colors.ink)
        }
    }
}

@Composable
private fun TodayLine(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = LocalShopColors.current.muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
