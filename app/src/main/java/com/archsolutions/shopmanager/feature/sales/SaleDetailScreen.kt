package com.archsolutions.shopmanager.feature.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.archsolutions.shopmanager.core.designsystem.components.ChipRow
import com.archsolutions.shopmanager.core.designsystem.components.InkButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/** Sale detail (addendum SaleDetail): items, totals, and "Void this sale" for the open day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(
    isExpanded: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SaleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val voidFailed by viewModel.voidFailed.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.error_could_not_save)
    var showVoidSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(voidFailed) {
        if (voidFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.voidFailedShown()
        }
    }

    val methodText = if (state.method == PayMethod.CREDIT) {
        stringResource(R.string.credit_customer, state.customerName.orEmpty())
    } else {
        stringResource(if (state.method == PayMethod.CASH) R.string.label_cash else R.string.label_mobile_money)
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
                    Column {
                        Text(
                            text = stringResource(R.string.sale_title, state.time),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 32.sp else 24.sp,
                            color = colors.ink,
                        )
                        if (state.date.isNotEmpty()) {
                            Text(
                                text = DayFormat.date(state.date, "EEE d MMM", locale) + " · " + methodText,
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
        if (!state.loaded || state.notFound) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 12.dp)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = colors.line)
                        DetailRow(
                            label = stringResource(R.string.sale_qty_line, item.productName, Qty.format(item.qty)),
                            value = Money.format((item.qty * item.unitPrice).roundToLong(), withCode = false),
                        )
                    }
                    HorizontalDivider(color = colors.line)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.label_total),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Money.format(state.total),
                            style = amountTextStyle(22.sp),
                            color = colors.ink,
                        )
                    }
                    state.cashReceived?.let { cash ->
                        HorizontalDivider(color = colors.line)
                        DetailRow(
                            label = stringResource(R.string.cash_received_change),
                            value = Money.format(cash, withCode = false) + " · " +
                                Money.format(state.change ?: 0L, withCode = false),
                            muted = true,
                        )
                    }
                }
            }
            if (state.voided) {
                Badge(
                    text = stringResource(R.string.voided_reason, state.voidReason.orEmpty()),
                    kind = BadgeKind.Muted,
                )
            }
            if (state.canVoid) {
                SecondaryButton(
                    text = stringResource(R.string.void_this_sale_action),
                    onClick = { showVoidSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Outlined.Close,
                )
            }
        }
    }

    if (showVoidSheet) {
        VoidSaleSheet(
            pinEnabled = state.pinEnabled,
            voiding = state.voiding,
            onDismiss = { showVoidSheet = false },
            onVoid = { reason, note -> viewModel.void(reason, note) },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, muted: Boolean = false) {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) colors.muted else colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = amountTextStyle(16.sp),
            color = colors.ink,
        )
    }
}

// Void sheet: reason chips, optional note, PIN notice when enabled (5.2, addendum C4).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoidSaleSheet(
    pinEnabled: Boolean,
    voiding: Boolean,
    onDismiss: () -> Unit,
    onVoid: (reason: String, note: String) -> Unit,
) {
    val colors = LocalShopColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val reasons = listOf(
        stringResource(R.string.reason_wrong_item),
        stringResource(R.string.reason_customer_returned),
        stringResource(R.string.reason_wrong_amount),
        stringResource(R.string.reason_other),
    )
    var reason by rememberSaveable { mutableStateOf(reasons.first()) }
    var note by rememberSaveable { mutableStateOf("") }
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.void_this_sale),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.void_explain),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            SectionLabel(text = stringResource(R.string.label_reason))
            ChipRow(
                items = reasons,
                selectedItem = reason,
                onItemSelected = { reason = it },
            )
            Text(
                text = stringResource(R.string.note_optional),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
            )
            ShopTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(R.string.note_placeholder),
            )
            if (pinEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.pin_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.keep_sale),
                    onClick = close,
                    modifier = Modifier.weight(1f),
                )
                InkButton(
                    text = stringResource(R.string.void_sale),
                    onClick = {
                        onVoid(reason, note)
                        close()
                    },
                    enabled = !voiding,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
