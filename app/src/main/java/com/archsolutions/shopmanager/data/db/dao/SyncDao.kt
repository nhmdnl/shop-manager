package com.archsolutions.shopmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.archsolutions.shopmanager.data.db.entities.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun state(): SyncStateEntity

    @Query("UPDATE sync_state SET enabled = :enabled, fillId = :fillId, fillStartSeq = NULL WHERE id = 1")
    suspend fun setState(enabled: Boolean, fillId: String?)

    /** A Boolean, not a count: the watcher only needs empty to non-empty, and a COUNT(*) over a fill's entries would run after every acknowledgement. */
    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox)")
    fun hasOutbox(): Flow<Boolean>

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()
}
