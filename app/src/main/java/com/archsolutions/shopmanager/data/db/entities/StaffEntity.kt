package com.archsolutions.shopmanager.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** A person who signs in with a PIN. Never deleted, only deactivated. Not synced. */
@Entity(tableName = "staff", indices = [Index(value = ["uid"], unique = true)])
data class StaffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String,
    val pinHash: String,
    val active: Boolean = true,
    val createdAt: Long,
)
