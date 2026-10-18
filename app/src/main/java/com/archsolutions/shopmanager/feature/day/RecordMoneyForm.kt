package com.archsolutions.shopmanager.feature.day

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.ChipRow
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.QuickAmountChips
import com.archsolutions.shopmanager.core.designsystem.components.SectionLabel
import com.archsolutions.shopmanager.core.designsystem.components.SegmentedControl
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.designsystem.components.dashedBorder
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.data.db.dao.CustomerWithOwed
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import com.archsolutions.shopmanager.data.db.entities.MoneyMethod
import java.time.ZoneId

/** The three kinds on the segmented control; Cash in opens from the "+" next to the title. */
private val SEGMENT_TYPES = listOf(MoneyEntryType.EXPENSE, MoneyEntryType.CREDIT_REPAYMENT, MoneyEntryType.CASH_OUT)

@Composable
internal fun expenseCategoryLabel(category: ExpenseCategory): String = stringResource(
    when (category) {
        ExpenseCategory.TRANSPORT -> R.string.cat_transport
        ExpenseCategory.RENT -> R.string.cat_rent
        ExpenseCategory.POWER -> R.string.cat_power
        ExpenseCategory.WATER -> R.string.cat_water
        ExpenseCategory.LUNCH -> R.string.cat_lunch
        ExpenseCategory.CLEANING -> R.string.cat_cleaning
        ExpenseCategory.AIRTIME -> R.string.cat_airtime
        ExpenseCategory.OTHER -> R.string.cat_other
    }
)

/** Record money form (7.4; addendum CreditPaid, CashIn). Shared by the tablet Day panel and the phone screen. */
@Composable
internal fun RecordMoneyForm(
    state: RecordMoneyUiState,
    viewModel: RecordMoneyViewModel,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = LocalShopColors.current
    val confirmOverpayment by viewModel.confirmOverpayment.collectAsStateWithLifecycle()
    val segmentLabels = mapOf(
        MoneyEntryType.EXPENSE to stringResource(R.string.kind_expense),
        MoneyEntryType.CREDIT_REPAYMENT to stringResource(R.string.credit_paid_back),
        MoneyEntryType.CASH_OUT to stringResource(R.string.cash_taken_out),
    )
    val methodLabels = mapOf(
        MoneyMethod.CASH to stringResource(R.string.label_cash),
        MoneyMethod.MOBILE_MONEY to stringResource(R.string.label_mobile_money),
    )

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            header?.invoke()
            if (state.type != MoneyEntryType.CASH_IN) {
                SegmentedControl(
                    items = SEGMENT_TYPES,
                    selectedItem = state.type,
                    onItemSelected = viewModel::setType,
                    labelProvider = { segmentLabels.getValue(it) },
                )
            }
            if (state.type == MoneyEntryType.CREDIT_REPAYMENT) {
                SectionLabel(text = stringResource(R.string.who_paid))
                CustomerChooser(state = state, zoneId = zoneId, onSelect = viewModel::selectCustomer)
            }
            FieldLabel(stringResource(R.string.label_amount))
            val owed = state.selectedCustomer?.owed
            MoneyField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                trailingBadge = if (owed != null && (state.amount ?: 0L) in 1 until owed) stringResource(R.string.part_payment) else null,
                isMatchesBadge = false,
            )
            QuickAmounts(state = state, onPick = viewModel::setAmount)
            when (state.type) {
                MoneyEntryType.EXPENSE -> {
                    SectionLabel(text = stringResource(R.string.what_for))
                    CategoryGrid(selected = state.category, onSelect = viewModel::setCategory)
                    SectionLabel(text = stringResource(R.string.paid_from))
                }
                MoneyEntryType.CREDIT_REPAYMENT -> SectionLabel(text = stringResource(R.string.received_in))
                MoneyEntryType.CASH_OUT -> SectionLabel(text = stringResource(R.string.paid_from))
                MoneyEntryType.CASH_IN -> SectionLabel(text = stringResource(R.string.into_channel))
            }
            SegmentedControl(
                items = MoneyMethod.entries,
                selectedItem = state.method,
                onItemSelected = viewModel::setMethod,
                labelProvider = { methodLabels.getValue(it) },
            )
            if (state.type == MoneyEntryType.CREDIT_REPAYMENT) {
                val customer = state.selectedCustomer
                val after = state.owedAfter
                if (customer != null && after != null) {
                    Text(
                        text = if (after >= 0) {
                            stringResource(R.string.after_this_owes, customer.customer.name, Money.format(after))
                        } else {
                            stringResource(R.string.after_this_credit, customer.customer.name, Money.format(-after))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .dashedBorder(colors.muted, 14.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            FieldLabel(stringResource(R.string.note_optional))
            ShopTextField(value = state.note, onValueChange = viewModel::setNote, placeholder = stringResource(R.string.note_placeholder))
            if (state.type == MoneyEntryType.CASH_IN) {
                Text(stringResource(R.string.cash_in_hint), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        PrimaryButton(
            text = stringResource(
                when (state.type) {
                    MoneyEntryType.EXPENSE -> R.string.save_expense
                    MoneyEntryType.CREDIT_REPAYMENT -> R.string.save_payment
                    MoneyEntryType.CASH_OUT -> R.string.save_cash_out
                    MoneyEntryType.CASH_IN -> R.string.save_cash_in
                }
            ),
            onClick = viewModel::save,
            enabled = state.canSave,
            height = 64.dp,
            leadingIcon = Icons.Default.Check,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    if (confirmOverpayment) {
        val customer = state.selectedCustomer
        AlertDialog(
            onDismissRequest = viewModel::dismissOverpayment,
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.overpay_title)) },
            text = {
                if (customer != null) {
                    Text(
                        stringResource(
                            R.string.overpay_body,
                            customer.customer.name,
                            Money.format(customer.owed.coerceAtLeast(0L)),
                            Money.format((state.amount ?: 0L) - customer.owed.coerceAtLeast(0L)),
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmOverpayment) {
                    Text(stringResource(R.string.overpay_confirm), color = colors.green, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOverpayment) {
                    Text(stringResource(R.string.action_cancel), color = colors.ink)
                }
            },
        )
    }
}

// Credit paid back: "Pay all X" plus round amounts below it; Cash in: the float amounts from the design.
@Composable
private fun QuickAmounts(state: RecordMoneyUiState, onPick: (Long) -> Unit) {
    val amounts: List<Long> = when (state.type) {
        MoneyEntryType.CREDIT_REPAYMENT -> {
            val owed = state.selectedCustomer?.owed ?: 0L
            if (owed <= 0L) emptyList() else listOf(owed) + listOf(20_000L, 10_000L).filter { it < owed }
        }
        MoneyEntryType.CASH_IN -> listOf(10_000L, 20_000L, 50_000L, 100_000L)
        else -> emptyList()
    }
    if (amounts.isEmpty()) return
    val payAll = state.type == MoneyEntryType.CREDIT_REPAYMENT
    if (!payAll) {
        // The four float amounts fit side by side; only the longer "Pay all" labels need the scrolling row.
        QuickAmountChips(
            amounts = amounts,
            selected = state.amount,
            labelFor = { Money.format(it, withCode = false) },
            onSelect = onPick,
        )
        return
    }
    val payAllLabel = stringResource(R.string.pay_all, Money.format(amounts.first(), withCode = false))
    ChipRow(
        items = amounts,
        selectedItem = state.amount ?: -1L,
        onItemSelected = onPick,
        labelProvider = { amount ->
            if (amount == amounts.first()) payAllLabel else Money.format(amount, withCode = false)
        },
    )
}

@Composable
private fun CategoryGrid(selected: ExpenseCategory, onSelect: (ExpenseCategory) -> Unit) {
    val colors = LocalShopColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseCategory.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    val isSelected = category == selected
                    Surface(
                        onClick = { onSelect(category) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) colors.greenTint else colors.surface,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.green else colors.line),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                            Text(
                                text = expenseCategoryLabel(category),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) colors.green else colors.ink,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

// The chosen customer's card, or the list to choose from (customers who owe first).
@Composable
private fun CustomerChooser(
    state: RecordMoneyUiState,
    zoneId: ZoneId,
    onSelect: (Long) -> Unit,
) {
    val colors = LocalShopColors.current
    val locale = LocalConfiguration.current.locales[0]
    val selected = state.selectedCustomer
    ShopCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
        Column {
            val rows = if (selected != null) {
                listOf(selected)
            } else {
                state.customers.sortedByDescending { it.owed }
            }
            rows.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = colors.line)
                val isSelected = item.customer.id == selected?.customer?.id
                val since = if (isSelected) state.owedSinceMillis else null
                CustomerOwedRow(
                    item = item,
                    sinceText = since?.let { DayFormat.dateOf(it, zoneId, "d MMM", locale) },
                    isSelected = isSelected,
                    // Tapping the chosen customer again lets the user pick someone else.
                    onClick = { onSelect(if (isSelected) 0L else item.customer.id) },
                )
            }
        }
    }
}

@Composable
private fun CustomerOwedRow(
    item: CustomerWithOwed,
    sinceText: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) colors.greenTint else colors.segmentTrack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = if (isSelected) colors.green else colors.muted)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.customer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
            val owedText = when {
                item.owed > 0 && sinceText != null -> stringResource(R.string.owes_since, Money.format(item.owed), sinceText)
                item.owed > 0 -> stringResource(R.string.owes_amount, Money.format(item.owed))
                item.owed < 0 -> stringResource(R.string.has_credit, Money.format(-item.owed))
                else -> stringResource(R.string.owes_nothing)
            }
            Text(
                text = owedText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.owed > 0) colors.rust else colors.muted,
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = colors.green, unselectedColor = colors.muted),
        )
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

/** "Saved · UGX 10,000", or the save error (7.9). */
@Composable
internal fun RecordMoneySnackbarEffects(
    viewModel: RecordMoneyViewModel,
    hostState: SnackbarHostState,
    onSaved: (() -> Unit)? = null,
) {
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val savedMessage = saved?.let { stringResource(R.string.money_saved, Money.format(it)) }
    val failedMessage = stringResource(R.string.error_could_not_save)
    LaunchedEffect(saved) {
        if (savedMessage != null) {
            if (onSaved != null) {
                viewModel.savedShown()
                onSaved()
            } else {
                hostState.showSnackbar(savedMessage)
                viewModel.savedShown()
            }
        }
    }
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            hostState.showSnackbar(failedMessage)
            viewModel.saveFailedShown()
        }
    }
}
