package com.archsolutions.shopmanager.feature.suppliers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopSwitch
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.data.db.entities.ProductEntity
import com.archsolutions.shopmanager.feature.stock.FieldLabel
import com.archsolutions.shopmanager.feature.stock.PickerSheet

/** New / edit supplier (addendum A, SupplierEdit): contact details and the products they sell. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierEditScreen(
    isExpanded: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SupplierEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val form = state.form
    var linking by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val couldNotSave = stringResource(R.string.error_could_not_save)

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(couldNotSave)
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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = {
                    Text(
                        text = stringResource(if (state.isNew) R.string.new_supplier else R.string.edit_supplier),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isExpanded) 30.sp else 24.sp,
                        color = colors.ink,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
                PrimaryButton(
                    text = stringResource(R.string.save_supplier),
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    leadingIcon = Icons.Default.Check,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .padding(16.dp),
                )
            }
        },
    ) { paddingValues ->
        // Centred at form width on tablets; the full width on phones.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FieldLabel(stringResource(R.string.label_business_name))
                ShopTextField(
                    value = form.name,
                    onValueChange = { text -> viewModel.edit { it.copy(name = text) } },
                    placeholder = "",
                    capitalization = KeyboardCapitalization.Words,
                )
                FieldLabel(stringResource(R.string.label_contact_person))
                ShopTextField(
                    value = form.contact,
                    onValueChange = { text -> viewModel.edit { it.copy(contact = text) } },
                    placeholder = "",
                    capitalization = KeyboardCapitalization.Words,
                )
                FieldLabel(stringResource(R.string.label_phone))
                ShopTextField(
                    value = form.phone,
                    onValueChange = { text -> viewModel.edit { it.copy(phone = text) } },
                    placeholder = stringResource(R.string.phone_placeholder),
                    keyboardType = KeyboardType.Phone,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.whatsapp_same_number),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    ShopSwitch(checked = form.whatsappSame, onCheckedChange = { on -> viewModel.edit { it.copy(whatsappSame = on) } })
                }
                if (!form.whatsappSame) {
                    FieldLabel(stringResource(R.string.label_whatsapp_number))
                    ShopTextField(
                        value = form.whatsapp,
                        onValueChange = { text -> viewModel.edit { it.copy(whatsapp = text) } },
                        placeholder = stringResource(R.string.phone_placeholder),
                        keyboardType = KeyboardType.Phone,
                    )
                }
                FieldLabel(stringResource(R.string.label_location_optional))
                ShopTextField(
                    value = form.location,
                    onValueChange = { text -> viewModel.edit { it.copy(location = text) } },
                    placeholder = "",
                    capitalization = KeyboardCapitalization.Words,
                )
                FieldLabel(stringResource(R.string.label_notes_optional))
                ShopTextField(
                    value = form.notes,
                    onValueChange = { text -> viewModel.edit { it.copy(notes = text) } },
                    placeholder = stringResource(R.string.supplier_notes_placeholder),
                )
                LinkedProductsCard(linked = state.linked, onLink = { linking = true }, onUnlink = viewModel::unlink)
            }
        }
    }

    if (linking) {
        PickerSheet(
            title = stringResource(R.string.link_product),
            options = state.linkable,
            labelFor = { it.name },
            onPick = { product ->
                viewModel.link(product.id)
                linking = false
            },
            onDismiss = { linking = false },
            searchable = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedProductsCard(linked: List<ProductEntity>, onLink: () -> Unit, onUnlink: (Long) -> Unit) {
    val colors = LocalShopColors.current
    ShopCard(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = stringResource(R.string.products_they_sell), modifier = Modifier.weight(1f))
                TextButton(onClick = onLink) {
                    Text(stringResource(R.string.action_link), fontWeight = FontWeight.SemiBold, color = colors.green)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                linked.forEach { product ->
                    val remove = stringResource(R.string.unlink_product, product.name)
                    Surface(
                        onClick = { onUnlink(product.id) },
                        shape = CircleShape,
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(product.name, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                            Icon(
                                Icons.Default.Close,
                                contentDescription = remove,
                                tint = colors.muted,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
