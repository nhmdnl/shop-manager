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
        val first = gateInputsFlow(flowOf(settings), flowOf(null)).first()
        assertEquals(true, first.sharedPinRequired)
        assertEquals(
            GateDecision.ASK_SHARED_PIN,
            decideGate(first.staffEnabled, first.role, Permission.VOID_SALE, first.sharedPinRequired),
        )
    }

    @Test
    fun `no pin set yields no shared pin`() = runTest {
        val first = gateInputsFlow(flowOf(ShopSettings()), flowOf(null)).first()
        assertEquals(false, first.sharedPinRequired)
    }
}
