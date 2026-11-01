package com.archsolutions.shopmanager.feature.cloud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.cloud.contract.Period
import com.archsolutions.cloud.contract.PlanDto
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.CappedFontScale
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ShopTextField
import com.archsolutions.shopmanager.core.money.Money

/** Pay for a cloud plan by Mobile Money. [onDone] goes back. */
@Composable
fun CloudPayScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudPayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CloudPayContent(
        state = state,
        actions = PayActions(
            retryLoad = viewModel::retryLoad,
            tryAgain = viewModel::tryAgain,
            select = viewModel::select,
            setMsisdn = viewModel::setMsisdn,
            pay = viewModel::pay,
        ),
        onDone = onDone,
        modifier = modifier,
    )
}

/** What the pay screen can do; the ViewModel's functions in the app, no-ops in the layout tests. */
class PayActions(
    val retryLoad: () -> Unit = {},
    val tryAgain: () -> Unit = {},
    val select: (String) -> Unit = {},
    val setMsisdn: (String) -> Unit = {},
    val pay: () -> Unit = {},
)

/** The pay screen from plain state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudPayContent(
    state: PayUiState,
    actions: PayActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShopColors.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                title = {
                    CappedFontScale {
                        Text(
                            text = stringResource(R.string.cloud_pay_title),
                            fontFamily = DisplayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = colors.ink,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(color = colors.green)
                state.loadFailed -> {
                    Message(stringResource(R.string.cloud_pay_load_failed))
                    PrimaryButton(text = stringResource(R.string.cloud_pay_retry), onClick = actions.retryLoad)
                }
                else -> when (state.phase) {
                    PayPhase.CHOOSE -> Choose(state, actions)
                    PayPhase.WAITING -> {
                        CircularProgressIndicator(color = colors.green)
                        Message(stringResource(R.string.cloud_pay_waiting))
                    }
                    PayPhase.PAID -> {
                        Message(stringResource(R.string.cloud_pay_paid))
                        PrimaryButton(text = stringResource(R.string.cloud_pay_done), onClick = onDone)
                    }
                    PayPhase.FAILED -> {
                        Message(stringResource(R.string.cloud_pay_failed), error = true)
                        PrimaryButton(text = stringResource(R.string.cloud_pay_retry), onClick = actions.tryAgain)
                    }
                    PayPhase.STILL_WAITING -> {
                        Message(stringResource(R.string.cloud_pay_still_waiting))
                        PrimaryButton(text = stringResource(R.string.cloud_pay_done), onClick = onDone)
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = if (error) MaterialTheme.colorScheme.error else LocalShopColors.current.ink,
    )
}

@Composable
private fun Choose(state: PayUiState, vm: PayActions) {
    val colors = LocalShopColors.current
    state.plans.forEach { plan ->
        PlanRow(plan, selected = plan.code == state.selected, onClick = { vm.select(plan.code) })
    }
    if (state.online) {
        Text(
            text = stringResource(R.string.cloud_pay_number),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        ShopTextField(
            value = state.msisdn,
            onValueChange = vm.setMsisdn,
            placeholder = stringResource(R.string.cloud_phone_placeholder),
            keyboardType = KeyboardType.Phone,
        )
        PrimaryButton(text = stringResource(R.string.cloud_pay_button), onClick = vm.pay, enabled = state.canPay)
    } else {
        state.instructions?.let { Message(it) }
    }
}

@Composable
private fun PlanRow(plan: PlanDto, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalShopColors.current
    val price = stringResource(
        if (plan.period == Period.MONTH) R.string.cloud_price_month else R.string.cloud_price_week,
        Money.format(plan.priceUgx),
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.paper,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.green else colors.muted),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = colors.green),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
                Text(price, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
    }
}
