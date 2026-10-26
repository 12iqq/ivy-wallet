package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration136to137_CapturedNote : Migration(136, 137) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // a note typed in from the notification, for the transaction's description
        db.execSQL("ALTER TABLE captured_transactions ADD COLUMN note TEXT")
    }
}
