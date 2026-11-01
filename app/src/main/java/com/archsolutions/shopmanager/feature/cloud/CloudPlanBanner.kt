package com.archsolutions.shopmanager.feature.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard

/** BIL-08: the plan is ending, late or paused. A tap opens Pay. Rust, because every one of them is a warning. */
@Composable
fun CloudPlanBanner(notice: CloudNotice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    val text = stringResource(
        when (notice) {
            CloudNotice.ENDS_SOON -> R.string.cloud_notice_ends_soon
            CloudNotice.GRACE -> R.string.cloud_notice_grace
            CloudNotice.PAUSED -> R.string.cloud_notice_paused
        },
    )
    ShopCard(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        borderColor = colors.rust.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.rustTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = colors.rust)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
