package com.archsolutions.shopmanager.feature.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archsolutions.shopmanager.R
import com.archsolutions.shopmanager.core.designsystem.DisplayFontFamily
import com.archsolutions.shopmanager.core.designsystem.LocalShopColors
import com.archsolutions.shopmanager.core.designsystem.components.ChipRow
import com.archsolutions.shopmanager.core.designsystem.components.PrimaryButton
import com.archsolutions.shopmanager.core.designsystem.components.SecondaryButton
import com.archsolutions.shopmanager.core.designsystem.components.ShopCard
import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.domain.staff.Role
import com.archsolutions.shopmanager.feature.pin.SetPinDialog
import com.archsolutions.shopmanager.feature.pin.rememberPinGate
import kotlinx.coroutines.launch

/** What the person is doing on this screen; held in memory only, so a sign-out or leaving forgets it. */
private sealed interface StaffDialogState {
    data object Adding : StaffDialogState
    data class AddingPin(val name: String, val role: Role) : StaffDialogState
    data class Renaming(val person: StaffEntity) : StaffDialogState
    data class ResettingPin(val person: StaffEntity) : StaffDialogState
}

/**
 * The staff list: add a person, rename, switch off or on, reset a PIN. Owner only: it asks for the Owner PIN
 * itself when entered (no prompt for a signed-in Owner), stays locked until that succeeds, and closes on sign-out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: StaffViewModel = hiltViewModel()) {
    val colors = LocalShopColors.current
    val pinGate = rememberPinGate()
    val staffEnabled by viewModel.staffEnabled.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val currentId by viewModel.currentId.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<StaffDialogState?>(null) }
    val lastOwnerMessage = stringResource(R.string.staff_last_owner)
    val blankMessage = stringResource(R.string.staff_name_blank)

    LaunchedEffect(Unit) { pinGate.run(R.string.pin_for_staff) { unlocked = true } }
    OnSignOut {
        dialog = null
        unlocked = false
        onBack()
    }
    // Unknown or off is locked: this screen only exists while staff is on.
    val open = unlocked && staffEnabled == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.paper,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.staff_title), fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, color = colors.ink)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = colors.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.paper),
            )
        },
    ) { padding ->
        if (!open) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Text(stringResource(R.string.staff_locked), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                SecondaryButton(
                    text = stringResource(R.string.staff_unlock),
                    onClick = { pinGate.run(R.string.pin_for_staff) { unlocked = true } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(people, key = { it.id }) { person ->
                    PersonCard(
                        person = person,
                        isYou = person.id == currentId,
                        onRename = { dialog = StaffDialogState.Renaming(person) },
                        onResetPin = { dialog = StaffDialogState.ResettingPin(person) },
                        onToggle = {
                            scope.launch { if (!viewModel.toggleActive(person.id)) snackbar.showSnackbar(lastOwnerMessage) }
                        },
                    )
                }
            }
            PrimaryButton(
                text = stringResource(R.string.staff_add),
                onClick = { dialog = StaffDialogState.Adding },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    when (val d = dialog) {
        null -> Unit
        StaffDialogState.Adding -> {
            var role by remember { mutableStateOf(Role.CASHIER) }
            val ownerLabel = stringResource(R.string.staff_role_owner)
            val cashierLabel = stringResource(R.string.staff_role_cashier)
            NameDialog(
                title = stringResource(R.string.staff_add_title),
                initial = "",
                confirmText = stringResource(R.string.action_next),
                onDismiss = { dialog = null },
                onConfirm = { name -> dialog = StaffDialogState.AddingPin(name, role) },
                extra = {
                    ChipRow(
                        items = listOf(Role.CASHIER, Role.OWNER),
                        selectedItem = role,
                        onItemSelected = { role = it },
                        labelProvider = { if (it == Role.OWNER) ownerLabel else cashierLabel },
                    )
                },
            )
        }
        is StaffDialogState.AddingPin -> SetPinDialog(
            onDismiss = { dialog = null },
            hint = stringResource(R.string.pin_staff_hint),
            onSet = { pin ->
                dialog = null
                scope.launch { if (viewModel.add(d.name, d.role, pin) == StaffInputProblem.BLANK_NAME) snackbar.showSnackbar(blankMessage) }
            },
        )
        is StaffDialogState.Renaming -> NameDialog(
            title = stringResource(R.string.staff_rename_title),
            initial = d.person.name,
            confirmText = stringResource(R.string.action_save),
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                scope.launch { if (viewModel.rename(d.person.id, name) == StaffInputProblem.BLANK_NAME) snackbar.showSnackbar(blankMessage) }
            },
        )
        is StaffDialogState.ResettingPin -> SetPinDialog(
            onDismiss = { dialog = null },
            hint = stringResource(R.string.pin_staff_hint),
            onSet = { pin ->
                dialog = null
                scope.launch { viewModel.resetPin(d.person.id, pin) }
            },
        )
    }
}

@Composable
private fun roleLabel(role: Role): String =
    stringResource(if (role == Role.OWNER) R.string.staff_role_owner else R.string.staff_role_cashier)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(person: StaffEntity, isYou: Boolean, onRename: () -> Unit, onResetPin: () -> Unit, onToggle: () -> Unit) {
    val colors = LocalShopColors.current
    val role = Role.fromKey(person.role)
    val canResetPin = role == Role.CASHIER || isYou
    ShopCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (person.active) colors.ink else colors.muted)
            val tags = listOfNotNull(
                role?.let { roleLabel(it) },
                if (isYou) stringResource(R.string.staff_you) else null,
                if (!person.active) stringResource(R.string.staff_inactive) else null,
            )
            Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(stringResource(R.string.staff_rename), onRename)
                if (canResetPin) ActionChip(stringResource(R.string.staff_reset_pin), onResetPin)
                ActionChip(stringResource(if (person.active) R.string.staff_switch_off else R.string.staff_switch_on), onToggle)
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit) {
    val colors = LocalShopColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = colors.segmentTrack, modifier = Modifier.heightIn(min = 48.dp)) {
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
