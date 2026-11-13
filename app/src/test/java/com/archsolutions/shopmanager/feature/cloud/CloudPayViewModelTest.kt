package com.archsolutions.shopmanager.feature.cloud

import com.archsolutions.cloud.contract.PaymentDto
import com.archsolutions.cloud.contract.PaymentRequest
import com.archsolutions.cloud.contract.PaymentStatus
import com.archsolutions.cloud.contract.PlanDto
import com.archsolutions.cloud.contract.PlansResponse
import com.archsolutions.cloud.contract.Period
import com.archsolutions.shopmanager.data.cloud.CloudPayments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private val MONTH = PlanDto("m", "Monthly", 5_000, Period.MONTH)
private val WEEK = PlanDto("w", "Weekly", 6_000, Period.WEEK)

private class FakePayments : CloudPayments {
    var plans = PlansResponse(listOf(MONTH, WEEK), onlinePayment = true)
    var plansFailure: Throwable? = null
    var payFailure: Throwable? = null
    var phone: String? = "0772 123 456"
    val requests = mutableListOf<PaymentRequest>()
    /** One entry per poll; the last one repeats. A Throwable is thrown instead. */
    var answers: List<Any> = listOf(PaymentStatus.PENDING)
    var polls = 0
    var refreshes = 0

    override suspend fun plans(): PlansResponse {
        plansFailure?.let { throw it }
        return plans
    }

    override suspend fun pay(request: PaymentRequest): PaymentDto {
        payFailure?.let { throw it }
        requests += request
        return dto(PaymentStatus.PENDING)
    }

    override suspend fun payment(id: String): PaymentDto {
        val answer = answers[minOf(polls, answers.lastIndex)]
        polls++
        if (answer is Throwable) throw answer
        return dto(answer as PaymentStatus)
    }

    override suspend fun signInPhone() = phone
    override suspend fun refreshPlanStatus() { refreshes++ }

    private fun dto(status: PaymentStatus) = PaymentDto("p1", "m", 5_000, status, createdAt = 0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CloudPayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pay = FakePayments()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): CloudPayViewModel = CloudPayViewModel(pay).also { runCurrent() }

    private fun TestScope.startPay(vm: CloudPayViewModel) {
        vm.pay(); runCurrent()
    }

    @Test fun `offline payment shows instructions and no pay button`() = runTest(dispatcher) {
        pay.plans = PlansResponse(listOf(MONTH), onlinePayment = false, payInstructions = "Send to 0700 000 000")
        val s = vm().state.value
        assertFalse(s.online)
        assertEquals("Send to 0700 000 000", s.instructions)
        assertFalse(s.canPay)
    }

    @Test fun `msisdn defaults to sign-in phone`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals("0772 123 456", vm.state.value.msisdn)
        assertEquals("m", vm.state.value.selected)
        assertTrue(vm.state.value.canPay)
        vm.setMsisdn("0701")
        startPay(vm)
        assertEquals("0701", pay.requests.single().msisdn)
    }

    @Test fun `confirmed after 2 polls goes to PAID and refreshes status`() = runTest(dispatcher) {
        pay.answers = listOf(PaymentStatus.PENDING, PaymentStatus.CONFIRMED)
        val vm = vm()
        startPay(vm)
        assertEquals(PayPhase.WAITING, vm.state.value.phase)
        advanceTimeBy(3_001); runCurrent()
        assertEquals(PayPhase.WAITING, vm.state.value.phase)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(PayPhase.PAID, vm.state.value.phase)
        assertEquals(2, pay.polls)
        assertEquals(1, pay.refreshes)
    }

    @Test fun `no answer for 3 minutes goes to STILL_WAITING`() = runTest(dispatcher) {
        val vm = vm()
        startPay(vm)
        advanceTimeBy(179_000); runCurrent()
        assertEquals(PayPhase.WAITING, vm.state.value.phase)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(PayPhase.STILL_WAITING, vm.state.value.phase)
        assertEquals(0, pay.refreshes)
    }

    @Test fun `failed goes to FAILED`() = runTest(dispatcher) {
        pay.answers = listOf(PaymentStatus.FAILED)
        val vm = vm()
        startPay(vm)
        advanceTimeBy(3_001); runCurrent()
        assertEquals(PayPhase.FAILED, vm.state.value.phase)
    }

    @Test fun `a failed pay call goes to FAILED`() = runTest(dispatcher) {
        pay.payFailure = IOException("offline")
        val vm = vm()
        startPay(vm)
        assertEquals(PayPhase.FAILED, vm.state.value.phase)
    }

    @Test fun `polling errors are ignored until the answer arrives`() = runTest(dispatcher) {
        pay.answers = listOf(IOException("x"), IOException("y"), PaymentStatus.CONFIRMED)
        val vm = vm()
        startPay(vm)
        advanceTimeBy(9_001); runCurrent()
        assertEquals(PayPhase.PAID, vm.state.value.phase)
    }

    @Test fun `a failed plans load offers a retry`() = runTest(dispatcher) {
        pay.plansFailure = IOException("offline")
        val vm = vm()
        assertTrue(vm.state.value.loadFailed)
        pay.plansFailure = null
        vm.retryLoad(); runCurrent()
        assertFalse(vm.state.value.loadFailed)
        assertEquals(2, vm.state.value.plans.size)
    }

    @Test fun `retry after FAILED goes back to CHOOSE`() = runTest(dispatcher) {
        pay.payFailure = IOException("offline")
        val vm = vm()
        startPay(vm)
        vm.tryAgain()
        assertEquals(PayPhase.CHOOSE, vm.state.value.phase)
    }
}
