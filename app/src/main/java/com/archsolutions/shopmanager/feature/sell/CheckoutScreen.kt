package com.archsolutions.shopmanager.feature.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.data.db.entities.PayMethod

/** Phone Checkout (7.2): full-screen task opened from the cart bar; Sell shows "Sale saved" afterwards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: SellViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedSale by viewModel.savedSale.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.error_could_not_save)
    var linesExpanded by rememberSaveable { mutableStateOf(false) }
    var showNewCustomer by rememberSaveable { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    // A previous sale's snackbar may still be up on Sell; only a sale saved here closes Checkout.
    val saleIdOnEntry = remember { viewModel.savedSale.value?.saleId }

    // Leave once the sale is saved (Sell shows the snackbar) or the cart was emptied here.
    LaunchedEffect(savedSale, state.loaded, state.lines.isEmpty()) {
        val savedHere = savedSale != null && savedSale?.saleId != saleIdOnEntry
        if (!left && (savedHere || (state.loaded && state.lines.isEmpty()))) {
            left = true
            onDone()
        }
    }
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.saveFailedShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    Text(
                        text = stringResource(R.string.checkout_title),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = colors.ink,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.action_complete_sale),
                onClick = viewModel::completeSale,
                enabled = state.canComplete,
                height = 64.dp,
                leadingIcon = Icons.Default.Check,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Credit collapses the lines to a summary to make room for the customer picker.
            if (state.method == PayMethod.CREDIT && !linesExpanded) {
                CartSummaryCard(state = state, onEdit = { linesExpanded = true })
            } else {
                ShopCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.lines.forEachIndexed { index, line ->
                            if (index > 0) HorizontalDivider(color = colors.line)
                            CartLineRow(line = line, onQtyChange = { viewModel.setQty(line.product.id, it) })
                        }
                    }
                }
                TotalRow(total = state.total)
            }
            SectionLabel(text = stringResource(R.string.label_payment))
            PaymentSection(
                state = state,
                viewModel = viewModel,
                customerLabel = stringResource(R.string.who_buys_on_credit),
                changeLabel = stringResource(R.string.change_to_give),
                customerListMaxHeight = 260.dp,
                onNewCustomer = { showNewCustomer = true },
            )
        }
    }

    if (showNewCustomer) {
        NewCustomerSheet(
            initialName = state.customerQuery,
            onDismiss = { showNewCustomer = false },
            onSave = viewModel::addCustomer,
        )
    }
}

@Composable
private fun CartSummaryCard(
    state: SellUiState,
    onEdit: () -> Unit,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = cartSummary(state.lines),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) {
                    Text(
                        text = stringResource(R.string.action_edit),
                        fontWeight = FontWeight.Bold,
                        color = colors.green,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.total_with_items, ItemCountText(state.itemCount)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                )
                Text(
                    text = Money.format(state.total),
                    style = amountTextStyle(30.sp),
                    color = colors.ink,
                )
            }
        }
    }
}
