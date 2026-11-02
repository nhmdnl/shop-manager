package com.archsolutions.shopmanager.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** A random (v4) UUID per row; SQLite evaluates `randomblob` and `random` once per row. `random() & 3`, because `abs(random())` overflows on -2^63. */
private const val UUID_SQL =
    "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || " +
        "substr('89ab', 1 + (random() & 3), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"

/**
 * v2: a uid on the 8 synced tables, the sync tables and the sync triggers. The triggers come last, so the uid
 * UPDATEs never meet them. `DEFAULT ''` only exists to add the column; the entities declare no default, and Room
 * checks a column's default only when the entity declares one.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (t in SyncTriggers.TABLES) {
            db.execSQL("ALTER TABLE `$t` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `$t` SET `uid` = $UUID_SQL")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${t}_uid` ON `$t` (`uid`)")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `fillId` TEXT, `fillStartSeq` INTEGER, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tbl` TEXT NOT NULL, `uid` TEXT NOT NULL, `op` TEXT NOT NULL)")
        db.execSQL("INSERT INTO sync_state (id, enabled, fillId, fillStartSeq) VALUES (1, 0, NULL, NULL)")
        SyncTriggers.create(db)
    }
}
