package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.designsystem.components.Badge
import com.archsolutions.shopmanager.core.designsystem.components.BadgeKind
import com.archsolutions.shopmanager.core.designsystem.components.DenominationRow
import com.archsolutions.shopmanager.core.designsystem.components.MoneyField
import com.archsolutions.shopmanager.core.money.Money
import com.archsolutions.shopmanager.domain.NoteCounter

/**
 * UGX note counter (6.4, 6.6): one stepper per note, coins as an amount, and the total, which fills
 * the cash field. [fillHeight] pins the total to the bottom of a full-height card (tablet).
 */
@Composable
internal fun NoteCounterPanel(
    notes: Map<Long, Int>,
    coins: Long,
    total: Long,
    onNoteCountChange: (Long, Int) -> Unit,
    onCoinsChange: (Long?) -> Unit,
    fillHeight: Boolean,
    title: String = stringResource(R.string.count_notes),
    optional: Boolean = true,
    showHint: Boolean = true,
) {
    val colors = LocalShopColors.current
    Column(modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
            )
            if (optional) {
                Badge(text = stringResource(R.string.label_optional), kind = BadgeKind.Muted)
            } else {
                Text(text = Money.format(total, withCode = false), style = amountTextStyle(22.sp), color = colors.ink)
            }
        }
        if (showHint) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.count_notes_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = if (fillHeight) {
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier
            },
        ) {
            NoteCounter.DENOMINATIONS.forEach { denomination ->
                DenominationRow(
                    denomination = denomination,
                    count = notes[denomination] ?: 0,
                    onCountChange = { onNoteCountChange(denomination, it) },
                )
                HorizontalDivider(color = colors.line)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.coins),
                    style = amountTextStyle(16.sp),
                    color = colors.ink,
                    modifier = Modifier.width(110.dp),
                )
                MoneyField(
                    value = coins.takeIf { it > 0 },
                    onValueChange = onCoinsChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (optional) {
            HorizontalDivider(color = colors.line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.total_counted),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.muted,
                )
                Text(
                    text = Money.format(total),
                    style = amountTextStyle(28.sp),
                    color = colors.ink,
                )
            }
        }
    }
}
