package com.archsolutions.shopmanager.feature.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.db.entities.PayMethod

/** Today's sales (addendum Sales): newest first, filter chips, voided rows struck through. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    isExpanded: Boolean,
    onBack: () -> Unit,
    onOpenSale: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SalesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val filterLabels = mapOf(
        SalesFilter.ALL to stringResource(R.string.filter_all),
        SalesFilter.CASH to stringResource(R.string.label_cash),
        SalesFilter.MOBILE_MONEY to stringResource(R.string.label_mobile_money),
        SalesFilter.CREDIT to stringResource(R.string.label_credit),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = colors.ink,
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.todays_sales),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 32.sp else 24.sp,
                            color = colors.ink,
                        )
                        state.date?.let { date ->
                            Text(
                                text = stringResource(
                                    R.string.sales_summary,
                                    DayFormat.date(date, "EEE d MMM", locale),
                                    pluralStringResource(R.plurals.plurals_sales, state.saleCount, state.saleCount),
                                    Money.format(state.salesTotal),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (isExpanded) 28.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ChipRow(
                    items = SalesFilter.entries,
                    selectedItem = state.filter,
                    onItemSelected = viewModel::setFilter,
                    labelProvider = { filterLabels.getValue(it) },
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
            item {
                ShopCard(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                    padding = 0.dp,
                ) {
                    Column {
                        if (state.loaded && state.rows.isEmpty()) {
                            Text(
                                text = stringResource(R.string.empty_reports),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            )
                        }
                        state.rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(color = colors.line)
                            SaleListRow(row = row, onClick = { onOpenSale(row.saleId) })
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.voided_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

/** One sale row: time, items with a payment badge, amount. Also used by Home's Recent sales card. */
@Composable
fun SaleListRow(
    row: SaleRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val decoration = if (row.voided) TextDecoration.LineThrough else null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.time,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.width(56.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = row.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.voided) colors.muted else colors.ink,
                textDecoration = decoration,
            )
            Box {
                when {
                    row.voided -> Badge(
                        text = stringResource(R.string.voided_reason, row.voidReason.orEmpty()),
                        kind = BadgeKind.Muted,
                    )
                    row.method == PayMethod.CREDIT -> Badge(
                        text = stringResource(R.string.credit_customer, row.customerName.orEmpty()),
                        kind = BadgeKind.Warn,
                    )
                    else -> Badge(
                        text = stringResource(
                            if (row.method == PayMethod.CASH) R.string.label_cash else R.string.label_mobile_money
                        ),
                        kind = BadgeKind.Muted,
                    )
                }
            }
        }
        Text(
            text = Money.format(row.total, withCode = false),
            style = amountTextStyle(17.sp),
            color = if (row.voided) colors.muted else colors.ink,
            textDecoration = decoration,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
