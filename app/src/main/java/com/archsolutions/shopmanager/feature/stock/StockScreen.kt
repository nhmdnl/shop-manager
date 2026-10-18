package com.archsolutions.shopmanager.feature.stock

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SearchField
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.feature.day.DayGateLockCard
import java.time.ZoneId
import com.archsolutions.shopmanager.core.designsystem.components.NoProductsCard

/** Stock (7.3): tablet = table + Add purchase panel; phone = list (tap = Add purchase, long-press = Edit). */
@Composable
fun StockScreen(
    isExpanded: Boolean,
    gateState: DayGateState,
    zoneId: ZoneId,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    onNewProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onAddPurchase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (isExpanded) {
        StockExpanded(
            state = state,
            viewModel = viewModel,
            gateState = gateState,
            zoneId = zoneId,
            onOpenDay = onOpenDay,
            onCloseDay = onCloseDay,
            onNewProduct = onNewProduct,
            onEditProduct = onEditProduct,
            modifier = modifier,
        )
    } else {
        StockCompact(
            state = state,
            viewModel = viewModel,
            onNewProduct = onNewProduct,
            onEditProduct = onEditProduct,
            onAddPurchase = onAddPurchase,
            modifier = modifier,
        )
    }
}

@Composable
private fun StockExpanded(
    state: StockUiState,
    viewModel: StockViewModel,
    gateState: DayGateState,
    zoneId: ZoneId,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    onNewProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    modifier: Modifier = Modifier,
    purchaseViewModel: PurchaseViewModel = hiltViewModel(),
) {
    val colors = LocalShopColors.current
    val purchase by purchaseViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    PurchaseSnackbarEffects(viewModel = purchaseViewModel, hostState = snackbarHostState)

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
                    .padding(horizontal = 28.dp)
                    .padding(top = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.stock_summary,
                                pluralStringResource(R.plurals.plurals_products, state.productCount, state.productCount),
                                Money.format(state.stockValue),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                        Text(
                            text = stringResource(R.string.nav_stock),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = colors.ink,
                        )
                    }
                    SecondaryButton(
                        text = stringResource(R.string.new_product),
                        onClick = onNewProduct,
                        leadingIcon = Icons.Default.Add,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.search_placeholder),
                )
                Spacer(modifier = Modifier.height(12.dp))
                StockFilterChips(state = state, onSelect = viewModel::setFilter)
                Spacer(modifier = Modifier.height(16.dp))
                if (state.loaded && state.productCount == 0) NoProductsCard(onAddProduct = onNewProduct)
                ShopCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 20.dp),
                    padding = 0.dp,
                ) {
                    LazyColumn {
                        item { StockTableHeader() }
                        itemsIndexed(state.rows, key = { _, row -> row.product.id }) { index, row ->
                            if (index > 0) HorizontalDivider(color = colors.line)
                            StockTableRow(
                                row = row,
                                inPurchase = purchase.contains(row.product.id),
                                onClick = { onEditProduct(row.product.id) },
                                onPurchase = { purchaseViewModel.addProduct(row.product.id) },
                            )
                        }
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val title: @Composable () -> Unit = {
                Text(
                    text = stringResource(R.string.action_add_purchase),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = colors.ink,
                )
            }
            // 6.3: purchases are locked until the day is open; the stock list stays usable.
            if (gateState is DayGateState.Ready) {
                PurchaseForm(state = purchase, viewModel = purchaseViewModel, header = title)
            } else {
                title()
                DayGateLockCard(gateState = gateState, zoneId = zoneId, onOpenDay = onOpenDay, onCloseDay = onCloseDay)
            }
        }
    }
}

@Composable
private fun StockTableHeader() {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        HeaderCell(stringResource(R.string.col_product), Modifier.weight(1.6f))
        HeaderCell(stringResource(R.string.col_category), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.col_in_stock), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.col_alert), Modifier.weight(1f))
        Spacer(modifier = Modifier.width(128.dp))
    }
    HorizontalDivider(color = colors.line)
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LocalShopColors.current.muted,
        modifier = modifier,
    )
}

@Composable
private fun StockTableRow(
    row: StockRow,
    inPurchase: Boolean,
    onClick: () -> Unit,
    onPurchase: () -> Unit,
) {
    val colors = LocalShopColors.current
    val product = row.product
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (inPurchase) colors.greenTint else colors.surface)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            modifier = Modifier.weight(1.6f),
        )
        Text(
            text = row.categoryName.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StockQtyText(qty = product.stockQty, low = row.isLow, size = 20)
            if (row.isLow) Badge(text = stringResource(R.string.low_badge), kind = BadgeKind.Warn)
        }
        Text(
            text = stringResource(R.string.alert_below, Qty.format(product.lowStockLevel)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = onPurchase,
            shape = CircleShape,
            color = colors.surface,
            border = BorderStroke(1.dp, if (inPurchase) colors.green else colors.line),
            modifier = Modifier.width(128.dp),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.action_purchase),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.green,
                )
            }
        }
    }
}

/** Quantity in the display font; rust when low, "Check stock" when negative (5.1.6). */
@Composable
private fun StockQtyText(qty: Double, low: Boolean, size: Int) {
    val colors = LocalShopColors.current
    if (qty < 0) {
        Badge(text = stringResource(R.string.check_stock), kind = BadgeKind.Warn)
    } else {
        Text(
            text = Qty.format(qty),
            style = amountTextStyle(size.sp),
            color = if (low) colors.rust else colors.ink,
        )
    }
}

@Composable
private fun StockFilterChips(state: StockUiState, onSelect: (StockFilter) -> Unit) {
    val allLabel = stringResource(R.string.filter_all_count, state.productCount)
    val lowLabel = stringResource(R.string.filter_low, state.lowCount)
    val names = state.categories.associate { it.id to it.name }
    ChipRow(
        items = listOf(StockFilter.All, StockFilter.Low) + state.categories.map { StockFilter.Category(it.id) },
        selectedItem = state.filter,
        onItemSelected = onSelect,
        labelProvider = { filter ->
            when (filter) {
                StockFilter.All -> allLabel
                StockFilter.Low -> lowLabel
                is StockFilter.Category -> names[filter.id].orEmpty()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun StockCompact(
    state: StockUiState,
    viewModel: StockViewModel,
    onNewProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onAddPurchase: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_stock),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = colors.ink,
                    )
                },
                actions = {
                    FilledIconButton(
                        onClick = onNewProduct,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.green),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.new_product),
                            tint = colors.onGreen,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = stringResource(R.string.search_placeholder),
            )
            StockFilterChips(state = state, onSelect = viewModel::setFilter)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.stock_value), style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                Text(text = Money.format(state.stockValue), style = amountTextStyle(18.sp), color = colors.ink)
            }
            if (state.loaded && state.productCount == 0) NoProductsCard(onAddProduct = onNewProduct)
            ShopCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                padding = 0.dp,
            ) {
                LazyColumn {
                    itemsIndexed(state.rows, key = { _, row -> row.product.id }) { index, row ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onAddPurchase(row.product.id) },
                                    onLongClick = { onEditProduct(row.product.id) },
                                )
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.product.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.ink,
                                )
                                Text(
                                    text = listOfNotNull(row.categoryName, unitLabel(row.product.unit)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                            if (row.isLow) Badge(text = stringResource(R.string.low_badge), kind = BadgeKind.Warn)
                            StockQtyText(qty = row.product.stockQty, low = row.isLow, size = 22)
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.muted,
                            )
                        }
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.action_add_purchase),
                onClick = { onAddPurchase(0L) },
                leadingIcon = Icons.Default.Add,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

/** "Purchase saved · UGX 82,400", or the save error (7.9). */
@Composable
internal fun PurchaseSnackbarEffects(viewModel: PurchaseViewModel, hostState: SnackbarHostState) {
    val savedTotal by viewModel.savedTotal.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val savedMessage = savedTotal?.let { stringResource(R.string.purchase_saved, Money.format(it)) }
    val failedMessage = stringResource(R.string.error_could_not_save)
    LaunchedEffect(savedTotal) {
        if (savedMessage != null) {
            // Clear only afterwards: clearing changes this effect's key and would cancel the snackbar.
            hostState.showSnackbar(savedMessage)
            viewModel.savedShown()
        }
    }
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            hostState.showSnackbar(failedMessage)
            viewModel.saveFailedShown()
        }
    }
}
