package com.ivy.autocapture.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.ivy.autocapture.data.CaptureProcessor
import com.ivy.autocapture.parser.UaeBanks
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.CapturedTransactionEntity
import com.ivy.domain.AppStarter
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotification
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * Auto-add's notifications. A transaction gets a notification of its own with an "Add note"
 * reply, for what the bank's text can't say (the film, what was ordered).
 */
class CaptureNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
    private val sharedPrefs: SharedPrefs,
) {
    /** A transaction waiting for review. */
    fun waiting(captured: CapturedTransactionEntity) {
        if (!enabled()) return
        val notification = base(
            title = context.getString(R.string.new_bank_transaction),
            text = context.getString(
                R.string.new_bank_transaction_text,
                captured.currency,
                captured.amount.format(captured.currency),
                place(captured)
            ),
            openReview = true,
        )
        if (captured.type != TransactionType.TRANSFER) notification.addAction(noteAction(captured.id))
        notificationService.showNotification(notification, notificationId(captured.id))
    }

    /** A transaction added right away ("Add without review"), named [title]. */
    fun added(captured: CapturedTransactionEntity, title: String?) {
        if (!enabled()) return
        val notification = base(
            title = context.getString(R.string.transaction_added, title ?: place(captured)),
            text = "${captured.currency} ${captured.amount.format(captured.currency)}",
            openReview = false,
        ).addAction(noteAction(captured.id))
        notificationService.showNotification(notification, notificationId(captured.id))
    }

    /** Several found at once by a scheduled check. */
    fun waitingCount(count: Int) {
        if (!enabled()) return
        val notification = base(
            title = context.getString(R.string.new_bank_transactions, count),
            text = context.getString(R.string.new_bank_transactions_text),
            openReview = true,
        )
        notificationService.showNotification(notification, SUMMARY_NOTIFICATION_ID)
    }

    /** The reply was saved; Android expects the notification to change after a reply. */
    fun noteSaved(captured: CapturedTransactionEntity, note: String) {
        val notification = base(
            title = context.getString(R.string.note_saved),
            text = note,
            openReview = captured.status == CapturedTransactionEntity.STATUS_PENDING,
        ).setPriority(NotificationCompat.PRIORITY_LOW).setOnlyAlertOnce(true)
        notificationService.showNotification(notification, notificationId(captured.id))
    }

    /** Added or skipped in the app: its notification has nothing left to do. */
    fun dismiss(capturedId: UUID) {
        notificationService.dismissNotification(notificationId(capturedId))
    }

    // the user can turn these off in Settings -> Notifications
    private fun enabled(): Boolean = sharedPrefs.getBoolean(SharedPrefs.NOTIFY_DETECTED_TRANSACTIONS, true)

    private fun place(captured: CapturedTransactionEntity): String =
        captured.merchant ?: UaeBanks.byId(captured.bankId)?.displayName.orEmpty()

    private fun base(title: String, text: String, openReview: Boolean): IvyNotification {
        val open = appStarter.getRootIntent()
            .apply { if (openReview) putExtra(CaptureProcessor.EXTRA_OPEN_REVIEW, true) }
        return notificationService
            .defaultIvyNotification(channel = IvyNotificationChannel.AUTO_CAPTURE)
            .apply {
                setContentTitle(title)
                setContentText(text)
                setPriority(NotificationCompat.PRIORITY_DEFAULT)
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        if (openReview) REVIEW_REQUEST_CODE else OPEN_REQUEST_CODE,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
    }

    private fun noteAction(capturedId: UUID): NotificationCompat.Action {
        val reply = Intent(context, NoteReplyReceiver::class.java)
            .putExtra(NoteReplyReceiver.EXTRA_CAPTURED_ID, capturedId.toString())
        // the typed reply is added to the intent, so it can't be immutable
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(capturedId),
            reply,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val input = RemoteInput.Builder(NoteReplyReceiver.KEY_NOTE)
            .setLabel(context.getString(R.string.note_hint))
            .build()
        return NotificationCompat.Action
            .Builder(R.drawable.ic_notification, context.getString(R.string.add_note), pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun notificationId(capturedId: UUID): Int =
        ITEM_NOTIFICATION_BASE + (capturedId.hashCode() and ITEM_ID_MASK)

    private companion object {
        const val SUMMARY_NOTIFICATION_ID = 4242
        const val REVIEW_REQUEST_CODE = 4243
        const val OPEN_REQUEST_CODE = 4244
        const val ITEM_NOTIFICATION_BASE = 0x40000
        const val ITEM_ID_MASK = 0xFFFF
    }
}
