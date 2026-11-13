package com.archsolutions.cloud.contract.shop

import com.archsolutions.cloud.contract.ArchJson
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncExamplesTest {
    private val classes = listOf(
        SyncBatch::class, SyncReply::class, CategoryRow::class, ProductRow::class, CustomerRow::class,
        BusinessDayRow::class, SaleRow::class, SaleItemRow::class, MoneyEntryRow::class, PurchaseRow::class,
    )
    private val byFile = mapOf(
        "SyncBatch" to classes[0], "SyncReply" to classes[1], "category" to classes[2], "product" to classes[3],
        "customer" to classes[4], "business_day" to classes[5], "sale" to classes[6], "sale_item" to classes[7],
        "money_entry" to classes[8], "purchase" to classes[9],
    )

    @Test fun `every sync example round trips`() {
        val dir = File(javaClass.getResource("/examples/sync")!!.toURI())
        val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertEquals(byFile.keys.sorted(), files.map { it.nameWithoutExtension }, "one example per class")
        for (f in files) {
            val serializer = serializer(byFile.getValue(f.nameWithoutExtension).java)
            val text = f.readText()
            val decoded = ArchJson.decodeFromString(serializer, text)
            val again = ArchJson.encodeToString(serializer, decoded)
            assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(again), f.name)
        }
    }

    @Test fun `table names are parent first`() {
        assertEquals(
            listOf("category", "product", "customer", "business_day", "sale", "sale_item", "money_entry", "purchase"),
            SyncTables.ALL,
        )
    }

    @Test fun `fill flags default to false`() {
        val json = """{"batchId":"b","schemaVersion":2,"fillId":"f","fromSeq":1,"toSeq":1,"shopSettings":{"allowedDifference":0},"rows":[]}"""
        val batch = ArchJson.decodeFromString<SyncBatch>(json)
        assertFalse(batch.fillStart)
        assertFalse(batch.fillEnd)
    }

    @Test fun `a row with data that is not an object still decodes`() {
        val row = ArchJson.decodeFromString<SyncRow>("""{"table":"sale","op":"UPSERT","uid":"s","data":[1]}""")
        assertEquals(JsonArray(listOf(JsonPrimitive(1))), row.data)
    }

    @Test fun `unknown fields are ignored`() {
        val row = ArchJson.decodeFromString<CategoryRow>("""{"name":"Drinks","sortOrder":1,"extra":true}""")
        assertEquals(CategoryRow("Drinks", 1), row)
        assertTrue(SYNC_SCHEMA_VERSION == 2 && MAX_BATCH_ROWS == 500 && MAX_BATCH_BYTES == 1_048_576)
    }
}
