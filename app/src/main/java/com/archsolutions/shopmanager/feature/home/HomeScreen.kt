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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.DayBar
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isExpanded: Boolean,
    onNavigateToSell: () -> Unit,
    onNavigateToStock: () -> Unit,
    onNavigateToDay: () -> Unit,
    onNavigateToSettings: () -> Unit,
    shopName: String = "Mary Shop",
    dateString: String = "Wednesday 30 Sep 2026",
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current

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
                            text = dateString,
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
            // DayBar
            DayBar(
                openedSince = stringResource(R.string.day_open_since, "07:58"),
                balancesSummary = stringResource(R.string.opening_cash_momo, "UGX 150,000", "UGX 40,000"),
                onAddExpense = onNavigateToDay,
                onCloseDay = onNavigateToDay,
                isCompact = !isExpanded,
            )

            if (isExpanded) {
                // Tablet: Two big tiles (150 dp tall, radius 24)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Big New Sale Tile
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(150.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onNavigateToSell() },
                        color = colors.green,
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.onGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = colors.onGreen,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.action_new_sale),
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = colors.onGreen,
                                )
                                Text(
                                    text = "Tap products, then Complete",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onGreen.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }

                    // Big Add Purchase Tile
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(150.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onNavigateToStock() },
                        color = colors.surface,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, colors.line),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.segmentTrack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Inventory2,
                                    contentDescription = null,
                                    tint = colors.ink,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.action_add_purchase),
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = colors.ink,
                                )
                                Text(
                                    text = "Stock in from a supplier",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.muted,
                                )
                            }
                        }
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
