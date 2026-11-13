package com.archsolutions.cloud.contract

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExamplesTest {
    private val classes = listOf(
        CodeRequest::class, VerifyRequest::class, VerifyResponse::class, LinkRequest::class,
        LinkResponse::class, ShopStatus::class, PlanDto::class, PlansResponse::class,
        PaymentRequest::class, PaymentDto::class, BackupCreated::class, BackupDto::class,
        ErrorBody::class,
    ).associateBy { it.simpleName!! }

    @Test fun `every example round trips`() {
        val dir = File(javaClass.getResource("/examples")!!.toURI())
        val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertEquals(classes.keys.sorted(), files.map { it.nameWithoutExtension }, "one example per class")
        for (f in files) {
            val serializer = serializer(classes.getValue(f.nameWithoutExtension).java)
            val text = f.readText()
            val decoded = ArchJson.decodeFromString(serializer, text)
            val again = ArchJson.encodeToString(serializer, decoded)
            assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(again), f.name)
            assertTrue(again.isNotEmpty())
        }
    }

    @Test fun `verify request uses the key for`() {
        val json = ArchJson.encodeToString(VerifyRequest("+256772123456", "123456", Purpose.TABLET))
        assertEquals("""{"phone":"+256772123456","code":"123456","for":"tablet"}""", json)
    }

    @Test fun `unknown fields are ignored`() {
        val s = ArchJson.decodeFromString<ShopStatus>("""{"plan":"backup_month","state":"ACTIVE","features":["backup"],"extra":1}""")
        assertEquals(PlanState.ACTIVE, s.state)
    }
}
