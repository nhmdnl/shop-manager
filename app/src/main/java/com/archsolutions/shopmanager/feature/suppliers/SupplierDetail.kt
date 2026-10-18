package com.archsolutions.shopmanager.feature.suppliers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.Phone
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.core.util.WhatsApp
import com.archsolutions.shopmanager.data.db.dao.SupplierProductRow
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.domain.OrderTemplates
import com.archsolutions.shopmanager.domain.Reorder
import com.archsolutions.shopmanager.domain.isLow

/** Supplier detail on phones (7.5); tablets show [SupplierDetailPane] beside the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRecordPurchase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SupplierDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val supplier = state.supplier

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = { SupplierTitle(supplier, large = false) },
                actions = {
                    if (supplier != null) {
                        IconButton(onClick = { onEdit(supplier.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_supplier), tint = colors.ink)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ContactButtons(supplier, buttonModifier = Modifier.weight(1f))
            }
            ReorderCard(state = state, onToggle = viewModel::toggle, onQty = viewModel::setQty)
            SendOrderButton(state)
            SecondaryButton(
                text = stringResource(R.string.record_purchase),
                onClick = { supplier?.let { onRecordPurchase(it.id) } },
                leadingIcon = Icons.Outlined.Inventory2,
                height = 64.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            ProductsSummaryCard(state.products)
        }
    }
}

/** The right pane of the tablet Suppliers screen. */
@Composable
internal fun SupplierDetailPane(
    state: SupplierDetailUiState,
    onToggle: (Long) -> Unit,
    onQty: (Long, Long) -> Unit,
    onEdit: (Long) -> Unit,
    onRecordPurchase: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val supplier = state.supplier ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SupplierTitle(supplier, large = true, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEdit(supplier.id) }) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_supplier), tint = colors.ink)
            }
            ContactButtons(supplier)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ReorderCard(state = state, onToggle = onToggle, onQty = onQty)
                SendOrderButton(state)
            }
            ProductsTableCard(
                products = state.products,
                onRecordPurchase = { onRecordPurchase(supplier.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SupplierTitle(supplier: SupplierEntity?, large: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    Column(modifier = modifier) {
        val contactLine = listOfNotNull(supplier?.contactPerson, supplier?.phone, supplier?.location).joinToString(" · ")
        if (contactLine.isNotEmpty()) {
            Text(text = contactLine, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 1)
        }
        Text(
            text = supplier?.name.orEmpty(),
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 30.sp else 24.sp,
            color = colors.ink,
            maxLines = 1,
        )
    }
}

/** Call opens the dialer; WhatsApp opens the supplier's chat. Both stay disabled without a number. */
@Composable
private fun ContactButtons(supplier: SupplierEntity?, buttonModifier: Modifier = Modifier) {
    val context = LocalContext.current
    val phone = supplier?.phone
    val whatsapp = supplier?.whatsapp ?: phone
    SecondaryButton(
        text = stringResource(R.string.action_call),
        onClick = { phone?.let { Phone.dial(context, it) } },
        enabled = phone != null,
        leadingIcon = Icons.Outlined.Phone,
        modifier = buttonModifier,
    )
    SecondaryButton(
        text = stringResource(R.string.action_whatsapp),
        onClick = { whatsapp?.let { WhatsApp.send(context, it, "") } },
        enabled = whatsapp != null,
        leadingIcon = Icons.AutoMirrored.Outlined.Chat,
        modifier = buttonModifier,
    )
}

@Composable
private fun ReorderCard(
    state: SupplierDetailUiState,
    onToggle: (Long) -> Unit,
    onQty: (Long, Long) -> Unit,
) {
    val colors = LocalShopColors.current
    var editing by remember { mutableStateOf<ReorderItem?>(null) }
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = stringResource(R.string.reorder_title), modifier = Modifier.weight(1f))
                if (state.reorder.isNotEmpty()) {
                    Text(stringResource(R.string.suggested_quantities), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
            if (state.reorder.isEmpty()) {
                Text(
                    text = stringResource(R.string.nothing_to_reorder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            state.reorder.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = colors.line)
                ReorderRow(item = item, onToggle = { onToggle(item.line.productId) }, onEditQty = { editing = item })
            }
        }
    }
    editing?.let { item ->
        OrderQtyDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { qty ->
                onQty(item.line.productId, qty)
                editing = null
            },
        )
    }
}

@Composable
private fun ReorderRow(item: ReorderItem, onToggle: () -> Unit, onEditQty: () -> Unit) {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = colors.green, checkmarkColor = colors.onGreen),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.line.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (item.checked) colors.ink else colors.muted,
            )
            Text(
                text = stringResource(R.string.stock_left, Qty.format(item.line.stockQty)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.checked) colors.rust else colors.muted,
            )
        }
        Surface(
            onClick = onEditQty,
            shape = RoundedCornerShape(12.dp),
            color = colors.segmentTrack,
        ) {
            Text(
                text = stringResource(R.string.order_qty, item.qty.toString()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SendOrderButton(state: SupplierDetailUiState) {
    val context = LocalContext.current
    val templates = OrderTemplates(
        greeting = stringResource(R.string.order_greeting),
        line = stringResource(R.string.order_line),
        thanks = stringResource(R.string.order_thanks),
    )
    PrimaryButton(
        text = stringResource(R.string.send_order),
        onClick = {
            val supplier = state.supplier ?: return@PrimaryButton
            val text = Reorder.message(
                templates = templates,
                contact = supplier.contactPerson ?: supplier.name,
                shopName = state.shopName,
                items = state.orderItems.map { it.line.name to it.qty },
            )
            WhatsApp.send(context, supplier.whatsapp ?: supplier.phone, text)
        },
        enabled = state.orderItems.isNotEmpty(),
        leadingIcon = Icons.AutoMirrored.Outlined.Chat,
    )
}

@Composable
private fun OrderQtyDialog(item: ReorderItem, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val colors = LocalShopColors.current
    var text by remember { mutableStateOf(item.qty.toString()) }
    val qty = text.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.order_qty_title, item.line.name), color = colors.ink) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedIconButton(
                    onClick = { text = ((qty ?: 1L) - 1L).coerceAtLeast(1L).toString() },
                    border = BorderStroke(1.dp, colors.line),
                ) { Icon(Icons.Default.Remove, contentDescription = null, tint = colors.ink) }
                ShopTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(5) },
                    placeholder = "1",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                OutlinedIconButton(
                    onClick = { text = ((qty ?: 0L) + 1L).toString() },
                    border = BorderStroke(1.dp, colors.line),
                ) { Icon(Icons.Default.Add, contentDescription = null, tint = colors.ink) }
            }
        },
        confirmButton = {
            TextButton(onClick = { qty?.let(onSave) }, enabled = (qty ?: 0L) >= 1L) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ProductsTableCard(products: List<SupplierProductRow>, onRecordPurchase: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(text = stringResource(R.string.products_from_supplier))
            if (products.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_linked_products),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    TableHeader(stringResource(R.string.col_product), Modifier.weight(1f))
                    TableHeader(stringResource(R.string.col_stock), Modifier.width(64.dp))
                    TableHeader(stringResource(R.string.col_cost), Modifier.width(96.dp), TextAlign.End)
                }
                products.forEach { product ->
                    HorizontalDivider(color = colors.line)
                    Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Qty.format(product.stockQty),
                            style = amountTextStyle(18.sp),
                            color = if (product.isLow) colors.rust else colors.ink,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            text = Money.format(product.cost),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(96.dp),
                        )
                    }
                }
            }
            SecondaryButton(
                text = stringResource(R.string.record_purchase),
                onClick = onRecordPurchase,
                leadingIcon = Icons.Outlined.Inventory2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TableHeader(text: String, modifier: Modifier, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalShopColors.current.muted,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
private fun ProductsSummaryCard(products: List<SupplierProductRow>) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = stringResource(R.string.products_from_supplier), modifier = Modifier.weight(1f))
                Text(
                    text = products.size.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.green,
                )
            }
            Text(
                text = if (products.isEmpty()) stringResource(R.string.no_linked_products) else products.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
            )
        }
    }
}
