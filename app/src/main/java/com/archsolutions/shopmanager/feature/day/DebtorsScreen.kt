package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SearchField
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.core.util.DayFormat
import com.archsolutions.shopmanager.core.util.WhatsApp
import com.archsolutions.shopmanager.domain.Debtor
import com.archsolutions.shopmanager.domain.Debtors
import java.time.ZoneId

/** Owed to the shop (addendum B): who owes what, since when, and a WhatsApp reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtorsScreen(
    isExpanded: Boolean,
    zoneId: ZoneId,
    nowMillis: () -> Long,
    onBack: () -> Unit,
    onRecordPayment: (customerId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebtorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current

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
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.label_owed_to_shop),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 32.sp else 24.sp,
                            color = colors.ink,
                        )
                        Text(
                            text = pluralStringResource(R.plurals.plurals_customers, state.count, state.count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.record_payment),
                onClick = { onRecordPayment(0L) },
                leadingIcon = Icons.Default.Add,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 720.dp),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = if (isExpanded) 28.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ShopCard(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp), padding = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.total_owed), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        Text(Money.format(state.totalOwed), style = amountTextStyle(34.sp), color = colors.ink)
                        Text(stringResource(R.string.total_owed_note), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                }
            }
            item {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.search_customer),
                    modifier = Modifier.widthIn(max = 720.dp),
                )
            }
            item {
                ShopCard(modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp), padding = 0.dp) {
                    Column {
                        if (state.loaded && state.debtors.isEmpty()) {
                            Text(
                                text = stringResource(R.string.nobody_owes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.muted,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                        state.debtors.forEachIndexed { index, debtor ->
                            if (index > 0) HorizontalDivider(color = colors.line)
                            DebtorRow(
                                debtor = debtor,
                                shopName = state.shopName,
                                zoneId = zoneId,
                                nowMillis = nowMillis(),
                                onClick = { onRecordPayment(debtor.customer.id) },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.debtors_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun DebtorRow(
    debtor: Debtor,
    shopName: String,
    zoneId: ZoneId,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val colors = LocalShopColors.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val since = DayFormat.dateOf(debtor.sinceMillis, zoneId, "d MMM", locale)
    val days = DayFormat.daysBetween(debtor.sinceMillis, nowMillis, zoneId)
    val overdue = days > Debtors.OVERDUE_DAYS
    val sinceLine = when {
        overdue -> stringResource(R.string.owed_since_days, since, days.toInt())
        debtor.lastPaidMillis != null -> stringResource(
            R.string.owed_since_last_paid,
            since,
            DayFormat.dateOf(debtor.lastPaidMillis, zoneId, "d MMM", locale),
        )
        else -> stringResource(R.string.owed_since, since)
    }
    val reminder = stringResource(
        R.string.reminder_message,
        debtor.customer.name,
        shopName,
        Money.format(debtor.owed, withCode = false),
        since,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.segmentTrack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.muted)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(debtor.customer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(sinceLine, style = MaterialTheme.typography.bodyMedium, color = if (overdue) colors.rust else colors.muted)
        }
        Text(Money.format(debtor.owed, withCode = false), style = amountTextStyle(17.sp), color = colors.ink)
        OutlinedIconButton(
            onClick = { WhatsApp.send(context, debtor.customer.phone, reminder) },
            border = BorderStroke(1.dp, colors.line),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = stringResource(R.string.send_reminder), tint = colors.ink)
        }
    }
}
