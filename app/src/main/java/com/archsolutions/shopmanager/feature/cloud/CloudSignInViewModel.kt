package com.archsolutions.shopmanager.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.shopmanager.data.cloud.CloudAccount
import com.archsolutions.shopmanager.data.cloud.CloudException
import com.archsolutions.shopmanager.data.cloud.LinkOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class SignInStep { PHONE, CODE, PROFILE, REPLACE, DONE }

enum class SignInError { PHONE, CODE_WRONG, CODE_EXPIRED, TOO_MANY, OFFLINE, OTHER }

data class SignInUiState(
    val step: SignInStep = SignInStep.PHONE,
    val phone: String = "",
    val code: String = "",
    val name: String = "",
    val consent: Boolean = false,
    val busy: Boolean = false,
    val error: SignInError? = null,
    /** Whole minutes to wait, set with [SignInError.TOO_MANY]. */
    val retryMinutes: Int = 1,
    /** "Send a new code" is on 60 s after a code went out. */
    val canResend: Boolean = false,
    /** The owner answered No to "Replace the old tablet?": the screen goes back. */
    val left: Boolean = false,
)

private const val RESEND_AFTER_MS = 60_000L

@HiltViewModel
class CloudSignInViewModel @Inject constructor(
    private val account: CloudAccount,
) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    // Only here: never in SavedStateHandle or on disk.
    private var ticket: String? = null
    private var resendJob: Job? = null

    fun setPhone(value: String) = _state.update { it.copy(phone = value, error = null) }
    fun setCode(value: String) = _state.update { it.copy(code = value, error = null) }
    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun setConsent(value: Boolean) = _state.update { it.copy(consent = value) }

    fun sendCode() {
        val s = _state.value
        if (s.busy || s.phone.isEmpty()) return
        run(forRequest = true) {
            account.requestCode(s.phone) // as typed; the server normalizes
            startResendTimer()
            _state.update { it.copy(step = SignInStep.CODE, code = "") }
        }
    }

    fun verify() {
        val s = _state.value
        if (s.busy || s.code.isEmpty()) return
        run {
            val response = account.verify(s.phone, s.code)
            ticket = response.ownerTicket
            if (response.newOwner) {
                _state.update { it.copy(step = SignInStep.PROFILE) }
            } else {
                finishLink(replace = false, name = null, consent = false)
            }
        }
    }

    fun submitProfile() {
        val s = _state.value
        if (s.busy || !s.consent) return
        run { finishLink(replace = false, name = s.name.takeIf { it.isNotBlank() }, consent = true) }
    }

    fun replaceOldTablet() {
        val s = _state.value
        if (s.busy) return
        run { finishLink(replace = true, name = s.name.takeIf { it.isNotBlank() }, consent = s.consent) }
    }

    fun keepOldTablet() {
        ticket = null
        _state.update { it.copy(left = true) }
    }

    private suspend fun finishLink(replace: Boolean, name: String?, consent: Boolean) {
        val t = ticket ?: return
        val outcome = account.link(t, _state.value.phone, replace, name, consent)
        _state.update {
            when (outcome) {
                LinkOutcome.Linked -> it.copy(step = SignInStep.DONE)
                LinkOutcome.TabletExists -> it.copy(step = SignInStep.REPLACE)
            }
        }
        if (outcome == LinkOutcome.Linked) ticket = null
    }

    private fun startResendTimer() {
        resendJob?.cancel()
        _state.update { it.copy(canResend = false) }
        resendJob = viewModelScope.launch {
            delay(RESEND_AFTER_MS)
            _state.update { it.copy(canResend = true) }
        }
    }

    /** Runs [block] with the busy flag; no exception leaves it. */
    private fun run(forRequest: Boolean = false, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = mapError(e, forRequest)
                _state.update {
                    it.copy(error = error, retryMinutes = if (error == SignInError.TOO_MANY) minutesFor(e) else it.retryMinutes)
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun mapError(e: Exception, forRequest: Boolean): SignInError = when {
        e is IOException -> SignInError.OFFLINE
        e is CloudException -> when {
            e.code == ErrorCode.RATE_LIMITED || e.http == 429 -> SignInError.TOO_MANY
            e.code == ErrorCode.OTP_INVALID -> SignInError.CODE_WRONG
            e.code == ErrorCode.OTP_EXPIRED -> SignInError.CODE_EXPIRED
            e.code == ErrorCode.BAD_REQUEST && forRequest -> SignInError.PHONE
            else -> SignInError.OTHER
        }
        else -> SignInError.OTHER
    }

    private fun minutesFor(e: Exception): Int {
        val seconds = (e as? CloudException)?.retryAfterSeconds ?: return 1
        return ((seconds + 59) / 60).coerceAtLeast(1)
    }
}
