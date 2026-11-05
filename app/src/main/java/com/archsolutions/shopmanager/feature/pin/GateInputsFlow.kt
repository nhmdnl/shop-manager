package com.archsolutions.shopmanager.feature.pin

import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.settings.ShopSettings
import com.archsolutions.shopmanager.domain.staff.GateDecision
import com.archsolutions.shopmanager.domain.staff.GateInputs
import com.archsolutions.shopmanager.domain.staff.Permission
import com.archsolutions.shopmanager.domain.staff.decideGate
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Every field comes from the same settings emission, so there is no stale "no PIN" between them. [staffActive] is
 * the repo's "setting on and an active Owner exists"; the raw setting alone is never used to decide.
 */
internal fun gateInputsFlow(settings: Flow<ShopSettings>, current: Flow<StaffEntity?>, staffActive: Flow<Boolean>): Flow<GateInputs> =
    combine(settings, current, staffActive) { s, person, active ->
        GateInputs(active, person?.let { Role.fromKey(it.role) }, s.pinEnabled && s.pinHash.isNotEmpty())
    }

/** Whether a PIN prompt would appear for [permission] right now (the real gate decision), for the "PIN needed" hints. */
internal fun pinNeededFlow(
    settings: Flow<ShopSettings>,
    current: Flow<StaffEntity?>,
    staffActive: Flow<Boolean>,
    permission: Permission,
): Flow<Boolean> = gateInputsFlow(settings, current, staffActive).map {
    decideGate(it.staffEnabled, it.role, permission, it.sharedPinRequired) != GateDecision.RUN
}
