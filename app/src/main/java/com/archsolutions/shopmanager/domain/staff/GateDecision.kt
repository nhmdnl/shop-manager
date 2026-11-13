package com.archsolutions.shopmanager.domain.staff

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

enum class GateDecision { RUN, ASK_OWNER_PIN, ASK_SHARED_PIN }

/** Staff off: the shared PIN gate as before. Staff on: roles decide, and anyone short of the permission needs an Owner's PIN. */
fun decideGate(staffEnabled: Boolean, current: Role?, permission: Permission, sharedPinRequired: Boolean): GateDecision = when {
    !staffEnabled -> if (sharedPinRequired) GateDecision.ASK_SHARED_PIN else GateDecision.RUN
    current.can(permission) -> GateDecision.RUN
    else -> GateDecision.ASK_OWNER_PIN
}

/** What [decideGate] needs, taken together so a decision never mixes old and new values. */
data class GateInputs(val staffEnabled: Boolean, val role: Role?, val sharedPinRequired: Boolean)

/** Waits for the first real state (null means not known yet, never RUN), then decides. */
suspend fun StateFlow<GateInputs?>.decide(permission: Permission): GateDecision {
    val s = value ?: filterNotNull().first()
    return decideGate(s.staffEnabled, s.role, permission, s.sharedPinRequired)
}
