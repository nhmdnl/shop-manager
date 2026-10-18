package com.archsolutions.shopmanager.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.archsolutions.shopmanager.core.designsystem.components.LockedActionButton
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.feature.day.DayGateLockCard
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
    onNavigateToDay: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val tradingOpen = gateState is DayGateState.Ready

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
                    onAddExpense = onNavigateToDay,
                    onCloseDay = onNavigateToDay,
                    isCompact = !isExpanded,
                )
            } else {
                DayGateLockCard(
                    gateState = gateState,
                    zoneId = zoneId,
                    onOpenDay = onOpenDay,
                    onCloseDay = onNavigateToDay,
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
                            text = stringResource(R.string.action_new_sale),
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
                        modifier = Modifier.weight(1f),
                        onAddPurchase = onNavigateToStock,
                    )
                    RecentSalesCard(
                        modifier = Modifier.weight(1f),
                        onSeeAll = onNavigateToSell,
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
                            onClick = onNavigateToDay,
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.Outlined.ReceiptLong,
                        )
                    }
                } else {
                    LockedActionButton(
                        text = stringResource(R.string.action_new_sale),
                        modifier = Modifier.fillMaxWidth(),
                        height = 72.dp,
                        cornerRadius = 20.dp,
                    )

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

                LowStockCard(
                    modifier = Modifier.fillMaxWidth(),
                    onAddPurchase = onNavigateToStock,
                )
                RecentSalesCard(
                    modifier = Modifier.fillMaxWidth(),
                    onSeeAll = onNavigateToSell,
                )
            }
        }
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

            // In Milestone 1, show sample or empty state
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
    }
}

@Composable
private fun RecentSalesCard(
    onSeeAll: () -> Unit,
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
                SectionLabel(text = stringResource(R.string.recent_sales))
                Text(
                    text = stringResource(R.string.action_see_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSeeAll() },
                )
            }

            Text(
                text = stringResource(R.string.empty_reports),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}
