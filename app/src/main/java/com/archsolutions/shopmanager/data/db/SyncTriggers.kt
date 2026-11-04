package com.archsolutions.shopmanager.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.archsolutions.cloud.contract.shop.SyncTables
import com.archsolutions.shopmanager.data.db.entities.SyncOutboxEntity.Companion.OP_DELETE
import com.archsolutions.shopmanager.data.db.entities.SyncOutboxEntity.Companion.OP_UPSERT

/**
 * The triggers that fill `sync_outbox` while `sync_state.enabled = 1`. Three per synced table
 * (`sync_<t>_ai`, `_au`, `_ad`). An UPDATE that changes the uid writes a DELETE for the old one first.
 *
 * `CREATE TRIGGER IF NOT EXISTS` keeps an existing trigger as it is, so a changed trigger body needs
 * `DROP TRIGGER` in a new migration. `MIGRATION_1_2` has its own frozen copy of the v2 triggers.
 */
object SyncTriggers {
    val TABLES: List<String> = SyncTables.ALL

    private const val ENABLED = "WHEN (SELECT enabled FROM sync_state WHERE id = 1) = 1"

    fun create(db: SupportSQLiteDatabase) {
        for (t in TABLES) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_ai AFTER INSERT ON $t $ENABLED BEGIN " +
                    "INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', NEW.uid, '$OP_UPSERT'); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_au AFTER UPDATE ON $t $ENABLED BEGIN " +
                    "INSERT INTO sync_outbox (tbl, uid, op) SELECT '$t', OLD.uid, '$OP_DELETE' WHERE OLD.uid <> NEW.uid; " +
                    "INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', NEW.uid, '$OP_UPSERT'); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t}_ad AFTER DELETE ON $t $ENABLED BEGIN " +
                    "INSERT INTO sync_outbox (tbl, uid, op) VALUES ('$t', OLD.uid, '$OP_DELETE'); END",
            )
        }
    }

    /** The single sync_state row, then the triggers: what a new database needs. */
    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO sync_state (id, enabled, fillId, fillStartSeq) VALUES (1, 0, NULL, NULL)")
        create(db)
    }

    /** onCreate for a new database. Upgrades get the same in MIGRATION_1_2, from a frozen copy. */
    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = install(db)
    }
}
