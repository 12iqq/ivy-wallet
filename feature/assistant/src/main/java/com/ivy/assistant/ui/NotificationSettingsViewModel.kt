package com.ivy.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivy.assistant.data.AssistantSettingsStore
import com.ivy.base.legacy.SharedPrefs
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.deprecated.logic.notification.TransactionReminderLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Immutable
data class NotificationSettingsState(
    val detectedTransactions: Boolean,
    val cardReminders: Boolean,
    val reminderDaysBefore: Int,
    val budgetAlerts: Boolean,
    val dailyReminder: Boolean,
    val backupAlerts: Boolean,
)

sealed interface NotificationSettingsEvent {
    data class SetDetectedTransactions(val enabled: Boolean) : NotificationSettingsEvent
    data class SetCardReminders(val enabled: Boolean) : NotificationSettingsEvent
    data class SetReminderDaysBefore(val days: Int) : NotificationSettingsEvent
    data class SetBudgetAlerts(val enabled: Boolean) : NotificationSettingsEvent
    data class SetDailyReminder(val enabled: Boolean) : NotificationSettingsEvent
    data class SetBackupAlerts(val enabled: Boolean) : NotificationSettingsEvent
}

/** One switch per kind of notification the app can show. */
@Stable
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val store: AssistantSettingsStore,
    private val sharedPrefs: SharedPrefs,
    private val transactionReminderLogic: TransactionReminderLogic,
) : ComposeViewModel<NotificationSettingsState, NotificationSettingsEvent>() {

    private var detectedTransactions by mutableStateOf(
        sharedPrefs.getBoolean(SharedPrefs.NOTIFY_DETECTED_TRANSACTIONS, true)
    )

    // the original Ivy "show notifications" setting, which only ever controlled this reminder
    private var dailyReminder by mutableStateOf(sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true))

    @Composable
    override fun uiState(): NotificationSettingsState {
        val settings by store.settings.collectAsState()
        return NotificationSettingsState(
            detectedTransactions = detectedTransactions,
            cardReminders = settings.cardReminders,
            reminderDaysBefore = settings.reminderDaysBefore,
            budgetAlerts = settings.budgetAlerts,
            dailyReminder = dailyReminder,
            backupAlerts = settings.backupAlerts,
        )
    }

    override fun onEvent(event: NotificationSettingsEvent) {
        when (event) {
            is NotificationSettingsEvent.SetDetectedTransactions -> {
                detectedTransactions = event.enabled
                sharedPrefs.putBoolean(SharedPrefs.NOTIFY_DETECTED_TRANSACTIONS, event.enabled)
            }

            is NotificationSettingsEvent.SetCardReminders -> store.update { it.copy(cardReminders = event.enabled) }
            is NotificationSettingsEvent.SetReminderDaysBefore -> store.update {
                it.copy(reminderDaysBefore = event.days.coerceIn(MIN_DAYS_BEFORE, MAX_DAYS_BEFORE))
            }

            is NotificationSettingsEvent.SetBudgetAlerts -> store.update { it.copy(budgetAlerts = event.enabled) }
            is NotificationSettingsEvent.SetDailyReminder -> setDailyReminder(event.enabled)
            is NotificationSettingsEvent.SetBackupAlerts -> store.update { it.copy(backupAlerts = event.enabled) }
        }
    }

    private fun setDailyReminder(enabled: Boolean) {
        dailyReminder = enabled
        sharedPrefs.putBoolean(SharedPrefs.SHOW_NOTIFICATIONS, enabled)
        // switched off = no background job at all, not just a silent one
        if (enabled) transactionReminderLogic.scheduleReminder() else transactionReminderLogic.cancelReminder()
    }

    companion object {
        const val MIN_DAYS_BEFORE = 2
        const val MAX_DAYS_BEFORE = 10
    }
}
