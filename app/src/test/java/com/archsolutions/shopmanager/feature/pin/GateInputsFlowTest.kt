package com.archsolutions.shopmanager.feature.pin

import com.archsolutions.shopmanager.data.settings.ShopSettings
import com.archsolutions.shopmanager.domain.staff.GateDecision
import com.archsolutions.shopmanager.domain.staff.Permission
import com.archsolutions.shopmanager.domain.staff.decideGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GateInputsFlowTest {
    @Test
    fun `shared pin set and staff off asks for the pin on the first emission`() = runTest {
        val settings = ShopSettings(pinEnabled = true, pinHash = "abc", staffEnabled = false)
        val first = gateInputsFlow(flowOf(settings), flowOf(null), flowOf(false)).first()
        assertEquals(true, first.sharedPinRequired)
        assertEquals(
            GateDecision.ASK_SHARED_PIN,
            decideGate(first.staffEnabled, first.role, Permission.VOID_SALE, first.sharedPinRequired),
        )
    }

    @Test
    fun `no pin set yields no shared pin`() = runTest {
        val first = gateInputsFlow(flowOf(ShopSettings()), flowOf(null), flowOf(false)).first()
        assertEquals(false, first.sharedPinRequired)
    }

    @Test
    fun `staff on and nobody signed in asks for an Owner PIN at the flow level`() = runTest {
        val first = gateInputsFlow(flowOf(ShopSettings(staffEnabled = true)), flowOf(null), flowOf(true)).first()
        Permission.entries.forEach {
            assertEquals(
                GateDecision.ASK_OWNER_PIN,
                decideGate(first.staffEnabled, first.role, it, first.sharedPinRequired),
            )
        }
    }

    @Test
    fun `setting on but no active Owner counts as staff off`() = runTest {
        val first = gateInputsFlow(flowOf(ShopSettings(staffEnabled = true)), flowOf(null), flowOf(false)).first()
        assertEquals(false, first.staffEnabled)
    }

    @Test
    fun `pin needed hint follows the real decision`() = runTest {
        val owner = com.archsolutions.shopmanager.data.db.entities.StaffEntity(id = 1, name = "O", role = "OWNER", pinHash = "x", createdAt = 0)
        val cashier = owner.copy(id = 2, role = "CASHIER")
        val on = ShopSettings(staffEnabled = true)
        assertEquals(false, pinNeededFlow(flowOf(on), flowOf(owner), flowOf(true), Permission.VOID_SALE).first())
        assertEquals(true, pinNeededFlow(flowOf(on), flowOf(cashier), flowOf(true), Permission.VOID_SALE).first())
        assertEquals(true, pinNeededFlow(flowOf(on), flowOf(null), flowOf(true), Permission.CORRECT_STOCK).first())
        // Staff off follows the shared PIN.
        assertEquals(true, pinNeededFlow(flowOf(ShopSettings(pinEnabled = true, pinHash = "h")), flowOf(null), flowOf(false), Permission.VOID_SALE).first())
        assertEquals(false, pinNeededFlow(flowOf(ShopSettings()), flowOf(null), flowOf(false), Permission.VOID_SALE).first())
    }
}
