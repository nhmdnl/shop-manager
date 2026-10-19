package com.archsolutions.shopmanager.feature.day

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.app.DayGateState
import com.archsolutions.shopmanager.core.designsystem.components.DayLockCard
import com.archsolutions.shopmanager.core.designsystem.components.InkButton
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.util.DayFormat
import java.time.ZoneId

/** The lock card for each gate state that blocks trading (6.3). Draws nothing while trading is open. */
@Composable
fun DayGateLockCard(
    gateState: DayGateState,
    zoneId: ZoneId,
    onOpenDay: () -> Unit,
    onCloseDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    when (gateState) {
        is DayGateState.PreviousDayStillOpen -> {
            val date = DayFormat.date(gateState.day.date, "EEE d MMM", locale)
            DayLockCard(
                title = stringResource(R.string.yesterday_open_title),
                body = stringResource(R.string.yesterday_open_body, date),
                modifier = modifier,
                action = {
                    InkButton(
                        text = stringResource(R.string.close_date, date),
                        onClick = onCloseDay,
                        modifier = Modifier.fillMaxWidth(),
                        height = 64.dp,
                    )
                },
            )
        }
        is DayGateState.TodayClosed -> DayLockCard(
            title = stringResource(
                R.string.day_closed_at,
                gateState.day.closedAt?.let { DayFormat.time(it, zoneId) }.orEmpty(),
            ),
            body = stringResource(R.string.selling_opens_tomorrow),
            modifier = modifier,
        )
        DayGateState.OpenDayRequired, DayGateState.DayNotOpen -> DayLockCard(
            title = stringResource(R.string.day_not_open),
            body = stringResource(R.string.day_locked_body),
            modifier = modifier,
            action = {
                PrimaryButton(
                    text = stringResource(R.string.open_the_day),
                    onClick = onOpenDay,
                    height = 64.dp,
                    leadingIcon = Icons.Outlined.AccountBalanceWallet,
                )
            },
        )
        DayGateState.Loading, is DayGateState.Ready -> Unit
    }
}
