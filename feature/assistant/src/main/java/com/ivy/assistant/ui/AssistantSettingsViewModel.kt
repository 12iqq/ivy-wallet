package com.ivy.assistant.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.assistant.data.AssistantSettings
import com.ivy.assistant.data.AssistantSettingsStore
import com.ivy.assistant.data.BackupFrequency
import com.ivy.assistant.data.DailyAssistant
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class AssistantSettingsState(
    val settings: AssistantSettings,
    val backingUp: Boolean,
)

sealed interface AssistantSettingsEvent {
    data class SetCardReminders(val enabled: Boolean) : AssistantSettingsEvent
    data class SetReminderDaysBefore(val days: Int) : AssistantSettingsEvent
    data class SetBudgetAlerts(val enabled: Boolean) : AssistantSettingsEvent
    data class SetBackupFrequency(val frequency: BackupFrequency) : AssistantSettingsEvent

    /** The user picked (or created) the backup file with the system file picker. */
    data class BackupFileChosen(val uri: Uri, val fileName: String?) : AssistantSettingsEvent
    data object BackupNow : AssistantSettingsEvent
}

@Stable
@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AssistantSettingsStore,
    private val dailyAssistant: DailyAssistant,
) : ComposeViewModel<AssistantSettingsState, AssistantSettingsEvent>() {

    private var backingUp by mutableStateOf(false)

    @Composable
    override fun uiState(): AssistantSettingsState {
        val settings by store.settings.collectAsState()
        return AssistantSettingsState(settings = settings, backingUp = backingUp)
    }

    override fun onEvent(event: AssistantSettingsEvent) {
        when (event) {
            is AssistantSettingsEvent.SetCardReminders -> store.update { it.copy(cardReminders = event.enabled) }
            is AssistantSettingsEvent.SetReminderDaysBefore -> store.update {
                it.copy(reminderDaysBefore = event.days.coerceIn(MIN_DAYS_BEFORE, MAX_DAYS_BEFORE))
            }

            is AssistantSettingsEvent.SetBudgetAlerts -> store.update { it.copy(budgetAlerts = event.enabled) }
            is AssistantSettingsEvent.SetBackupFrequency -> store.update {
                it.copy(backupFrequency = event.frequency)
            }

            is AssistantSettingsEvent.BackupFileChosen -> {
                keepAccess(event.uri)
                store.update {
                    it.copy(
                        backupUri = event.uri.toString(),
                        backupFileName = event.fileName,
                        lastBackupError = null,
                        // pick a frequency for the user the first time
                        backupFrequency = if (it.backupFrequency == BackupFrequency.Off) {
                            BackupFrequency.Weekly
                        } else {
                            it.backupFrequency
                        }
                    )
                }
                backupNow()
            }

            AssistantSettingsEvent.BackupNow -> backupNow()
        }
    }

    private fun backupNow() {
        if (backingUp) return
        viewModelScope.launch {
            backingUp = true
            dailyAssistant.backupNow()
            backingUp = false
        }
    }

    /** Without this the app loses access to the file after a restart. */
    private fun keepAccess(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Timber.w(e, "Backup location doesn't support persistent access")
        }
    }

    companion object {
        const val MIN_DAYS_BEFORE = 2
        const val MAX_DAYS_BEFORE = 10
    }
}
