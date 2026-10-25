package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration135to136_UtilityBillPlanned : Migration(135, 136) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // whether a bill already got its planned payment
        db.execSQL("ALTER TABLE utility_bills ADD COLUMN planned INTEGER NOT NULL DEFAULT 0")
    }
}
