package com.archsolutions.shopmanager.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.archsolutions.cloud.contract.shop.SyncTables

/**
 * The triggers that fill `sync_outbox` while `sync_state.enabled = 1`. Three per synced table
 * (`sync_<t>_ai`, `_au`, `_ad`). An UPDATE that changes the uid writes a DELETE for the old one first.
 */
object SyncTriggers {
    val TABLES: List<String> = SyncTables.ALL

    private const val ENABLED = "WHEN (SELECT enabled FROM sync_state WHERE id = 1) = 1"

    fun create(db: SupportSQLiteDatabase) {
        for (t in TABLES) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_ai AFTER INSERT ON $t $ENABLED BEGIN " +
                    "INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', NEW.uid, 'UPSERT'); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_au AFTER UPDATE ON $t $ENABLED BEGIN " +
                    "INSERT INTO sync_outbox (tbl, uid, op) SELECT '$t', OLD.uid, 'DELETE' WHERE OLD.uid <> NEW.uid; " +
                    "INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', NEW.uid, 'UPSERT'); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_ad AFTER DELETE ON $t $ENABLED BEGIN " +
                    "INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', OLD.uid, 'DELETE'); END",
            )
        }
    }

    /** onCreate for a new database: the single sync_state row, then the triggers. Upgrades do the same in MIGRATION_1_2. */
    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO sync_state (id, enabled, fillId, fillStartSeq) VALUES (1, 0, NULL, NULL)")
            create(db)
        }
    }
}
