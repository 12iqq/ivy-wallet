package com.ivy.insights.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.ivy.data.db.dao.write.UtilityBillDao
import com.ivy.data.db.entity.UtilityBillEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** Reads the utility bill texts in the SMS inbox and keeps the bills, so they outlive the texts. */
class UtilityBillReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: UtilityBillDao,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Reads every bill text, old ones too: there's one a month, so this is cheap. Reading the
     * same text again updates its bill instead of adding a copy.
     * @return number of bills found
     */
    suspend fun readInbox(): Int = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext 0
        val bills = mutableListOf<UtilityBillEntity>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                UtilityBillParser.senders.joinToString(" OR ") { "${Telephony.Sms.ADDRESS} LIKE ?" },
                UtilityBillParser.senders.map { "%$it%" }.toTypedArray(),
                null
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                while (cursor.moveToNext()) {
                    UtilityBillParser.parse(
                        sender = cursor.getString(addressIndex).orEmpty(),
                        body = cursor.getString(bodyIndex).orEmpty(),
                    )?.let(bills::add)
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "No SMS permission")
        }
        if (bills.isNotEmpty()) dao.saveAll(bills)
        bills.size
    }
}
