package com.archsolutions.shopmanager.feature.sell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.ChipRow
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.QtyStepper
import com.archsolutions.shopmanager.core.designsystem.components.SearchField
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.dao.CustomerWithOwed
import com.archsolutions.shopmanager.data.db.entities.PayMethod
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.data.db.entities.Unit as ProductUnit
import com.archsolutions.shopmanager.domain.CartLine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToLong

/** "Sugar 1kg ×2, Cooking oil 1L" */
internal fun cartSummary(lines: List<CartLine>): String =
    lines.joinToString(", ") { if (it.qty == 1.0) it.product.name else "${it.product.name} ×${Qty.format(it.qty)}" }

@Composable
internal fun payMethodLabel(method: PayMethod): String = stringResource(
    when (method) {
        PayMethod.CASH -> R.string.label_cash
        PayMethod.MOBILE_MONEY -> R.string.label_mobile_money
        PayMethod.CREDIT -> R.string.label_credit
    }
)

@Composable
internal fun CartLineRow(
    line: CartLine,
    onQtyChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.product.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.each_price, Money.format(line.product.sellPrice)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
        QtyStepper(value = line.qty, onValueChange = onQtyChange)
        Text(
            text = Money.format(line.lineTotal, withCode = false),
            style = amountTextStyle(16.sp),
            color = colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}

@Composable
internal fun PaymentTiles(
    selected: PayMethod,
    onSelect: (PayMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PaymentTile(PayMethod.CASH, Icons.Outlined.Payments, selected, onSelect, Modifier.weight(1f))
        PaymentTile(PayMethod.MOBILE_MONEY, Icons.Outlined.PhoneAndroid, selected, onSelect, Modifier.weight(1f))
        PaymentTile(PayMethod.CREDIT, Icons.AutoMirrored.Outlined.ReceiptLong, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun PaymentTile(
    method: PayMethod,
    icon: ImageVector,
    selected: PayMethod,
    onSelect: (PayMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val isSelected = method == selected
    Surface(
        onClick = { onSelect(method) },
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) colors.greenTint else colors.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.green else colors.line),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) colors.green else colors.ink,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = payMethodLabel(method),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) colors.green else colors.ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Quick amounts (5.1.7) fill the field; any other amount can be typed.
@Composable
internal fun CashReceivedSection(
    total: Long,
    quickAmounts: List<Long>,
    cashReceived: Long?,
    onCashReceived: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exactLabel = stringResource(R.string.cash_exact)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(text = stringResource(R.string.cash_received))
        if (quickAmounts.isNotEmpty()) {
            QuickAmountChips(
                amounts = quickAmounts,
                selected = cashReceived,
                labelFor = { if (it == total) exactLabel else Money.format(it, withCode = false) },
                onSelect = onCashReceived,
            )
        }
        MoneyField(value = cashReceived, onValueChange = onCashReceived)
    }
}

// Equal-width chips so all four amounts fit the 400 dp tablet panel without scrolling.
@Composable
private fun QuickAmountChips(
    amounts: List<Long>,
    selected: Long?,
    labelFor: (Long) -> String,
    onSelect: (Long) -> Unit,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        amounts.forEach { amount ->
            val isSelected = amount == selected
            Surface(
                onClick = { onSelect(amount) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = CircleShape,
                color = if (isSelected) colors.ink else colors.surface,
                border = if (isSelected) null else BorderStroke(1.dp, colors.line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = labelFor(amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) colors.surface else colors.ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChangeRow(
    label: String,
    change: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.greenTint)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.green,
        )
        Text(
            text = Money.format(change),
            style = amountTextStyle(22.sp),
            color = colors.green,
        )
    }
}

@Composable
internal fun CustomerPicker(
    label: String,
    query: String,
    customers: List<CustomerWithOwed>,
    selected: CustomerWithOwed?,
    onQueryChange: (String) -> Unit,
    onSelect: (Long) -> Unit,
    onNewCustomer: () -> Unit,
    modifier: Modifier = Modifier,
    listMaxHeight: Dp = Dp.Unspecified,
) {
    val colors = LocalShopColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(text = label)
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_or_add_customer),
        )
        ShopCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Column(
                modifier = Modifier
                    .heightIn(max = listMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                customers.forEach { item ->
                    CustomerRow(
                        item = item,
                        isSelected = item.customer.id == selected?.customer?.id,
                        onClick = { onSelect(item.customer.id) },
                    )
                    HorizontalDivider(color = colors.line)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNewCustomer)
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.greenTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = stringResource(R.string.new_customer),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.green,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(
    item: CustomerWithOwed,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSelected) colors.greenTint else colors.segmentTrack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = if (isSelected) colors.green else colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.customer.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
            )
            val (balance, balanceColor) = when {
                item.owed > 0 -> stringResource(R.string.owes_amount, Money.format(item.owed)) to colors.rust
                item.owed < 0 -> stringResource(R.string.has_credit, Money.format(-item.owed)) to colors.green
                else -> stringResource(R.string.owes_nothing) to colors.muted
            }
            Text(text = balance, style = MaterialTheme.typography.bodyMedium, color = balanceColor)
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = colors.green, unselectedColor = colors.muted),
        )
    }
}

/** "Joseph K. will owe UGX 34,500 after this sale. Not counted in cash or Mobile Money." */
@Composable
internal fun CreditInfoBox(
    customerName: String,
    owedAfterSale: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Text(
        text = stringResource(R.string.will_owe, customerName, Money.format(owedAfterSale)) +
            ". " + stringResource(R.string.credit_not_counted),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.ink,
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(colors.muted, 14.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

/** Small rounded "Clear" button from the design. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalShopColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.segmentTrack,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.ink else colors.muted,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** Long-press on a tile (addendum QtyDialog): big stepper, quick chips, decimals for kg and litre. */
@Composable
internal fun QuantityDialog(
    product: ProductEntity,
    initialQty: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val colors = LocalShopColors.current
    val allowsDecimals = product.unit == ProductUnit.KG || product.unit == ProductUnit.LITRE
    var text by rememberSaveable { mutableStateOf(Qty.format(initialQty)) }
    val qty = Qty.parse(text) ?: 0.0
    val step = 1.0

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.surface) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column {
                    Text(stringResource(R.string.how_many), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    Text(
                        text = product.name,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = colors.ink,
                    )
                    Text(
                        text = stringResource(
                            R.string.price_each_in_stock,
                            Money.format(product.sellPrice),
                            Qty.format(product.stockQty),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BigStepButton(Icons.Default.Remove) { text = Qty.format((qty - step).coerceAtLeast(0.0)) }
                    BasicTextField(
                        value = text,
                        onValueChange = { input ->
                            val cleaned = if (allowsDecimals) input.filter { it.isDigit() || it == '.' || it == ',' } else input.filter(Char::isDigit)
                            text = cleaned.take(7)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (allowsDecimals) KeyboardType.Decimal else KeyboardType.Number,
                        ),
                        textStyle = amountTextStyle(52.sp).copy(color = colors.ink, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(colors.green),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    BigStepButton(Icons.Default.Add) { text = Qty.format(qty + step) }
                }
                ChipRow(
                    items = listOf(1.0, 5.0, 10.0, 12.0, 24.0),
                    selectedItem = qty,
                    onItemSelected = { text = Qty.format(it) },
                    labelProvider = { Qty.format(it) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.line_total), style = MaterialTheme.typography.bodyLarge, color = colors.muted)
                    Text(
                        text = Money.format((qty * product.sellPrice).roundToLong()),
                        style = amountTextStyle(20.sp),
                        color = colors.ink,
                    )
                }
                Text(stringResource(R.string.qty_decimals_hint), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.add_qty_to_sale, Qty.format(qty)),
                        onClick = { onConfirm(qty) },
                        enabled = qty > 0.0,
                        height = 56.dp,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BigStepButton(icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalShopColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.paper,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(28.dp))
        }
    }
}

/** Quick add from the credit picker (addendum CustomerAdd): name required, phone optional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewCustomerSheet(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit,
) {
    val colors = LocalShopColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf("") }
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.new_customer),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.ink,
            )
            FieldLabel(stringResource(R.string.label_name))
            ShopTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "",
                capitalization = KeyboardCapitalization.Words,
            )
            FieldLabel(stringResource(R.string.label_phone_optional))
            ShopTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = stringResource(R.string.phone_placeholder),
                keyboardType = KeyboardType.Phone,
                capitalization = KeyboardCapitalization.None,
            )
            Text(stringResource(R.string.phone_reminder_hint), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = close,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.save_and_choose),
                    onClick = {
                        onSave(name, phone)
                        close()
                    },
                    enabled = name.isNotBlank(),
                    height = 56.dp,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalShopColors.current.ink,
    )
}

private class SaleSavedVisuals(
    override val message: String,
    override val actionLabel: String,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/** Ink snackbar: "Sale saved" with the payment summary and Undo; other messages use the default look. */
@Composable
internal fun SaleSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        if (data.visuals is SaleSavedVisuals) {
            Snackbar(
                containerColor = colors.ink,
                contentColor = colors.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(12.dp),
                action = {
                    TextButton(onClick = { data.performAction() }) {
                        Text(
                            text = data.visuals.actionLabel.orEmpty(),
                            color = colors.greenTint,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                    }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.greenTint)
                    Column {
                        Text(stringResource(R.string.sale_saved), fontWeight = FontWeight.Bold, color = colors.surface)
                        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = colors.surface)
                    }
                }
            }
        } else {
            Snackbar(snackbarData = data)
        }
    }
}

/**
 * Shows "Sale saved" for six seconds after a sale (addendum SaleSaved); Undo voids it without a PIN (C2).
 * Also reports save failures, keeping the cart (7.9).
 */
@Composable
internal fun SaleSnackbarEffects(viewModel: SellViewModel, hostState: SnackbarHostState) {
    val savedSale by viewModel.savedSale.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val undoLabel = stringResource(R.string.action_undo)
    val failedMessage = stringResource(R.string.error_could_not_save)
    val summary = savedSale?.let { sale ->
        val method = payMethodLabel(sale.method)
        if (sale.change != null && sale.change > 0) {
            stringResource(R.string.sale_saved_summary_change, method, Money.format(sale.total), Money.format(sale.change))
        } else {
            stringResource(R.string.sale_saved_summary, method, Money.format(sale.total))
        }
    }

    LaunchedEffect(savedSale?.saleId) {
        val sale = savedSale ?: return@LaunchedEffect
        val remaining = SALE_SAVED_MILLIS - (System.nanoTime() / 1_000_000 - sale.savedAtMillis)
        val result = if (remaining > 0) {
            withTimeoutOrNull(remaining) { hostState.showSnackbar(SaleSavedVisuals(summary.orEmpty(), undoLabel)) }
        } else {
            null
        }
        if (result == SnackbarResult.ActionPerformed) viewModel.undoSale(sale.saleId)
        viewModel.savedSaleShown()
    }
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            hostState.showSnackbar(failedMessage)
            viewModel.saveFailedShown()
        }
    }
}

@Composable
internal fun ItemCountText(count: Int): String = pluralStringResource(R.plurals.plurals_items, count, count)

private const val SALE_SAVED_MILLIS = 6_000L
