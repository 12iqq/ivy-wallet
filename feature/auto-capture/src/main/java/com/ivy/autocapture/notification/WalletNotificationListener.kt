package com.ivy.autocapture.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ivy.autocapture.data.AutoCaptureSettingsStore
import com.ivy.autocapture.data.CaptureProcessor
import com.ivy.autocapture.parser.GoogleWalletPackage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Android calls this only when a notification is posted - nothing runs in the
 * background otherwise. Everything except Google Wallet is ignored immediately.
 */
@AndroidEntryPoint
class WalletNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var processor: CaptureProcessor

    @Inject
    lateinit var settingsStore: AutoCaptureSettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("ReturnCount")
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != GoogleWalletPackage) return
        if (!settingsStore.current().readWalletNotifications) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val postedAt = Instant.ofEpochMilli(sbn.postTime)

        scope.launch {
            try {
                processor.onWalletNotification(title = title, text = text, postedAt = postedAt)
            } catch (e: Exception) {
                Timber.e(e, "Failed to process Google Wallet notification")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
