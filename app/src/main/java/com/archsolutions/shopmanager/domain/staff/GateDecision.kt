package com.archsolutions.shopmanager.domain.staff

enum class GateDecision { RUN, ASK_OWNER_PIN, ASK_SHARED_PIN }

/** Staff off: the shared PIN gate as before. Staff on: roles decide, and anyone short of the permission needs an Owner's PIN. */
fun decideGate(staffEnabled: Boolean, current: Role?, permission: Permission, sharedPinRequired: Boolean): GateDecision = when {
    !staffEnabled -> if (sharedPinRequired) GateDecision.ASK_SHARED_PIN else GateDecision.RUN
    current.can(permission) -> GateDecision.RUN
    else -> GateDecision.ASK_OWNER_PIN
}
