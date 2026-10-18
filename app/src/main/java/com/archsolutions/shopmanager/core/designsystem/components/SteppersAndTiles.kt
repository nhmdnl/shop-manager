package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors

@Composable
fun QtyStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
    step: Double = 1.0,
) {
    val colors = LocalShopColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = colors.paper,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier.size(40.dp),
            onClick = {
                val newVal = (value - step).coerceAtLeast(min)
                onValueChange(newVal)
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.action_decrease),
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        val displayVal = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
        Text(
            text = displayVal,
            style = com.archsolutions.shopmanager.core.designsystem.amountTextStyle(17.sp),
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Surface(
            shape = CircleShape,
            color = colors.paper,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier.size(40.dp),
            onClick = {
                onValueChange(value + step)
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_increase),
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductTile(
    name: String,
    priceFormatted: String,
    stockLeftFormatted: String,
    isLowStock: Boolean,
    selectedQuantity: String? = null,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    height: Dp = 104.dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val isSelected = selectedQuantity != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.greenTint else colors.surface)
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) colors.green else colors.line,
                ),
                RoundedCornerShape(14.dp),
            )
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column {
                Text(
                    text = priceFormatted,
                    style = com.archsolutions.shopmanager.core.designsystem.amountTextStyle(17.sp),
                    color = colors.ink,
                )
                Text(
                    text = stockLeftFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLowStock) colors.rust else colors.muted,
                    fontWeight = if (isLowStock) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.green),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = selectedQuantity.orEmpty(),
                    style = com.archsolutions.shopmanager.core.designsystem.amountTextStyle(14.sp),
                    color = colors.onGreen,
                )
            }
        }
    }
}
