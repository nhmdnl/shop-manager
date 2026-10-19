package com.archsolutions.shopmanager.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors

/** States board "No products yet": what to do next, not just an empty grid. */
@Composable
fun NoProductsCard(onAddProduct: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    ShopCard(modifier = modifier.fillMaxWidth(), padding = 28.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.segmentTrack), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = colors.muted)
            }
            Text(stringResource(R.string.empty_products), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                text = stringResource(R.string.empty_products_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(text = stringResource(R.string.add_product), onClick = onAddProduct, leadingIcon = Icons.Default.Add)
        }
    }
}
