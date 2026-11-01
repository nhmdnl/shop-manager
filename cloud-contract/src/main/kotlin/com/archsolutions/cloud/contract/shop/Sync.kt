package com.archsolutions.cloud.contract.shop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val SYNC_SCHEMA_VERSION = 2
const val MAX_BATCH_ROWS = 500
const val MAX_BATCH_BYTES = 1_048_576

/**
 * One outbox entry. [op] is "UPSERT" or "DELETE"; [table] and [op] are strings and [data] is any JSON value,
 * so one bad row cannot make the whole batch undecodable (the server rejects that row alone).
 */
@Serializable
data class SyncRow(val table: String, val op: String, val uid: String, val data: JsonElement? = null)

@Serializable
data class ShopSettings(val allowedDifference: Long)

/**
 * [fillStart] is true on the batch that holds the fill's first outbox entry and on any resend of it
 * (its [fromSeq] is at or below the fill's first seq). The server clears a resync request only on a fill whose start it saw.
 */
@Serializable
data class SyncBatch(
    val batchId: String,
    val schemaVersion: Int,
    val fillId: String,
    val fillStart: Boolean = false,
    val fillEnd: Boolean = false,
    val fromSeq: Long,
    val toSeq: Long,
    val shopSettings: ShopSettings,
    val rows: List<SyncRow>,
)

@Serializable
data class RejectedRow(val table: String, val uid: String, val reason: String)

@Serializable
data class SyncReply(val ackSeq: Long, val rejected: List<RejectedRow>)
