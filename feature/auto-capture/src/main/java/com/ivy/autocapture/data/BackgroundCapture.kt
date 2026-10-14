package com.ivy.autocapture.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import com.ivy.autocapture.notification.WalletNotificationListener
import com.ivy.autocapture.sms.BankSmsReceiver
import com.ivy.autocapture.sms.SmsCheckWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides what of auto-add may run in the background. A switched-off receiver or
 * listener is never started by Android, so it costs no battery at all:
 * - the SMS receiver exists only while new SMS are picked up as they arrive,
 * - otherwise a periodic job reads the inbox every few hours,
 * - the Google Wallet listener exists only while Wallet capture is in use (Android
 *   keeps a notification listener connected and calls it for every notification).
 */
@Singleton
class BackgroundCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AutoCaptureSettingsStore,
) {
    fun checksSmsOnSchedule(): Boolean = CaptureLogic.checksSmsOnSchedule(store.current())

    /** Call on app start and whenever the auto-add settings change. */
    fun apply() {
        val config = store.current()
        setEnabled(ComponentName(context, BankSmsReceiver::class.java), CaptureLogic.receivesSms(config))

        val wallet = ComponentName(context, WalletNotificationListener::class.java)
        val listensToWallet = CaptureLogic.listensToWallet(config)
        if (setEnabled(wallet, listensToWallet) && listensToWallet) {
            // reconnect right away instead of at the next reboot
            runCatching { NotificationListenerService.requestRebind(wallet) }
                .onFailure { Timber.w(it, "Couldn't reconnect the Wallet listener") }
        }

        if (CaptureLogic.checksSmsOnSchedule(config)) {
            SmsCheckWorker.schedule(context, config.smsCheck)
        } else {
            SmsCheckWorker.cancel(context)
        }
    }

    /** @return true when the state changed */
    private fun setEnabled(component: ComponentName, enabled: Boolean): Boolean {
        val packageManager = context.packageManager
        // "default" means as declared in the manifest, which is enabled
        val isEnabled = packageManager.getComponentEnabledSetting(component) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (isEnabled == enabled) return false
        packageManager.setComponentEnabledSetting(
            component,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP
        )
        return true
    }
}
