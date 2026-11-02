package com.archsolutions.shopmanager.data.cloud.sync

import java.io.File

/**
 * `files/sync_fill_needed`: a restore swapped the database, so the cloud's copy is stale and the next
 * sync run must start a new fill. A marker without a restore only costs one extra fill.
 */
class FillMarker(filesDir: File) {
    private val file = File(filesDir, NAME)

    fun set() {
        file.parentFile?.mkdirs()
        file.writeText("1")
    }

    fun isSet(): Boolean = file.exists()

    fun clear() {
        file.delete()
    }

    private companion object {
        const val NAME = "sync_fill_needed"
    }
}
