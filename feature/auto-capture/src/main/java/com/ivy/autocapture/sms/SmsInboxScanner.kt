package com.ivy.autocapture.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.ivy.autocapture.data.AutoCaptureSettingsStore
import com.ivy.autocapture.data.CaptureProcessor
import com.ivy.data.db.entity.CapturedTransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Reads SMS already in the inbox: to catch up on the last 30 days, or for scheduled checks. */
class SmsInboxScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: CaptureProcessor,
    private val store: AutoCaptureSettingsStore,
) {
    fun hasPermission(): Boolean = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** @return number of new transactions found */
    suspend fun scan(days: Long = 30): Int = scanSince(Instant.now().minus(Duration.ofDays(days))).size

    /**
     * Picks up bank SMS that arrived since the last check (scheduled checks and opening
     * the app) and shows one notification for the ones waiting for review.
     * @return number of new transactions found
     */
    suspend fun checkNew(): Int {
        if (!hasPermission()) return 0
        val now = Instant.now()
        // a little overlap: messages already captured are recognized and skipped
        val since = store.current().lastSmsCheckMillis
            ?.let { Instant.ofEpochMilli(it).minus(CheckOverlap) }
            ?: now.minus(FirstCheck)
        val found = scanSince(since)
        store.update { it.copy(lastSmsCheckMillis = now.toEpochMilli()) }
        processor.notifyFound(found)
        return found.size
    }

    private suspend fun scanSince(since: Instant): List<CapturedTransactionEntity> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val found = mutableListOf<CapturedTransactionEntity>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toEpochMilli().toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    processor.onMessage(
                        sender = cursor.getString(addressIndex).orEmpty(),
                        body = cursor.getString(bodyIndex).orEmpty(),
                        receivedAt = Instant.ofEpochMilli(cursor.getLong(dateIndex)),
                        notify = false,
                    )?.let(found::add)
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "No SMS permission")
        }
        found
    }

    private companion object {
        val CheckOverlap: Duration = Duration.ofMinutes(10)
        val FirstCheck: Duration = Duration.ofDays(1)
    }
}
