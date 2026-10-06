package com.ivy.autocapture.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ivy.autocapture.data.AutoCaptureSettingsStore
import com.ivy.autocapture.data.CaptureProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/** Receives incoming SMS and hands bank alerts to [CaptureProcessor]. */
@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var processor: CaptureProcessor

    @Inject
    lateinit var settingsStore: AutoCaptureSettingsStore

    @Suppress("ReturnCount")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!settingsStore.current().mode.readsSms) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // long messages arrive in several parts - join them per sender
        val bySender = messages
            .groupBy { it.originatingAddress.orEmpty() }
            .mapValues { (_, parts) ->
                parts.joinToString(separator = "") { it.messageBody.orEmpty() } to
                    (parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis())
            }

        val pending = goAsync()
        scope.launch {
            try {
                bySender.forEach { (sender, value) ->
                    val (body, timestamp) = value
                    processor.onMessage(
                        sender = sender,
                        body = body,
                        receivedAt = Instant.ofEpochMilli(timestamp)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to process SMS")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
