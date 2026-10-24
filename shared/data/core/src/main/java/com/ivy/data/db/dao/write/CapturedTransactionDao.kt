package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.CapturedTransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface CapturedTransactionDao {
    @Upsert
    suspend fun save(value: CapturedTransactionEntity)

    @Query("SELECT * FROM captured_transactions WHERE status = 'PENDING' ORDER BY receivedAt DESC")
    fun pendingFlow(): Flow<List<CapturedTransactionEntity>>

    @Query("SELECT * FROM captured_transactions WHERE status = 'PENDING' ORDER BY receivedAt DESC")
    suspend fun findPending(): List<CapturedTransactionEntity>

    @Query("SELECT COUNT(*) FROM captured_transactions WHERE status = 'PENDING'")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM captured_transactions WHERE id = :id")
    suspend fun findById(id: UUID): CapturedTransactionEntity?

    @Query("SELECT * FROM captured_transactions WHERE transactionId IS NOT NULL")
    suspend fun findAdded(): List<CapturedTransactionEntity>

    @Query("SELECT * FROM captured_transactions WHERE receivedAt BETWEEN :from AND :to")
    suspend fun findReceivedBetween(from: Instant, to: Instant): List<CapturedTransactionEntity>

    @Query("SELECT COUNT(*) FROM captured_transactions WHERE fingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int

    /** The same message seen again close to the first time (re-delivered SMS, inbox re-scan). */
    @Query(
        "SELECT COUNT(*) FROM captured_transactions " +
            "WHERE fingerprint = :fingerprint AND receivedAt BETWEEN :from AND :to"
    )
    suspend fun countByFingerprintBetween(fingerprint: String, from: Instant, to: Instant): Int

    /**
     * Atomically moves a pending item to [status].
     * @return 1 if this caller claimed it, 0 if it was already handled (e.g. a double tap).
     */
    @Query("UPDATE captured_transactions SET status = :status WHERE id = :id AND status = 'PENDING'")
    suspend fun claimPending(id: UUID, status: String): Int

    @Query("UPDATE captured_transactions SET status = :status, transactionId = :transactionId WHERE id = :id")
    suspend fun updateStatus(id: UUID, status: String, transactionId: UUID?)

    /** Same purchase reported twice (e.g. bank SMS + Google Wallet notification). */
    @Query(
        "SELECT COUNT(*) FROM captured_transactions " +
            "WHERE source = :source AND amount = :amount AND currency = :currency " +
            "AND (:last4 IS NULL OR last4 IS NULL OR last4 = :last4) " +
            "AND receivedAt BETWEEN :from AND :to"
    )
    @Suppress("LongParameterList")
    suspend fun countFromSourceBetween(
        source: String,
        amount: Double,
        currency: String,
        last4: String?,
        from: Instant,
        to: Instant,
    ): Int

    /** Housekeeping: forget handled items after a while */
    @Query("DELETE FROM captured_transactions WHERE status != 'PENDING' AND receivedAt < :before")
    suspend fun deleteHandledBefore(before: Instant)
}
