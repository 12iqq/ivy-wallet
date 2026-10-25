package com.ivy.domain.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.db.dao.write.UtilityBillDao
import com.ivy.data.db.entity.UtilityBillEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Reads the utility's texts (SEWA) and keeps the bills, so they outlive the texts. Bills and
 * payment confirmations also drive the bills' planned payments, see [UtilityBillPlanner].
 */
class UtilityBillReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: UtilityBillDao,
    private val planner: UtilityBillPlanner,
    private val sharedPrefs: SharedPrefs,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun tracking(): Boolean = sharedPrefs.getBoolean(SharedPrefs.TRACK_UTILITY_BILLS, false)

    /**
     * One text as it arrives (auto-add's SMS checks hand every text here first).
     * @return true when it's a utility text, which is never a bank transaction
     */
    suspend fun onMessage(sender: String, body: String, receivedAt: Instant): Boolean {
        val bill = UtilityBillParser.parse(sender, body)
        val payment = if (bill == null) UtilityBillParser.parsePayment(sender, body) else null
        if (tracking()) {
            bill?.let {
                save(listOf(it))
                planner.planDueBills()
            }
            payment?.let { planner.onPaymentReceived(it, receivedAt) }
        }
        return bill != null || payment != null
    }

    /**
     * Reads every utility text, old ones too: there are a couple a month, so this is cheap.
     * Reading a text again updates its bill instead of adding a copy.
     * @return number of bills found
     */
    suspend fun readInbox(): Int = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext 0
        val bills = mutableListOf<UtilityBillEntity>()
        val payments = mutableListOf<Pair<UtilityPayment, Instant>>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                UtilityBillParser.senders.joinToString(" OR ") { "${Telephony.Sms.ADDRESS} LIKE ?" },
                UtilityBillParser.senders.map { "%$it%" }.toTypedArray(),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressIndex).orEmpty()
                    val body = cursor.getString(bodyIndex).orEmpty()
                    UtilityBillParser.parse(sender, body)?.let(bills::add)
                    UtilityBillParser.parsePayment(sender, body)?.let {
                        payments += it to Instant.ofEpochMilli(cursor.getLong(dateIndex))
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "No SMS permission")
        }
        save(bills)
        if (tracking()) {
            planner.planDueBills()
            // a payment only settles a bill billed before it, so older ones change nothing
            payments.forEach { (payment, at) -> planner.onPaymentReceived(payment, at) }
        }
        bills.size
    }

    /** Saves bills read again without forgetting that they already got their planned payment. */
    private suspend fun save(bills: List<UtilityBillEntity>) {
        if (bills.isEmpty()) return
        dao.saveAll(bills.map { bill -> bill.copy(planned = dao.findById(bill.id)?.planned == true) })
    }
}
