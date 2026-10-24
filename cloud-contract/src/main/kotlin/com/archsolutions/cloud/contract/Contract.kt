package com.archsolutions.cloud.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire contract between the Arch-SME app and Arch-SME Cloud. Money is whole UGX (Long); times are epoch milliseconds. */
const val CONTRACT_VERSION = 1

/** Version of the privacy notice and terms the app and the web page ask the owner to accept. */
const val CONSENT_VERSION = "2026-10-24"

object ArchHeaders {
    const val CONTRACT = "X-Arch-Contract"
    const val APP_VERSION = "X-Arch-App-Version"
    const val CONTENT_SHA256 = "X-Content-SHA256"
}

object Features {
    const val BACKUP = "backup"
    const val LIVE = "live"
}

val ArchJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class CodeRequest(val phone: String)

/** Only the tablet signs in through /v1. */
@Serializable
enum class Purpose {
    @SerialName("tablet") TABLET,
}

@Serializable
data class VerifyRequest(
    val phone: String,
    val code: String,
    @SerialName("for") val purpose: Purpose,
)

@Serializable
data class VerifyResponse(val ownerTicket: String, val newOwner: Boolean)

@Serializable
data class LinkRequest(
    val ownerTicket: String,
    val label: String,
    val shopName: String,
    val language: String = "en",
    val replace: Boolean = false,
    val ownerName: String? = null,
    val consentVersion: String? = null,
)

@Serializable
data class LinkResponse(val deviceToken: String, val shopId: String)

@Serializable
enum class PlanState { TRIAL, ACTIVE, GRACE, LAPSED }

@Serializable
enum class Period { WEEK, MONTH }

@Serializable
data class ShopStatus(
    val plan: String,
    val state: PlanState,
    val trialEnd: Long? = null,
    val periodEnd: Long? = null,
    val graceEnd: Long? = null,
    val features: List<String>,
    val resync: Boolean = false,
)

@Serializable
data class PlanDto(val code: String, val name: String, val priceUgx: Long, val period: Period)

@Serializable
data class PlansResponse(
    val plans: List<PlanDto>,
    val onlinePayment: Boolean,
    val payInstructions: String? = null,
)

@Serializable
data class PaymentRequest(val planCode: String, val msisdn: String)

@Serializable
enum class PaymentStatus { PENDING, CONFIRMED, FAILED }

@Serializable
data class PaymentDto(
    val paymentId: String,
    val planCode: String,
    val amountUgx: Long,
    val status: PaymentStatus,
    val createdAt: Long,
    val confirmedAt: Long? = null,
)

@Serializable
data class BackupCreated(val backupId: String)

@Serializable
data class BackupDto(val backupId: String, val backupTime: Long, val sizeBytes: Long, val uploadedAt: Long)

@Serializable
enum class ErrorCode {
    BAD_REQUEST, OTP_INVALID, OTP_EXPIRED, UNAUTHORIZED, DEVICE_REVOKED, PLAN_LAPSED, NOT_FOUND,
    TABLET_EXISTS, TOO_LARGE, BACKUP_INVALID, UPGRADE_APP, RATE_LIMITED, INTERNAL,
}

@Serializable
data class ErrorBody(val code: ErrorCode, val message: String)
