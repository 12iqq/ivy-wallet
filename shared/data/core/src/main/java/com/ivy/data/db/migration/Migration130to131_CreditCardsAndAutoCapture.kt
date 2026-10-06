package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber", "ClassNaming")
class Migration130to131_CreditCardsAndAutoCapture : Migration(130, 131) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Credit card accounts
        db.execSQL("ALTER TABLE accounts ADD COLUMN accountType TEXT")
        db.execSQL("ALTER TABLE accounts ADD COLUMN creditLimit REAL")
        db.execSQL("ALTER TABLE accounts ADD COLUMN statementDay INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN paymentDueDay INTEGER")

        // Planned transfers (e.g. monthly credit card payment)
        db.execSQL("ALTER TABLE planned_payment_rules ADD COLUMN toAccountId TEXT")
        db.execSQL("ALTER TABLE planned_payment_rules ADD COLUMN toAmount REAL")

        // Auto-captured transactions waiting for review
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `captured_transactions` (" +
                "`id` TEXT NOT NULL, `source` TEXT NOT NULL, `sender` TEXT NOT NULL, " +
                "`rawText` TEXT NOT NULL, `bankId` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, `currency` TEXT NOT NULL, `merchant` TEXT, " +
                "`last4` TEXT, `isCreditCard` INTEGER, `receivedAt` INTEGER NOT NULL, " +
                "`accountId` TEXT, `categoryId` TEXT, `status` TEXT NOT NULL, " +
                "`transactionId` TEXT, `fingerprint` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}
