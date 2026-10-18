package com.archsolutions.shopmanager.feature.stock

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.QtyStepper
import com.archsolutions.shopmanager.core.designsystem.components.SearchField
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.SegmentedControl
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopDropdown
import com.archsolutions.shopmanager.core.designsystem.components.SmallMoneyField
import com.archsolutions.shopmanager.core.designsystem.components.dashedBorder
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.Qty
import com.archsolutions.shopmanager.data.db.entities.PurchasePaid
import com.archsolutions.shopmanager.data.db.entities.SupplierEntity
import com.archsolutions.shopmanager.data.db.entities.Unit as ProductUnit

/** Add purchase (5.3, 7.3): supplier, lines of qty × cost, total, Paid from, Save purchase. */
@Composable
internal fun PurchaseForm(
    state: PurchaseUiState,
    viewModel: PurchaseViewModel,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = LocalShopColors.current
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val noSupplier = stringResource(R.string.no_supplier)
    val paidLabels = mapOf(
        PurchasePaid.CASH to stringResource(R.string.label_cash),
        PurchasePaid.MOBILE_MONEY to stringResource(R.string.label_mobile_money),
        PurchasePaid.NOT_PAID to stringResource(R.string.not_paid_yet),
    )

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            header?.invoke()
            FieldLabel(stringResource(R.string.label_supplier))
            ShopDropdown(
                selectedLabel = state.suppliers.firstOrNull { it.id == state.supplierId }?.name ?: noSupplier,
                options = listOf<SupplierEntity?>(null) + state.suppliers,
                labelFor = { it?.name ?: noSupplier },
                onSelect = { viewModel.setSupplier(it?.id) },
            )
            SectionLabel(text = stringResource(R.string.products_bought))
            if (state.lines.isNotEmpty()) {
                ShopCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        state.lines.forEachIndexed { index, line ->
                            if (index > 0) HorizontalDivider(color = colors.line)
                            PurchaseLineRow(
                                line = line,
                                onQtyChange = { viewModel.setQty(line.product.id, it) },
                                onCostChange = { viewModel.setCost(line.product.id, it) },
                            )
                        }
                    }
                }
            }
            DashedAddButton(text = stringResource(R.string.add_product), onClick = { showPicker = true })
            HorizontalDivider(color = colors.line)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.total_paid),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                )
                Text(
                    text = Money.format(state.total),
                    style = amountTextStyle(30.sp),
                    color = colors.ink,
                )
            }
            Text(
                // Plural rules need a whole number: exactly one item is "one", anything else (incl. 0.5 kg) is "other".
                text = pluralStringResource(
                    R.plurals.plurals_saving_adds,
                    if (state.itemsAdded == 1.0) 1 else 2,
                    Qty.format(state.itemsAdded),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            FieldLabel(stringResource(R.string.paid_from))
            SegmentedControl(
                items = PurchasePaid.entries,
                selectedItem = state.paid,
                onItemSelected = viewModel::setPaid,
                labelProvider = { paidLabels.getValue(it) },
            )
            Text(
                text = stringResource(R.string.not_paid_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        PrimaryButton(
            text = stringResource(R.string.save_purchase),
            onClick = viewModel::save,
            enabled = state.canSave,
            height = 64.dp,
            leadingIcon = Icons.Default.Check,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    if (showPicker) {
        PickerSheet(
            title = stringResource(R.string.choose_product),
            options = state.products,
            labelFor = { it.name },
            supportingFor = { stringResource(R.string.stock_left, Qty.format(it.stockQty)) },
            searchable = true,
            onPick = {
                viewModel.addProduct(it.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun PurchaseLineRow(
    line: PurchaseLineUi,
    onQtyChange: (Double) -> Unit,
    onCostChange: (Long?) -> Unit,
) {
    val colors = LocalShopColors.current
    Column(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = line.product.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            QtyStepper(value = line.qty, onValueChange = onQtyChange)
            Text(
                text = "×",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            SmallMoneyField(value = line.unitCost, onValueChange = onCostChange, modifier = Modifier.width(108.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = Money.format(line.lineTotal, withCode = false),
                style = amountTextStyle(16.sp),
                color = colors.ink,
            )
        }
    }
}

@Composable
internal fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .dashedBorder(colors.muted, 14.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.green)
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = LocalShopColors.current.ink,
    )
}

@Composable
internal fun unitLabel(unit: ProductUnit): String = stringResource(
    when (unit) {
        ProductUnit.PCS -> R.string.unit_pcs
        ProductUnit.KG -> R.string.unit_kg
        ProductUnit.LITRE -> R.string.unit_litre
        ProductUnit.PACK -> R.string.unit_pack
    }
)

/** Bottom sheet list to pick one option, optionally searchable (products, suppliers). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> PickerSheet(
    title: String,
    options: List<T>,
    labelFor: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    supportingFor: (@Composable (T) -> String)? = null,
    searchable: Boolean = false,
) {
    val colors = LocalShopColors.current
    var query by rememberSaveable { mutableStateOf("") }
    val visible = if (query.isBlank()) options else options.filter { labelFor(it).contains(query.trim(), ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.ink,
            )
            if (searchable) {
                SearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.search_placeholder))
            }
            LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                items(visible) { option ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                            .heightIn(min = 56.dp)
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = labelFor(option), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                        supportingFor?.let {
                            Text(text = it(option), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                    }
                    HorizontalDivider(color = colors.line)
                }
            }
        }
    }
}
