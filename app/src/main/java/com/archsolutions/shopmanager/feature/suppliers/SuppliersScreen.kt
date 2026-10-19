package com.archsolutions.shopmanager.feature.suppliers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.Badge
import com.archsolutions.shopmanager.core.designsystem.components.BadgeKind
import com.archsolutions.shopmanager.core.designsystem.components.SearchField
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.util.Phone
import com.archsolutions.shopmanager.core.util.WhatsApp
import com.archsolutions.shopmanager.domain.SupplierSummary

/** Suppliers (7.5): tablets show the list and the selected supplier side by side; phones open a detail screen. */
@Composable
fun SuppliersScreen(
    isExpanded: Boolean,
    onOpenSupplier: (Long) -> Unit,
    onNewSupplier: () -> Unit,
    onEditSupplier: (Long) -> Unit,
    onRecordPurchase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SuppliersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (isExpanded) {
        TabletSuppliers(
            state = state,
            viewModel = viewModel,
            onNewSupplier = onNewSupplier,
            onEditSupplier = onEditSupplier,
            onRecordPurchase = onRecordPurchase,
            modifier = modifier,
        )
    } else {
        PhoneSuppliers(state = state, viewModel = viewModel, onOpenSupplier = onOpenSupplier, onNewSupplier = onNewSupplier, modifier = modifier)
    }
}

@Composable
private fun TabletSuppliers(
    state: SuppliersUiState,
    viewModel: SuppliersViewModel,
    onNewSupplier: () -> Unit,
    onEditSupplier: (Long) -> Unit,
    onRecordPurchase: (Long) -> Unit,
    modifier: Modifier,
    detailViewModel: SupplierDetailViewModel = hiltViewModel(),
) {
    val colors = LocalShopColors.current
    val detail by detailViewModel.state.collectAsStateWithLifecycle()
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    // The picked supplier while it is listed, else the first one (most low items).
    val selected = state.suppliers.firstOrNull { it.supplier.id == picked }?.supplier?.id ?: state.suppliers.firstOrNull()?.supplier?.id
    LaunchedEffect(selected) { selected?.let(detailViewModel::select) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .fillMaxHeight()
                .padding(start = 28.dp, end = 24.dp, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(modifier = Modifier.weight(1f))
                AddSupplierButton(onClick = onNewSupplier)
            }
            LowBanner(state = state, compact = false, onClick = viewModel::toggleLowOnly)
            SearchField(value = state.query, onValueChange = viewModel::setQuery, placeholder = stringResource(R.string.search_suppliers))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { EmptyStates(state) }
                items(state.suppliers, key = { it.supplier.id }) { summary ->
                    TabletSupplierRow(
                        summary = summary,
                        selected = summary.supplier.id == selected,
                        onClick = { picked = summary.supplier.id },
                    )
                }
            }
        }
        VerticalDivider(color = colors.line)
        if (selected != null && detail.supplier?.id == selected) {
            SupplierDetailPane(
                state = detail,
                onToggle = detailViewModel::toggle,
                onQty = detailViewModel::setQty,
                onEdit = onEditSupplier,
                onRecordPurchase = onRecordPurchase,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSuppliers(
    state: SuppliersUiState,
    viewModel: SuppliersViewModel,
    onOpenSupplier: (Long) -> Unit,
    onNewSupplier: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalShopColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                title = { ScreenTitle() },
                actions = { AddSupplierButton(onClick = onNewSupplier, modifier = Modifier.padding(end = 12.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { LowBanner(state = state, compact = true, onClick = viewModel::toggleLowOnly) }
            item {
                SearchField(value = state.query, onValueChange = viewModel::setQuery, placeholder = stringResource(R.string.search_suppliers))
            }
            item { EmptyStates(state) }
            if (state.suppliers.isNotEmpty()) {
                item {
                    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
                        Column {
                            state.suppliers.forEachIndexed { index, summary ->
                                if (index > 0) HorizontalDivider(color = colors.line)
                                PhoneSupplierRow(summary = summary, onClick = { onOpenSupplier(summary.supplier.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.nav_suppliers),
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = LocalShopColors.current.ink,
        modifier = modifier,
    )
}

@Composable
private fun AddSupplierButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.green, contentColor = colors.onGreen),
        modifier = modifier.size(52.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_supplier))
    }
}

/** "6 low items across 3 suppliers"; tapping it shows only suppliers with something low (and back). */
@Composable
private fun LowBanner(state: SuppliersUiState, compact: Boolean, onClick: () -> Unit) {
    val low = state.lowAcross
    if (low.items == 0 && !state.lowOnly) return
    val colors = LocalShopColors.current
    val summary = stringResource(
        R.string.low_across_suppliers,
        pluralStringResource(R.plurals.plurals_low_items, low.items, low.items),
        pluralStringResource(R.plurals.plurals_suppliers, low.suppliers, low.suppliers),
    )
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = colors.rustTint, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = colors.rust)
            Column(modifier = Modifier.weight(1f)) {
                if (compact) {
                    Text(stringResource(R.string.reorder_list), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = colors.rust)
                }
                Text(
                    text = summary,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (compact) FontWeight.Normal else FontWeight.SemiBold,
                    color = colors.rust,
                )
                if (state.lowOnly) {
                    Text(stringResource(R.string.show_all_suppliers), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.rust)
                }
            }
            Icon(
                imageVector = if (state.lowOnly) Icons.Default.Close else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.rust,
            )
        }
    }
}

@Composable
private fun EmptyStates(state: SuppliersUiState) {
    if (!state.loaded || state.suppliers.isNotEmpty()) return
    val colors = LocalShopColors.current
    Column(modifier = Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(if (state.hasAny) R.string.no_supplier_match else R.string.no_suppliers),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        if (!state.hasAny) {
            Text(stringResource(R.string.no_suppliers_hint), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}

@Composable
private fun SupplierNameLine(summary: SupplierSummary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = summary.supplier.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalShopColors.current.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (summary.lowCount > 0) {
            Badge(text = stringResource(R.string.low_count, summary.lowCount.toString()), kind = BadgeKind.Warn)
        }
    }
}

@Composable
private fun TabletSupplierRow(summary: SupplierSummary, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalShopColors.current
    val products = pluralStringResource(R.plurals.plurals_products, summary.productCount, summary.productCount)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.greenTint else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SupplierNameLine(summary)
                Text(
                    text = listOfNotNull(summary.supplier.contactPerson, summary.supplier.phone, products).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    maxLines = 1,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.muted)
        }
    }
}

@Composable
private fun PhoneSupplierRow(summary: SupplierSummary, onClick: () -> Unit) {
    val colors = LocalShopColors.current
    val context = LocalContext.current
    val supplier = summary.supplier
    val whatsapp = supplier.whatsapp ?: supplier.phone
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SupplierNameLine(summary)
            val contact = listOfNotNull(supplier.contactPerson, supplier.phone).joinToString(" · ")
            if (contact.isNotEmpty()) {
                Text(contact, style = MaterialTheme.typography.bodyMedium, color = colors.muted, maxLines = 1)
            }
            Text(
                text = pluralStringResource(R.plurals.plurals_products, summary.productCount, summary.productCount),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        RoundContactButton(
            icon = Icons.Outlined.Phone,
            description = stringResource(R.string.action_call),
            enabled = supplier.phone != null,
            onClick = { supplier.phone?.let { Phone.dial(context, it) } },
        )
        RoundContactButton(
            icon = Icons.AutoMirrored.Outlined.Chat,
            description = stringResource(R.string.action_whatsapp),
            enabled = whatsapp != null,
            onClick = { whatsapp?.let { WhatsApp.send(context, it, "") } },
        )
    }
}

@Composable
private fun RoundContactButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalShopColors.current
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.greenTint, contentColor = colors.green),
        modifier = Modifier.size(52.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}
