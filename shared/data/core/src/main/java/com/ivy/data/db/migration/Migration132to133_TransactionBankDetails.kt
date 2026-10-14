package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration132to133_TransactionBankDetails : Migration(132, 133) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // the shop as the bank's SMS named it, kept apart from the user's own description
        db.execSQL("ALTER TABLE transactions ADD COLUMN bankDetails TEXT")
    }
}
