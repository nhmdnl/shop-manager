package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.core.designsystem.CappedFontScale
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.amountTextStyle
import com.archsolutions.shopmanager.core.money.Money

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                BorderStroke(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) colors.green else colors.fieldBorder,
                ),
                RoundedCornerShape(14.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.muted,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.green),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Plain text input with the SearchField look, for names, phone numbers and notes. */
@Composable
fun ShopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val colors = LocalShopColors.current
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                BorderStroke(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) colors.green else colors.fieldBorder,
                ),
                RoundedCornerShape(14.dp),
            )
            .onFocusChanged { isFocused = it.hasFocus }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.green),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun MoneyField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    currencyCode: String = "UGX",
    trailingBadge: String? = null,
    isMatchesBadge: Boolean = true,
) {
    val colors = LocalShopColors.current
    var isFocused by remember { mutableStateOf(false) }
    val digits = amountDigits(value)
    // With larger fonts the badge moves under the box and leaves the whole width to the amount.
    val badgeBelow = trailingBadge != null && LocalDensity.current.fontScale > 1f

    CappedFontScale {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(
                        BorderStroke(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) colors.green else colors.fieldBorder,
                        ),
                        RoundedCornerShape(14.dp),
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = currencyCode,
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = colors.muted,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (digits.isEmpty()) {
                            Text(
                                text = "0",
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = colors.muted.copy(alpha = 0.5f),
                            )
                        }
                        BasicTextField(
                            value = digits,
                            onValueChange = { input -> onAmountInput(input, onValueChange) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = AmountTransformation,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = DisplayFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = colors.ink,
                                fontFeatureSettings = "tnum",
                            ),
                            cursorBrush = SolidColor(colors.green),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (trailingBadge != null && !badgeBelow) {
                        Badge(
                            text = trailingBadge,
                            kind = if (isMatchesBadge) BadgeKind.Ok else BadgeKind.Warn,
                        )
                    }
                }
            }
            if (trailingBadge != null && badgeBelow) {
                Badge(
                    text = trailingBadge,
                    kind = if (isMatchesBadge) BadgeKind.Ok else BadgeKind.Warn,
                )
            }
        }
    }
}

/** Compact amount box ("UGX 1,100") for per-line costs, e.g. on Add purchase. */
@Composable
fun SmallMoneyField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    var isFocused by remember { mutableStateOf(false) }

    CappedFontScale {
        Row(
            modifier = modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(
                    BorderStroke(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) colors.green else colors.fieldBorder,
                    ),
                    RoundedCornerShape(12.dp),
                )
                .onFocusChanged { isFocused = it.hasFocus }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "UGX",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
            Spacer(modifier = Modifier.width(6.dp))
            BasicTextField(
                value = amountDigits(value),
                onValueChange = { input -> onAmountInput(input, onValueChange) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = AmountTransformation,
                textStyle = amountTextStyle(16.sp).copy(color = colors.ink),
                cursorBrush = SolidColor(colors.green),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val MAX_AMOUNT_DIGITS = 12

/** What a money box edits: plain digits. [AmountTransformation] draws the thousands separators. */
private fun amountDigits(value: Long?): String = if (value == null || value == 0L) "" else value.toString()

private fun onAmountInput(input: String, onValueChange: (Long?) -> Unit) {
    val digits = input.filter(Char::isDigit)
    if (digits.length <= MAX_AMOUNT_DIGITS) onValueChange(digits.toLongOrNull())
}

/**
 * Adds Money's separators for display only, so the caret stays after the digit just typed.
 * Formatting the edited text itself left the caret one place short once a separator appeared.
 */
internal object AmountTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val amount = text.text.toLongOrNull() ?: return TransformedText(text, OffsetMapping.Identity)
        val shown = Money.format(amount, withCode = false)
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    if (offset <= 0) return 0
                    var seen = 0
                    shown.forEachIndexed { index, char ->
                        if (char.isDigit() && ++seen == offset) return index + 1
                    }
                    return shown.length
                }

                override fun transformedToOriginal(offset: Int): Int = shown.take(offset).count(Char::isDigit)
            },
        )
    }
}

/** A field that opens a menu of options (supplier, category). */
@Composable
fun <T> ShopDropdown(
    selectedLabel: String,
    options: List<T>,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.fieldBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.muted,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.surface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = labelFor(option), color = colors.ink) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
