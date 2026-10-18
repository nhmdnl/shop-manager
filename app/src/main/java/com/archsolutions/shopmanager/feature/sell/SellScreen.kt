package com.archsolutions.shopmanager.feature.sell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.feature.day.DayGateLockCard
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    isExpanded: Boolean,
    gateState: DayGateState,
    zoneId: ZoneId,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.action_new_sale),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        if (gateState !is DayGateState.Ready) {
            DayGateLockCard(
                gateState = gateState,
                zoneId = zoneId,
                onOpenDay = onOpenDay,
                onCloseDay = onCloseDay,
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = if (isExpanded) 28.dp else 16.dp, vertical = 12.dp),
            )
            return@Scaffold
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.empty_products),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
            )
        }
    }
}
