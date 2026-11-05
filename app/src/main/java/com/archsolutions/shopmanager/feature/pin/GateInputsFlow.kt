package com.archsolutions.shopmanager.feature.pin

import com.archsolutions.shopmanager.data.db.entities.StaffEntity
import com.archsolutions.shopmanager.data.settings.ShopSettings
import com.archsolutions.shopmanager.domain.staff.GateInputs
import com.archsolutions.shopmanager.domain.staff.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Every field comes from the same settings emission, so there is no stale "no PIN" between them. */
internal fun gateInputsFlow(settings: Flow<ShopSettings>, current: Flow<StaffEntity?>): Flow<GateInputs> =
    combine(settings, current) { s, person ->
        GateInputs(s.staffEnabled, person?.let { Role.fromKey(it.role) }, s.pinEnabled && s.pinHash.isNotEmpty())
    }
