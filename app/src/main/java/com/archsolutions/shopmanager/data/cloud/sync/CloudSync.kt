package com.archsolutions.shopmanager.data.cloud.sync

import com.archsolutions.shopmanager.data.cloud.CloudStore
import com.archsolutions.shopmanager.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** What the Owner view upload is doing, as the Cloud card sees it. */
data class SyncStatus(val hasOutbox: Boolean, val fillPending: Boolean, val lastSyncAt: Long?)

interface CloudSync {
    val status: Flow<SyncStatus>
}

/** Combines the outbox flows with the last finished run. */
class RoomCloudSync @Inject constructor(
    private val db: AppDatabase,
    private val store: CloudStore,
) : CloudSync {
    override val status: Flow<SyncStatus> = combine(
        db.syncDao().hasOutbox(),
        db.syncDao().fillPending(),
        store.lastSyncAt,
    ) { outbox, fill, last -> SyncStatus(outbox, fill, last) }
}
