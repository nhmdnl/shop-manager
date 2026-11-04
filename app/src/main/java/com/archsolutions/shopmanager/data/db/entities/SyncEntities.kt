package com.archsolutions.shopmanager.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row (id 1). `fillStartSeq` is the `seq` of the current fill's first outbox entry, so the batch holding it carries the contract's `fillStart`. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val fillId: String? = null,
    val fillStartSeq: Long? = null,
)

/** Filled by the `sync_*` triggers while sync is enabled; drained by the sync worker. */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val tbl: String,
    val uid: String,
    val op: String,
) {
    companion object {
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
        const val OP_FILL_END = "FILL_END"
    }
}
