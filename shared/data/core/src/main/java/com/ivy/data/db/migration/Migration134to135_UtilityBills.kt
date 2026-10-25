package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration134to135_UtilityBills : Migration(134, 135) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // utility bills (SEWA) read from SMS, for the utility report
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `utility_bills` (" +
                "`id` TEXT NOT NULL, `provider` TEXT NOT NULL, `account` TEXT NOT NULL, " +
                "`periodStart` TEXT NOT NULL, `periodEnd` TEXT NOT NULL, " +
                "`electricity` REAL NOT NULL, `water` REAL NOT NULL, `gas` REAL NOT NULL, " +
                "`municipality` REAL NOT NULL, `vat` REAL NOT NULL, `others` REAL NOT NULL, " +
                "`arrears` REAL NOT NULL, `total` REAL NOT NULL, `dueDate` TEXT, " +
                "`currency` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}
