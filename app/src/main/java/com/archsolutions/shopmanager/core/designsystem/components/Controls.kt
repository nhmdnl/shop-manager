package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors

@Composable
fun <T> ChipRow(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() },
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
) {
    val colors = LocalShopColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Surface(
                onClick = { onItemSelected(item) },
                shape = CircleShape,
                color = if (isSelected) colors.ink else colors.surface,
                border = if (isSelected) null else BorderStroke(1.dp, colors.line),
            ) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.surface else colors.ink,
                    )
                }
            }
        }
    }
}

/** Equal-width amount chips, so a row of four fits a 400 dp tablet panel without scrolling. */
@Composable
fun QuickAmountChips(
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
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: (T) -> String = { it.toString() },
) {
    val colors = LocalShopColors.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.segmentTrack)
            .padding(4.dp),
    ) {
        // As tall as the tallest label, so a label that wraps at large font sizes is not cut.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .shadow(1.dp, RoundedCornerShape(10.dp))
                                    .background(colors.surface)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onItemSelected(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) colors.ink else colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ShopSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalShopColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.surface,
            checkedTrackColor = colors.green,
            uncheckedThumbColor = colors.surface,
            uncheckedTrackColor = colors.segmentTrack,
            uncheckedBorderColor = colors.line,
        ),
    )
}
