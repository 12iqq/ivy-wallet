package com.ivy.autocapture.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.ivy.autocapture.data.CaptureProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Reads SMS already in the inbox, e.g. to catch up on the last 30 days. */
class SmsInboxScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: CaptureProcessor,
) {
    fun hasPermission(): Boolean = listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** @return number of new transactions found */
    suspend fun scan(days: Long = 30): Int = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext 0
        val since = Instant.now().minus(Duration.ofDays(days)).toEpochMilli()
        var found = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val captured = processor.onMessage(
                        sender = cursor.getString(addressIndex).orEmpty(),
                        body = cursor.getString(bodyIndex).orEmpty(),
                        receivedAt = Instant.ofEpochMilli(cursor.getLong(dateIndex)),
                        notify = false,
                    )
                    if (captured != null) found++
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "No SMS permission")
        }
        found
    }
}
