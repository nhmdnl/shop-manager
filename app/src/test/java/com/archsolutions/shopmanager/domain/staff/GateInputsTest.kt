package com.archsolutions.shopmanager.domain.staff

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GateInputsTest {
    @Test
    fun `unknown state never yields RUN - run waits for the first real state and then decides`() = runTest(UnconfinedTestDispatcher()) {
        val state = MutableStateFlow<GateInputs?>(null)
        var decision: GateDecision? = null
        val job = launch { decision = state.decide(Permission.VOID_SALE) }
        assertNull(decision)
        state.value = GateInputs(staffEnabled = true, role = Role.CASHIER, sharedPinRequired = false)
        job.join()
        assertEquals(GateDecision.ASK_OWNER_PIN, decision)
    }

    @Test
    fun `staff off with no pin runs only after the real emission`() = runTest(UnconfinedTestDispatcher()) {
        val state = MutableStateFlow<GateInputs?>(null)
        var decision: GateDecision? = null
        val job = launch { decision = state.decide(Permission.CORRECT_STOCK) }
        assertNull(decision)
        state.value = GateInputs(staffEnabled = false, role = null, sharedPinRequired = false)
        job.join()
        assertEquals(GateDecision.RUN, decision)
    }
}
