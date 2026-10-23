package com.archsolutions.shopmanager.feature.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.QtyStepper
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.SegmentedControl
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopDropdown
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.entities.CategoryEntity
import com.archsolutions.shopmanager.data.db.entities.Unit as ProductUnit
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.archsolutions.shopmanager.feature.pin.rememberPinGate

/** Edit product / New product (7.3, addendum NewProduct) with the Correct stock sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductScreen(
    isExpanded: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProductViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val pinGate = rememberPinGate()
    var showCorrect by rememberSaveable { mutableStateOf(false) }
    var showSupplierPicker by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    val unitText = unitLabel(form.unit)
    val unitLabels = ProductUnit.entries.associateWith { unitLabel(it) }
    val noCategory = stringResource(R.string.no_category)

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

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
                    Text(
                        text = stringResource(if (state.isNew) R.string.new_product else R.string.edit_product),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isExpanded) 32.sp else 26.sp,
                        color = colors.ink,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
        bottomBar = {
            PrimaryButton(
                text = stringResource(if (state.isNew) R.string.add_product else R.string.save_product),
                onClick = viewModel::save,
                enabled = state.canSave,
                height = 64.dp,
                leadingIcon = Icons.Default.Check,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 720.dp),
            )
        },
    ) { paddingValues ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 8.dp)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FieldLabel(stringResource(R.string.label_name))
            ShopTextField(
                value = form.name,
                onValueChange = { name -> viewModel.edit { it.copy(name = name) } },
                placeholder = stringResource(R.string.name_placeholder),
                capitalization = KeyboardCapitalization.Words,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.label_category))
                    ShopDropdown(
                        selectedLabel = state.categories.firstOrNull { it.id == form.categoryId }?.name ?: noCategory,
                        options = listOf<CategoryEntity?>(null) + state.categories,
                        labelFor = { it?.name ?: noCategory },
                        onSelect = { category -> viewModel.edit { it.copy(categoryId = category?.id) } },
                    )
                }
                Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.barcode_optional))
                    ShopTextField(
                        value = form.barcode,
                        onValueChange = { code -> viewModel.edit { it.copy(barcode = code) } },
                        placeholder = stringResource(R.string.barcode_placeholder),
                        capitalization = KeyboardCapitalization.None,
                    )
                }
            }
            SectionLabel(text = stringResource(R.string.label_unit))
            SegmentedControl(
                items = ProductUnit.entries,
                selectedItem = form.unit,
                onItemSelected = { unit -> viewModel.edit { it.copy(unit = unit) } },
                labelProvider = { unitLabels.getValue(it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.selling_price))
                    MoneyField(value = form.sellPrice, onValueChange = { price -> viewModel.edit { it.copy(sellPrice = price) } })
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(stringResource(R.string.cost_price))
                    MoneyField(value = form.costPrice, onValueChange = { price -> viewModel.edit { it.copy(costPrice = price) } })
                }
            }
            if (state.isNew) {
                ShopCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.starting_stock), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
                            Text(stringResource(R.string.starting_stock_hint), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                        QtyStepper(value = form.startingStock, onValueChange = { qty -> viewModel.edit { it.copy(startingStock = qty) } })
                    }
                }
            } else {
                ShopCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.in_stock), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
                            Text(stringResource(R.string.in_stock_hint), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            Row(
                                modifier = Modifier
                                    .clickable { showCorrect = true }
                                    .heightIn(min = 40.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                                Text(stringResource(R.string.correct_stock), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.green)
                            }
                        }
                        Text(
                            text = stringResource(R.string.qty_with_unit, Qty.format(state.stockQty), unitText),
                            style = amountTextStyle(24.sp),
                            color = if (state.lowStock) colors.rust else colors.ink,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.alert_when_below),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                QtyStepper(value = form.lowStockLevel, onValueChange = { level -> viewModel.edit { it.copy(lowStockLevel = level) } })
                Text(text = unitText, style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.padding(start = 8.dp))
            }
            SuppliersCard(
                links = form.links,
                onAdd = { showSupplierPicker = true },
                onMakePreferred = viewModel::makePreferred,
            )
        }
    }

    if (showCorrect) {
        CorrectStockSheet(
            productName = form.name,
            stockQty = state.stockQty,
            unitText = unitText,
            pinEnabled = state.pinEnabled,
            onDismiss = { showCorrect = false },
            onSave = { counted, reason, note ->
                pinGate.run(R.string.pin_for_stock) { viewModel.correctStock(counted, reason, note) }
            },
        )
    }
    if (showSupplierPicker) {
        PickerSheet(
            title = stringResource(R.string.choose_supplier),
            options = state.unlinkedSuppliers,
            labelFor = { it.name },
            onPick = {
                viewModel.linkSupplier(it)
                showSupplierPicker = false
            },
            onDismiss = { showSupplierPicker = false },
        )
    }
}

@Composable
private fun SuppliersCard(
    links: List<SupplierLinkDraft>,
    onAdd: () -> Unit,
    onMakePreferred: (Long) -> Unit,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = stringResource(R.string.label_suppliers), modifier = Modifier.weight(1f))
                if (links.isNotEmpty()) {
                    TextButton(onClick = onAdd) {
                        Text("+ " + stringResource(R.string.action_add), fontWeight = FontWeight.Bold, color = colors.green)
                    }
                }
            }
            if (links.isEmpty()) {
                SecondaryButton(
                    text = stringResource(R.string.link_supplier),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Default.Add,
                )
            }
            links.forEachIndexed { index, link ->
                if (index > 0) HorizontalDivider(color = colors.line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMakePreferred(link.supplierId) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(link.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
                        link.cost?.let {
                            Text(stringResource(R.string.supplier_cost, Money.format(it)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                    }
                    if (link.preferred) Badge(text = stringResource(R.string.preferred), kind = BadgeKind.Ok)
                }
            }
        }
    }
}

/** Addendum CorrectStock: the counted amount replaces the stock through one ADJUSTMENT movement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorrectStockSheet(
    productName: String,
    stockQty: Double,
    unitText: String,
    pinEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (counted: Double, reason: String, note: String) -> Unit,
) {
    val colors = LocalShopColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val reasons = listOf(
        stringResource(R.string.reason_damaged),
        stringResource(R.string.reason_expired),
        stringResource(R.string.reason_count_correction),
        stringResource(R.string.reason_other),
    )
    var counted by rememberSaveable { mutableDoubleStateOf(stockQty) }
    var reason by rememberSaveable { mutableStateOf(reasons[2]) }
    var note by rememberSaveable { mutableStateOf("") }
    val change = counted - stockQty
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.correct_stock),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.ink,
            )
            Text(
                text = stringResource(R.string.app_shows, productName, stringResource(R.string.qty_with_unit, Qty.format(stockQty), unitText)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.counted_on_shelf),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                QtyStepper(value = counted, onValueChange = { counted = it })
            }
            if (change != 0.0) {
                val tint = if (change < 0) colors.rust else colors.green
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (change < 0) colors.rustTint else colors.greenTint)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.label_change), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = tint, modifier = Modifier.weight(1f))
                    Text(
                        text = (if (change < 0) "− " else "+ ") + stringResource(R.string.qty_with_unit, Qty.format(abs(change)), unitText),
                        style = amountTextStyle(18.sp),
                        color = tint,
                    )
                }
            }
            SectionLabel(text = stringResource(R.string.label_reason))
            ChipRow(items = reasons, selectedItem = reason, onItemSelected = { reason = it })
            FieldLabel(stringResource(R.string.note_optional))
            ShopTextField(value = note, onValueChange = { note = it }, placeholder = stringResource(R.string.note_placeholder))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Text(
                    text = (if (pinEnabled) stringResource(R.string.pin_needed) + " · " else "") + stringResource(R.string.adjustment_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(text = stringResource(R.string.action_cancel), onClick = close, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = stringResource(R.string.save_correction),
                    onClick = {
                        onSave(counted, reason, note)
                        close()
                    },
                    enabled = change != 0.0,
                    height = 56.dp,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}
