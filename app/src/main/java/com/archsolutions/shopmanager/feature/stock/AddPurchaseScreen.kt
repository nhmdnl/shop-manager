package com.archsolutions.shopmanager.feature.stock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.archsolutions.shopmanager.feature.day.DayGateLockCard
import java.time.ZoneId

/** Phone Add purchase (7.3): full-screen task; returns to the stock list once saved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPurchaseScreen(
    gateState: DayGateState,
    zoneId: ZoneId,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PurchaseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedTotal by viewModel.savedTotal.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val colors = LocalShopColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.error_could_not_save)

    LaunchedEffect(savedTotal) {
        if (savedTotal != null) {
            viewModel.savedShown()
            onDone()
        }
    }
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.saveFailedShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = colors.ink,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.action_add_purchase),
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = colors.ink,
                    )
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
            PurchaseForm(state = state, viewModel = viewModel, modifier = content)
        } else {
            DayGateLockCard(
                gateState = gateState,
                zoneId = zoneId,
                onOpenDay = onOpenDay,
                onCloseDay = onCloseDay,
                modifier = content,
            )
        }
    }
}
