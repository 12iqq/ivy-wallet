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

    @Query("SELECT COUNT(*) FROM captured_transactions WHERE fingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int

    @Query("UPDATE captured_transactions SET status = :status, transactionId = :transactionId WHERE id = :id")
    suspend fun updateStatus(id: UUID, status: String, transactionId: UUID?)

    /** Same purchase reported twice (e.g. bank SMS + Google Wallet notification). */
    @Query(
        "SELECT COUNT(*) FROM captured_transactions " +
            "WHERE source = :source AND amount = :amount AND receivedAt BETWEEN :from AND :to"
    )
    suspend fun countFromSourceBetween(source: String, amount: Double, from: Instant, to: Instant): Int

    /** Housekeeping: forget handled items after a while */
    @Query("DELETE FROM captured_transactions WHERE status != 'PENDING' AND receivedAt < :before")
    suspend fun deleteHandledBefore(before: Instant)
}
