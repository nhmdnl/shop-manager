package com.archsolutions.shopmanager.domain.staff

import org.junit.Assert.assertEquals
import org.junit.Test

class GateDecisionTest {
    @Test
    fun `staff off with no shared pin runs`() {
        Permission.entries.forEach {
            assertEquals(GateDecision.RUN, decideGate(false, null, it, sharedPinRequired = false))
        }
    }

    @Test
    fun `staff off with a shared pin asks for it`() {
        Permission.entries.forEach {
            assertEquals(GateDecision.ASK_SHARED_PIN, decideGate(false, null, it, sharedPinRequired = true))
        }
    }

    @Test
    fun `owner runs everything`() {
        Permission.entries.forEach {
            assertEquals(GateDecision.RUN, decideGate(true, Role.OWNER, it, sharedPinRequired = true))
        }
    }

    @Test
    fun `cashier runs sell and asks an owner for the rest`() {
        Permission.entries.forEach {
            val expected = if (it == Permission.SELL) GateDecision.RUN else GateDecision.ASK_OWNER_PIN
            assertEquals(expected, decideGate(true, Role.CASHIER, it, sharedPinRequired = false))
        }
    }

    @Test
    fun `nobody signed in asks an owner`() {
        Permission.entries.forEach {
            assertEquals(GateDecision.ASK_OWNER_PIN, decideGate(true, null, it, sharedPinRequired = false))
        }
    }
}
