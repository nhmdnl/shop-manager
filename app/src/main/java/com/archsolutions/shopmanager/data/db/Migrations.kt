package com.archsolutions.shopmanager.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** A random (v4) UUID per row; SQLite evaluates `randomblob` and `random` once per row. `random() & 3`, because `abs(random())` overflows on -2^63. */
private const val UUID_SQL =
    "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || " +
        "substr('89ab', 1 + (random() & 3), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"

/** Frozen: the 8 synced tables as of schema v2. Not the contract's live list, so a later contract change can't alter this migration. */
private val V2_TABLES = listOf("category", "product", "customer", "business_day", "sale", "sale_item", "money_entry", "purchase")

/** Frozen copy of the v2 trigger SQL (the live one is [SyncTriggers.create]); a snapshot must not change with later edits. */
private fun createV2Triggers(db: SupportSQLiteDatabase) {
    val enabled = "WHEN (SELECT enabled FROM sync_state WHERE id = 1) = 1"
    for (t in V2_TABLES) {
        db.execSQL("CREATE TRIGGER sync_${t}_ai AFTER INSERT ON $t $enabled BEGIN INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', NEW.uid, 'UPSERT'); END")
        db.execSQL(
            "CREATE TRIGGER sync_${t}_au AFTER UPDATE ON $t $enabled BEGIN " +
                "INSERT INTO sync_outbox (tbl, uid, op) SELECT '$t', OLD.uid, 'DELETE' WHERE OLD.uid <> NEW.uid; " +
                "INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', NEW.uid, 'UPSERT'); END",
        )
        db.execSQL("CREATE TRIGGER sync_${t}_ad AFTER DELETE ON $t $enabled BEGIN INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', OLD.uid, 'DELETE'); END")
    }
}

/** Exposed so a test can fail loudly if the contract's table list drifts from the frozen one. */
internal fun v2TablesForTest(): List<String> = V2_TABLES

/**
 * v2: a uid on the 8 synced tables, the sync tables and the sync triggers. The triggers come last, so the uid
 * UPDATEs never meet them. `DEFAULT ''` only exists to add the column; the entities declare no default, and Room
 * checks a column's default only when the entity declares one.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (t in V2_TABLES) {
            db.execSQL("ALTER TABLE `$t` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `$t` SET `uid` = $UUID_SQL")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${t}_uid` ON `$t` (`uid`)")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `fillId` TEXT, `fillStartSeq` INTEGER, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tbl` TEXT NOT NULL, `uid` TEXT NOT NULL, `op` TEXT NOT NULL)")
        db.execSQL("INSERT INTO sync_state (id, enabled, fillId, fillStartSeq) VALUES (1, 0, NULL, NULL)")
        createV2Triggers(db)
    }
}
