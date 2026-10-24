package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration133to134_CapturedTransfers : Migration(133, 134) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // a card payment or top-up seen by both accounts becomes one transfer
        db.execSQL("ALTER TABLE captured_transactions ADD COLUMN toAccountId TEXT")
        db.execSQL("ALTER TABLE captured_transactions ADD COLUMN isTransfer INTEGER NOT NULL DEFAULT 0")
    }
}
