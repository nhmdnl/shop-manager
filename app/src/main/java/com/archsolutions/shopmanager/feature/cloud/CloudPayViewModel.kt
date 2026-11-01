package com.archsolutions.shopmanager.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archsolutions.cloud.contract.PaymentRequest
import com.archsolutions.cloud.contract.PaymentStatus
import com.archsolutions.cloud.contract.PlanDto
import com.archsolutions.shopmanager.data.cloud.CloudPayments
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PayPhase { CHOOSE, WAITING, PAID, FAILED, STILL_WAITING }

data class PayUiState(
    val plans: List<PlanDto> = emptyList(),
    val selected: String? = null,
    val msisdn: String = "",
    val online: Boolean = false,
    val instructions: String? = null,
    val phase: PayPhase = PayPhase.CHOOSE,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
) {
    /** Online payment only, with a plan and a number chosen. */
    val canPay: Boolean get() = online && selected != null && msisdn.isNotBlank() && phase == PayPhase.CHOOSE
}

private const val POLL_EVERY_MS = 3_000L
private const val GIVE_UP_AFTER_MS = 180_000L

@HiltViewModel
class CloudPayViewModel @Inject constructor(
    private val payments: CloudPayments,
) : ViewModel() {
    private val _state = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = _state.asStateFlow()

    private var payJob: Job? = null

    init {
        load()
    }

    fun retryLoad() = load()

    private fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val response = payments.plans()
                val phone = payments.signInPhone().orEmpty()
                _state.update {
                    it.copy(
                        plans = response.plans,
                        selected = it.selected?.takeIf { code -> response.plans.any { p -> p.code == code } }
                            ?: response.plans.firstOrNull()?.code,
                        msisdn = it.msisdn.ifEmpty { phone }, // as stored; the server normalizes
                        online = response.onlinePayment,
                        instructions = response.payInstructions,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun select(code: String) = _state.update { if (it.phase == PayPhase.CHOOSE) it.copy(selected = code) else it }

    fun setMsisdn(value: String) = _state.update { if (it.phase == PayPhase.CHOOSE) it.copy(msisdn = value) else it }

    fun pay() {
        val s = _state.value
        val plan = s.selected
        if (!s.canPay || plan == null || payJob?.isActive == true) return
        _state.update { it.copy(phase = PayPhase.WAITING) }
        payJob = viewModelScope.launch {
            val phase = try {
                val id = payments.pay(PaymentRequest(plan, s.msisdn)).paymentId
                poll(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PayPhase.FAILED
            }
            _state.update { it.copy(phase = phase) }
        }
    }

    /** Asks every 3 s; an error on one ask is ignored. Gives up after 3 minutes. */
    private suspend fun poll(id: String): PayPhase {
        var waited = 0L
        while (waited < GIVE_UP_AFTER_MS) {
            delay(POLL_EVERY_MS)
            waited += POLL_EVERY_MS
            val status = try {
                payments.payment(id).status
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            when (status) {
                PaymentStatus.CONFIRMED -> {
                    try {
                        payments.refreshPlanStatus()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                    return PayPhase.PAID
                }
                PaymentStatus.FAILED -> return PayPhase.FAILED
                PaymentStatus.PENDING -> Unit
            }
        }
        return PayPhase.STILL_WAITING
    }

    fun tryAgain() {
        payJob?.cancel()
        _state.update { it.copy(phase = PayPhase.CHOOSE) }
    }
}
