package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.model.TransactionType
import java.time.Instant
import java.util.UUID

/**
 * A transaction detected automatically (bank SMS, notification or statement import)
 * that waits in the review inbox until the user adds or dismisses it.
 * Not part of backups: once added it becomes a normal transaction.
 */
@Suppress("DataClassDefaultValues")
@Keep
@Entity(tableName = "captured_transactions")
data class CapturedTransactionEntity(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),
    /** SMS, NOTIFICATION or STATEMENT */
    val source: String,
    val sender: String,
    val rawText: String,
    val bankId: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val last4: String?,
    val isCreditCard: Boolean?,
    val receivedAt: Instant,
    /** Suggested account (from the card/account mapping); for a TRANSFER, where the money left */
    val accountId: UUID?,
    /** Suggested category (learned from previous transactions) */
    val categoryId: UUID?,
    /** PENDING, ADDED or DISMISSED */
    val status: String,
    /** The transaction created from it, once added */
    val transactionId: UUID?,
    /** Used to drop duplicates of the same message */
    val fingerprint: String,
    /** For a TRANSFER: the account the money reached */
    val toAccountId: UUID? = null,
    /** The message reads as money moving between your own accounts (a card payment, a top-up) */
    @ColumnInfo(defaultValue = "0")
    val isTransfer: Boolean = false,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ADDED = "ADDED"

        /** Being turned into a transaction right now. */
        const val STATUS_ADDING = "ADDING"
        const val STATUS_DISMISSED = "DISMISSED"

        /** The other message about a transfer that's already captured: kept only to know it was seen. */
        const val STATUS_MERGED = "MERGED"

        const val SOURCE_SMS = "SMS"
        const val SOURCE_NOTIFICATION = "NOTIFICATION"
        const val SOURCE_STATEMENT = "STATEMENT"
    }
}
