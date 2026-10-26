package com.ivy.autocapture.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.ivy.autocapture.data.CaptureProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** The note typed in reply to an auto-add notification. */
@AndroidEntryPoint
class NoteReplyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var processor: CaptureProcessor

    override fun onReceive(context: Context, intent: Intent) {
        val capturedId = intent.getStringExtra(EXTRA_CAPTURED_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val note = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_NOTE)?.toString()?.trim()
        if (capturedId == null || note.isNullOrEmpty()) return

        val pending = goAsync()
        scope.launch {
            try {
                processor.addNote(capturedId, note)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save the note")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CAPTURED_ID = "captured_id"
        const val KEY_NOTE = "note"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
