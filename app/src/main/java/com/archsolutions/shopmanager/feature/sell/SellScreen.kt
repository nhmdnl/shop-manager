package com.archsolutions.shopmanager.feature.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.ChipRow
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ProductTile
import com.archsolutions.shopmanager.core.designsystem.components.SearchField
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.domain.isLowStock
import com.archsolutions.shopmanager.feature.day.DayGateLockCard
import java.time.ZoneId

/** Sell (7.2): tablet = product grid + Current sale panel; phone = grid + cart bar, then Checkout. */
@Composable
fun SellScreen(
    isExpanded: Boolean,
    gateState: DayGateState,
    zoneId: ZoneId,
    viewModel: SellViewModel,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    var quantityFor by remember { mutableStateOf<ProductEntity?>(null) }
    var showNewCustomer by rememberSaveable { mutableStateOf(false) }

    SaleSnackbarEffects(viewModel = viewModel, hostState = snackbarHostState)

    if (gateState !is DayGateState.Ready) {
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
                snackbarHost = { SaleSnackbarHost(snackbarHostState) },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(horizontal = 28.dp)
                        .padding(top = 20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_new_sale),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = colors.ink,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ProductBrowser(
                        state = state,
                        columns = 5,
                        tileHeight = 112.dp,
                        onQuery = viewModel::setQuery,
                        onCategory = viewModel::setCategory,
                        onTap = viewModel::addOne,
                        onLongPress = { quantityFor = it },
                    )
                }
            }
            VerticalDivider(color = colors.line)
            CurrentSalePanel(
                state = state,
                viewModel = viewModel,
                onNewCustomer = { showNewCustomer = true },
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight(),
            )
        }
    } else {
        SellCompact(
            state = state,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onCheckout = onCheckout,
            onLongPress = { quantityFor = it },
            modifier = modifier,
        )
    }

    quantityFor?.let { product ->
        QuantityDialog(
            product = product,
            initialQty = state.qtyOf(product.id) ?: 1.0,
            onDismiss = { quantityFor = null },
            onConfirm = { qty ->
                viewModel.setQty(product.id, qty)
                quantityFor = null
            },
        )
    }
    if (showNewCustomer) {
        NewCustomerSheet(
            initialName = state.customerQuery,
            onDismiss = { showNewCustomer = false },
            onSave = viewModel::addCustomer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SellCompact(
    state: SellUiState,
    viewModel: SellViewModel,
    snackbarHostState: SnackbarHostState,
    onCheckout: () -> Unit,
    onLongPress: (ProductEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SaleSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.action_new_sale),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = colors.ink,
                    )
                },
                actions = {
                    if (state.lines.isNotEmpty()) {
                        PillButton(text = stringResource(R.string.action_clear), onClick = viewModel::clear)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
        bottomBar = {
            if (state.lines.isNotEmpty()) {
                CartBar(itemCount = state.itemCount, total = state.total, onCharge = onCheckout)
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            ProductBrowser(
                state = state,
                columns = 3,
                tileHeight = 104.dp,
                onQuery = viewModel::setQuery,
                onCategory = viewModel::setCategory,
                onTap = viewModel::addOne,
                onLongPress = onLongPress,
            )
        }
    }
}

// Search, category chips and the product grid; tap adds one, long-press opens the quantity dialog.
@Composable
private fun ProductBrowser(
    state: SellUiState,
    columns: Int,
    tileHeight: Dp,
    onQuery: (String) -> Unit,
    onCategory: (Long?) -> Unit,
    onTap: (Long) -> Unit,
    onLongPress: (ProductEntity) -> Unit,
) {
    val colors = LocalShopColors.current
    SearchField(
        value = state.query,
        onValueChange = onQuery,
        placeholder = stringResource(R.string.search_placeholder),
    )
    Spacer(modifier = Modifier.height(12.dp))
    CategoryChips(categories = state.categories, selected = state.categoryId, onSelect = onCategory)
    Spacer(modifier = Modifier.height(16.dp))
    if (state.loaded && state.products.isEmpty()) {
        Text(
            text = stringResource(R.string.empty_products),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )
        return
    }
    val checkStock = stringResource(R.string.check_stock)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, end = 4.dp, bottom = 20.dp),
    ) {
        items(state.products, key = { it.id }) { product ->
            ProductTile(
                name = product.name,
                priceFormatted = Money.format(product.sellPrice, withCode = false),
                // 5.1.6: stock can go negative; the tile asks for a stock check instead of blocking.
                stockLeftFormatted = if (product.stockQty < 0) {
                    checkStock
                } else {
                    stringResource(R.string.stock_left, Qty.format(product.stockQty))
                },
                isLowStock = product.isLowStock,
                selectedQuantity = state.qtyOf(product.id)?.let(Qty::format),
                onTap = { onTap(product.id) },
                onLongPress = { onLongPress(product) },
                height = tileHeight,
            )
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<CategoryEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    val allLabel = stringResource(R.string.filter_all)
    val names = categories.associate { it.id to it.name }
    ChipRow(
        items = listOf<Long?>(null) + categories.map { it.id },
        selectedItem = selected,
        onItemSelected = onSelect,
        labelProvider = { id -> id?.let { names[it] } ?: allLabel },
    )
}

// Tablet right panel: lines scroll, the total and payment stay at the bottom while there is room.
@Composable
private fun CurrentSalePanel(
    state: SellUiState,
    viewModel: SellViewModel,
    onNewCustomer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Column(
        modifier = modifier
            .background(colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.current_sale),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.ink,
            )
            PillButton(
                text = stringResource(R.string.action_clear),
                onClick = viewModel::clear,
                enabled = state.lines.isNotEmpty(),
            )
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(modifier = Modifier.heightIn(min = viewportHeight)) {
                    if (state.lines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_new_sale_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.muted,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    state.lines.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        CartLineRow(line = line, onQtyChange = { viewModel.setQty(line.product.id, it) })
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 12.dp))
                    TotalRow(total = state.total, modifier = Modifier.padding(vertical = 12.dp))
                    PaymentSection(
                        state = state,
                        viewModel = viewModel,
                        customerLabel = stringResource(R.string.label_customer),
                        changeLabel = stringResource(R.string.label_change),
                        customerListMaxHeight = 180.dp,
                        onNewCustomer = onNewCustomer,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        PrimaryButton(
            text = stringResource(R.string.action_complete_sale),
            onClick = viewModel::completeSale,
            enabled = state.canComplete,
            height = 64.dp,
            leadingIcon = Icons.Default.Check,
        )
    }
}

@Composable
internal fun TotalRow(
    total: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.label_total),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )
        Text(
            text = Money.format(total),
            style = amountTextStyle(34.sp),
            color = colors.ink,
        )
    }
}

/** Payment tiles plus what each method needs: cash received and change, or the credit customer. */
@Composable
internal fun PaymentSection(
    state: SellUiState,
    viewModel: SellViewModel,
    customerLabel: String,
    changeLabel: String,
    customerListMaxHeight: Dp,
    onNewCustomer: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PaymentTiles(selected = state.method, onSelect = viewModel::setMethod)
        when (state.method) {
            PayMethod.CASH -> {
                CashReceivedSection(
                    total = state.total,
                    quickAmounts = state.quickAmounts,
                    cashReceived = state.cashReceived,
                    onCashReceived = viewModel::setCashReceived,
                )
                state.change?.let { ChangeRow(label = changeLabel, change = it) }
            }
            PayMethod.CREDIT -> {
                CustomerPicker(
                    label = customerLabel,
                    query = state.customerQuery,
                    customers = state.customers,
                    selected = state.selectedCustomer,
                    onQueryChange = viewModel::setCustomerQuery,
                    onSelect = viewModel::selectCustomer,
                    onNewCustomer = onNewCustomer,
                    listMaxHeight = customerListMaxHeight,
                )
                val customer = state.selectedCustomer
                val owedAfter = state.owedAfterSale
                if (customer != null && owedAfter != null) {
                    CreditInfoBox(customerName = customer.customer.name, owedAfterSale = owedAfter)
                }
            }
            PayMethod.MOBILE_MONEY -> Unit
        }
    }
}

// Phone: sticky ink bar above the navigation bar ("6 items · UGX 22,500 · Charge ›").
@Composable
private fun CartBar(
    itemCount: Int,
    total: Long,
    onCharge: () -> Unit,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.ink)
            .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ItemCountText(itemCount),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.surface.copy(alpha = 0.75f),
            )
            Text(
                text = Money.format(total),
                style = amountTextStyle(22.sp),
                color = colors.surface,
            )
        }
        Surface(
            onClick = onCharge,
            shape = RoundedCornerShape(14.dp),
            color = colors.green,
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.action_charge),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onGreen,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onGreen,
                )
            }
        }
    }
}
