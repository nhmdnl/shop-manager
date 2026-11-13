package com.archsolutions.shopmanager.data.cloud

import com.archsolutions.cloud.contract.PaymentDto
import com.archsolutions.cloud.contract.PaymentRequest
import com.archsolutions.cloud.contract.PlansResponse

/** What the Pay screen needs from the cloud; a fake in its tests. Failures are [CloudException] or IOException. */
interface CloudPayments {
    suspend fun plans(): PlansResponse
    suspend fun pay(request: PaymentRequest): PaymentDto
    suspend fun payment(id: String): PaymentDto

    /** The phone the owner signed in with, as typed. */
    suspend fun signInPhone(): String?

    /** Refreshes the cached plan status after a payment is confirmed. */
    suspend fun refreshPlanStatus()
}
