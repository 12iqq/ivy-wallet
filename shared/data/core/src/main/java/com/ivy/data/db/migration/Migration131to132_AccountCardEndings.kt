package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration131to132_AccountCardEndings : Migration(131, 132) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // last 4 digits of the account's cards / account numbers, for matching bank SMS
        db.execSQL("ALTER TABLE accounts ADD COLUMN cardEndings TEXT")
    }
}
