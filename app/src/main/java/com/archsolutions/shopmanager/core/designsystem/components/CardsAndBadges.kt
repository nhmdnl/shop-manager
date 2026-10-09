package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors

@Composable
fun ShopCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalShopColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.line),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface,
            contentColor = colors.ink,
        ),
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = colors.muted,
        modifier = modifier,
    )
}

enum class BadgeKind {
    Warn,
    Ok,
    Muted,
}

@Composable
fun Badge(
    text: String,
    kind: BadgeKind = BadgeKind.Ok,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    val (bg, fg) = when (kind) {
        BadgeKind.Warn -> colors.rustTint to colors.rust
        BadgeKind.Ok -> colors.greenTint to colors.green
        BadgeKind.Muted -> colors.segmentTrack to colors.muted
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}
