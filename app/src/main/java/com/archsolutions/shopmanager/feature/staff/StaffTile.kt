package com.archsolutions.shopmanager.feature.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.fontScaled

/**
 * One person on the sign-in screen: their initial in a circle, the name and the role. [roleLabel] is empty for an
 * unknown role, and then no role line is shown. The name wraps; it is never cut short.
 */
@Composable
fun StaffTile(name: String, roleLabel: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShopColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = fontScaled(112.dp))
            .clip(shape)
            .background(if (selected) colors.greenTint else colors.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.green else colors.line), shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // The initial is decoration: the name already says who this is, so TalkBack must not read it as well.
        Box(
            modifier = Modifier
                .size(fontScaled(44.dp))
                .clip(CircleShape)
                .background(if (selected) colors.green else colors.segmentTrack)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = if (selected) colors.onGreen else colors.ink,
            )
        }
        Text(
            text = name,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = colors.ink,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (roleLabel.isNotEmpty()) {
            Text(roleLabel, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}
