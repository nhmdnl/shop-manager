package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.data.db.entities.MoneyEntryType
import java.time.ZoneId

/** Phone Record money (7.4): full-screen task; "+" switches to Cash in (addendum CashIn). Closes once saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordMoneyScreen(
    gateState: DayGateState,
    zoneId: ZoneId,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordMoneyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    RecordMoneySnackbarEffects(viewModel = viewModel, hostState = snackbarHostState, onSaved = onDone)
    val cashIn = state.type == MoneyEntryType.CASH_IN

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (cashIn) viewModel.setType(MoneyEntryType.EXPENSE) else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(if (cashIn) R.string.cash_in else R.string.record_money),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = colors.ink,
                        )
                        if (cashIn) {
                            Text(stringResource(R.string.cash_in_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                    }
                },
                actions = {
                    if (!cashIn && gateState is DayGateState.Ready) {
                        IconButton(onClick = { viewModel.setType(MoneyEntryType.CASH_IN) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cash_in), tint = colors.ink)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { paddingValues ->
        val content = Modifier
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        if (gateState is DayGateState.Ready) {
            RecordMoneyForm(state = state, viewModel = viewModel, zoneId = zoneId, modifier = content)
        } else {
            DayGateLockCard(gateState = gateState, zoneId = zoneId, onOpenDay = onOpenDay, onCloseDay = onCloseDay, modifier = content)
        }
    }
}
