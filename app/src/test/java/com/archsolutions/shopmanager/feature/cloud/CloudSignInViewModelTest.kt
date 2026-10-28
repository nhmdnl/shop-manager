package com.archsolutions.shopmanager.feature.cloud

import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.cloud.contract.VerifyResponse
import com.archsolutions.shopmanager.data.cloud.CloudAccount
import com.archsolutions.shopmanager.data.cloud.CloudException
import com.archsolutions.shopmanager.data.cloud.LinkOutcome
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

private class LinkCall(val ticket: String, val phone: String, val replace: Boolean, val name: String?, val consent: Boolean)

private class FakeAccount : CloudAccount {
    val sentPhones = mutableListOf<String>()
    val links = mutableListOf<LinkCall>()
    var newOwner = false
    var requestFailure: Throwable? = null
    var verifyFailure: Throwable? = null
    var linkFailure: Throwable? = null
    val linkResults = ArrayDeque<LinkOutcome>()

    override suspend fun requestCode(phone: String) {
        sentPhones += phone
        requestFailure?.let { throw it }
    }

    override suspend fun verify(phone: String, code: String): VerifyResponse {
        verifyFailure?.let { throw it }
        return VerifyResponse(ownerTicket = "ticket-1", newOwner = newOwner)
    }

    override suspend fun link(ticket: String, phone: String, replace: Boolean, ownerName: String?, consent: Boolean): LinkOutcome {
        links += LinkCall(ticket, phone, replace, ownerName, consent)
        linkFailure?.let { throw it }
        return linkResults.removeFirstOrNull() ?: LinkOutcome.Linked
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSignInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val account = FakeAccount()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm() = CloudSignInViewModel(account)

    private fun TestScope.toCode(vm: CloudSignInViewModel, phone: String = "0772123456") {
        vm.setPhone(phone); vm.sendCode(); runCurrent()
    }

    private fun TestScope.verify(vm: CloudSignInViewModel, code: String = "123456") {
        vm.setCode(code); vm.verify(); runCurrent()
    }

    @Test fun `existing owner goes phone code done`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(SignInStep.PHONE, vm.state.value.step)
        toCode(vm)
        assertEquals(SignInStep.CODE, vm.state.value.step)
        verify(vm)
        assertEquals(SignInStep.DONE, vm.state.value.step)
        val call = account.links.single()
        assertEquals("ticket-1", call.ticket)
        assertFalse(call.replace)
        assertNull(call.name)
    }

    @Test fun `new owner must tick consent before link`() = runTest(dispatcher) {
        account.newOwner = true
        val vm = vm()
        toCode(vm)
        verify(vm)
        assertEquals(SignInStep.PROFILE, vm.state.value.step)
        vm.setName("Amina")
        vm.submitProfile(); runCurrent()
        assertTrue(account.links.isEmpty())
        vm.setConsent(true)
        vm.submitProfile(); runCurrent()
        assertEquals(SignInStep.DONE, vm.state.value.step)
        val call = account.links.single()
        assertEquals("Amina", call.name)
        assertTrue(call.consent)
    }

    @Test fun `tablet exists asks replace and yes replaces`() = runTest(dispatcher) {
        account.linkResults += LinkOutcome.TabletExists
        val vm = vm()
        toCode(vm)
        verify(vm)
        assertEquals(SignInStep.REPLACE, vm.state.value.step)
        vm.replaceOldTablet(); runCurrent()
        assertEquals(SignInStep.DONE, vm.state.value.step)
        assertEquals(listOf(false, true), account.links.map { it.replace })
        assertEquals("ticket-1", account.links.last().ticket)
    }

    @Test fun `keeping the old tablet leaves without linking again`() = runTest(dispatcher) {
        account.linkResults += LinkOutcome.TabletExists
        val vm = vm()
        toCode(vm)
        verify(vm)
        vm.keepOldTablet()
        assertEquals(1, account.links.size)
        assertEquals(SignInStep.REPLACE, vm.state.value.step)
        assertTrue(vm.state.value.left)
    }

    @Test fun `wrong code shows CODE_WRONG and stays on CODE`() = runTest(dispatcher) {
        account.verifyFailure = CloudException(ErrorCode.OTP_INVALID, 401, null, "bad")
        val vm = vm()
        toCode(vm)
        verify(vm)
        assertEquals(SignInStep.CODE, vm.state.value.step)
        assertEquals(SignInError.CODE_WRONG, vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `expired code shows CODE_EXPIRED`() = runTest(dispatcher) {
        account.verifyFailure = CloudException(ErrorCode.OTP_EXPIRED, 401, null, "old")
        val vm = vm()
        toCode(vm)
        verify(vm)
        assertEquals(SignInError.CODE_EXPIRED, vm.state.value.error)
    }

    @Test fun `429 shows TOO_MANY with minutes`() = runTest(dispatcher) {
        account.requestFailure = CloudException(null, 429, 61, "slow down")
        val vm = vm()
        vm.setPhone("0772123456"); vm.sendCode(); runCurrent()
        assertEquals(SignInStep.PHONE, vm.state.value.step)
        assertEquals(SignInError.TOO_MANY, vm.state.value.error)
        assertEquals(2, vm.state.value.retryMinutes)
    }

    @Test fun `RATE_LIMITED code also shows TOO_MANY`() = runTest(dispatcher) {
        account.requestFailure = CloudException(ErrorCode.RATE_LIMITED, 429, 60, "slow down")
        val vm = vm()
        vm.setPhone("0772123456"); vm.sendCode(); runCurrent()
        assertEquals(SignInError.TOO_MANY, vm.state.value.error)
        assertEquals(1, vm.state.value.retryMinutes)
    }

    @Test fun `offline shows OFFLINE`() = runTest(dispatcher) {
        account.requestFailure = IOException("no network")
        val vm = vm()
        vm.setPhone("0772123456"); vm.sendCode(); runCurrent()
        assertEquals(SignInError.OFFLINE, vm.state.value.error)
        assertEquals(SignInStep.PHONE, vm.state.value.step)
    }

    @Test fun `bad request on send shows PHONE`() = runTest(dispatcher) {
        account.requestFailure = CloudException(ErrorCode.BAD_REQUEST, 400, null, "phone")
        val vm = vm()
        vm.setPhone("abc"); vm.sendCode(); runCurrent()
        assertEquals(SignInError.PHONE, vm.state.value.error)
    }

    @Test fun `a non-cloud failure from link shows OTHER and never escapes`() = runTest(dispatcher) {
        account.linkFailure = IllegalStateException("keystore")
        val vm = vm()
        toCode(vm)
        verify(vm)
        assertEquals(SignInError.OTHER, vm.state.value.error)
        assertEquals(SignInStep.CODE, vm.state.value.step)
        assertFalse(vm.state.value.busy)
    }

    @Test fun `phone sent as typed`() = runTest(dispatcher) {
        val vm = vm()
        toCode(vm, "0772 123456")
        assertEquals(listOf("0772 123456"), account.sentPhones)
    }

    @Test fun `send a new code unlocks after 60 seconds`() = runTest(dispatcher) {
        val vm = vm()
        toCode(vm)
        assertFalse(vm.state.value.canResend)
        advanceTimeBy(59_000); runCurrent()
        assertFalse(vm.state.value.canResend)
        advanceTimeBy(1_001); runCurrent()
        assertTrue(vm.state.value.canResend)
        vm.sendCode(); runCurrent()
        assertFalse(vm.state.value.canResend)
        assertEquals(2, account.sentPhones.size)
    }
}
