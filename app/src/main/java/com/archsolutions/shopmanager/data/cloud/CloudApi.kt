package com.archsolutions.shopmanager.data.cloud

import com.archsolutions.cloud.contract.ArchHeaders
import com.archsolutions.cloud.contract.ArchJson
import com.archsolutions.cloud.contract.BackupCreated
import com.archsolutions.cloud.contract.BackupDto
import com.archsolutions.cloud.contract.CONTRACT_VERSION
import com.archsolutions.cloud.contract.CodeRequest
import com.archsolutions.cloud.contract.ErrorBody
import com.archsolutions.cloud.contract.ErrorCode
import com.archsolutions.cloud.contract.LinkRequest
import com.archsolutions.cloud.contract.LinkResponse
import com.archsolutions.cloud.contract.PaymentDto
import com.archsolutions.cloud.contract.PaymentRequest
import com.archsolutions.cloud.contract.PlansResponse
import com.archsolutions.cloud.contract.Purpose
import com.archsolutions.cloud.contract.ShopStatus
import com.archsolutions.cloud.contract.VerifyRequest
import com.archsolutions.cloud.contract.VerifyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The server answered with an error. Network failures are not wrapped: they stay [IOException]. */
class CloudException(
    val code: ErrorCode?,
    val http: Int,
    val retryAfterSeconds: Int?,
    message: String,
) : Exception(message)

/** HTTP client for Arch-SME Cloud. [token] gives the device token, or null before the tablet is linked. */
class CloudApi(
    private val baseUrl: HttpUrl,
    client: OkHttpClient,
    private val appVersion: String,
    private val token: suspend () -> String?,
) {
    private val client = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun requestCode(phone: String) {
        post("v1/auth/code", ArchJson.encodeToString(CodeRequest(phone))).close()
    }

    suspend fun verify(phone: String, code: String): VerifyResponse =
        post("v1/auth/verify", ArchJson.encodeToString(VerifyRequest(phone, code, Purpose.TABLET))).decode()

    suspend fun link(req: LinkRequest): LinkResponse = post("v1/devices/link", ArchJson.encodeToString(req)).decode()

    suspend fun unlink() {
        post("v1/devices/unlink", "{}").close()
    }

    suspend fun status(): ShopStatus = get("v1/shop/status").decode()

    suspend fun plans(): PlansResponse = get("v1/plans").decode()

    suspend fun pay(req: PaymentRequest): PaymentDto = post("v1/payments", ArchJson.encodeToString(req)).decode()

    suspend fun payment(id: String): PaymentDto = get("v1/payments/$id").decode()

    suspend fun uploadBackup(zip: File, sha256: String): BackupCreated {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", zip.name, zip.asRequestBody("application/zip".toMediaType()))
            .build()
        return call(
            Request.Builder().url(url("v1/backups")).post(body).header(ArchHeaders.CONTENT_SHA256, sha256),
        ).decode()
    }

    suspend fun listBackups(): List<BackupDto> = get("v1/backups").decode(ListSerializer(BackupDto.serializer()))

    suspend fun downloadBackup(id: String, dest: File) {
        call(Request.Builder().url(url("v1/backups/$id/file")).get()).use { response ->
            withContext(Dispatchers.IO) {
                dest.outputStream().use { out -> response.body!!.byteStream().use { it.copyTo(out) } }
            }
        }
    }

    private fun url(path: String) = baseUrl.newBuilder().addPathSegments(path).build()

    private suspend fun get(path: String): Response = call(Request.Builder().url(url(path)).get())

    private suspend fun post(path: String, json: String): Response {
        val body: RequestBody = json.toRequestBody("application/json".toMediaType())
        return call(Request.Builder().url(url(path)).post(body))
    }

    /** Sends the request on IO with the standard headers; returns only a 2xx response, which the caller must close. */
    private suspend fun call(builder: Request.Builder): Response {
        val bearer = token()
        builder.header(ArchHeaders.CONTRACT, CONTRACT_VERSION.toString())
            .header(ArchHeaders.APP_VERSION, appVersion)
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        val request = builder.build()
        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response else response.use { throw it.toException() }
        }
    }

    private fun Response.toException(): CloudException {
        val text = try { body!!.string() } catch (e: IOException) { "" }
        val parsed = runCatching { ArchJson.decodeFromString<ErrorBody>(text) }.getOrNull()
        val retryAfter = header("Retry-After")?.trim()?.toIntOrNull()
        return CloudException(parsed?.code, code, retryAfter, parsed?.message ?: "HTTP $code")
    }

    private suspend inline fun <reified T> Response.decode(): T = use {
        withContext(Dispatchers.IO) { ArchJson.decodeFromString<T>(it.body!!.string()) }
    }

    private suspend fun <T> Response.decode(serializer: kotlinx.serialization.KSerializer<T>): T = use {
        withContext(Dispatchers.IO) { ArchJson.decodeFromString(serializer, it.body!!.string()) }
    }
}
