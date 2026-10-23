package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.fontScaled
import com.archsolutions.shopmanager.core.money.Money

@Composable
fun DayBar(
    openedSince: String,
    balancesSummary: String,
    onAddExpense: () -> Unit,
    onCloseDay: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    // Phones have no Day tab (3.5): on the compact bar, the top row opens Today's money.
    onDetails: (() -> Unit)? = null,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier.fillMaxWidth()) {
        if (isCompact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(
                            if (onDetails != null) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        onClickLabel = stringResource(R.string.todays_money),
                                        role = Role.Button,
                                        onClick = onDetails,
                                    )
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.greenTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = openedSince,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                        )
                        Text(
                            text = balancesSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                    if (onDetails != null) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.muted)
                    }
                }
                val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                FitOrStackRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                    SecondaryButton(
                        text = stringResource(R.string.action_add_expense),
                        onClick = onAddExpense,
                        height = 48.dp,
                        contentPadding = buttonPadding,
                    )
                    InkButton(
                        text = stringResource(R.string.action_close_day),
                        onClick = onCloseDay,
                        height = 48.dp,
                        contentPadding = buttonPadding,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Weighted, so the buttons are measured first and keep their width at large font sizes.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.greenTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column {
                        Text(
                            text = openedSince,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                        )
                        Text(
                            text = balancesSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.action_add_expense),
                        onClick = onAddExpense,
                        height = 48.dp,
                    )
                    InkButton(
                        text = stringResource(R.string.action_close_day),
                        onClick = onCloseDay,
                        height = 48.dp,
                    )
                }
            }
        }
    }
}

/** Takes the DayBar's place while trading is locked (6.3; addendum HomeLocked). */
@Composable
fun DayLockCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalShopColors.current
    ShopCard(
        modifier = modifier.fillMaxWidth(),
        padding = 20.dp,
        borderColor = colors.rust.copy(alpha = 0.3f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.rustTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.rust,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }
            action?.invoke()
        }
    }
}

/** An action that stays visible but disabled while the day is locked (addendum C5). */
@Composable
fun LockedActionButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    cornerRadius: Dp = 16.dp,
) {
    val colors = LocalShopColors.current
    val description = stringResource(R.string.locked_action, text)
    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.segmentTrack)
            .clearAndSetSemantics {
                contentDescription = description
                disabled()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun StatTile(
    label: String,
    amountFormatted: String,
    modifier: Modifier = Modifier,
    amountColor: Color? = null,
) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            Text(
                text = amountFormatted,
                style = com.archsolutions.shopmanager.core.designsystem.amountTextStyle(24.sp),
                color = amountColor ?: colors.ink,
            )
        }
    }
}

@Composable
fun DenominationRow(
    denomination: Long,
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val subtotal = denomination * count

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Money.format(denomination, withCode = false),
            style = com.archsolutions.shopmanager.core.designsystem.amountTextStyle(16.sp),
            color = colors.ink,
            modifier = Modifier.width(110.dp),
        )

        QtyStepper(
            value = count.toDouble(),
            onValueChange = { onCountChange(it.toInt()) },
            step = 1.0,
            min = 0.0,
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = Money.format(subtotal, withCode = false),
            style = com.archsolutions.shopmanager.core.designsystem.amountTextStyle(16.sp),
            color = colors.ink,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int = 3,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
) {
    val colors = LocalShopColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        for (i in 1..totalSteps) {
            val isDone = i < currentStep
            val isCurrent = i == currentStep

            val circleBg = when {
                isDone || isCurrent -> colors.green
                else -> colors.segmentTrack
            }
            val contentColor = when {
                isDone || isCurrent -> colors.onGreen
                else -> colors.muted
            }

            Box(
                modifier = Modifier
                    .size(fontScaled(28.dp))
                    .clip(CircleShape)
                    .background(circleBg),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = i.toString(),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = contentColor,
                    )
                }
            }

            labels.getOrNull(i - 1)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isDone || isCurrent) colors.ink else colors.muted,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            if (i < totalSteps) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(if (i < currentStep) colors.green else colors.segmentTrack)
                )
            }
        }
    }
}
