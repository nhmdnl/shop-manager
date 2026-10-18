package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
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
                    color = if (isFocused) colors.green else colors.line,
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
                    color = if (isFocused) colors.green else colors.line,
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

    val formattedString = remember(value) {
        if (value == null || value == 0L) "" else Money.format(value, withCode = false)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) colors.green else colors.line,
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
                if (formattedString.isEmpty()) {
                    Text(
                        text = "0",
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = colors.muted.copy(alpha = 0.5f),
                    )
                }
                BasicTextField(
                    value = formattedString,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        val parsed = digits.toLongOrNull()
                        onValueChange(parsed)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            if (trailingBadge != null) {
                Badge(
                    text = trailingBadge,
                    kind = if (isMatchesBadge) BadgeKind.Ok else BadgeKind.Warn,
                )
            }
        }
    }
}
