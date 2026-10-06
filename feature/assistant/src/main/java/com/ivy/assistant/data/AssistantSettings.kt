package com.ivy.assistant.data

import kotlinx.serialization.Serializable

@Suppress("MagicNumber")
@Serializable
enum class BackupFrequency(val days: Long) {
    Off(0),
    Daily(1),
    Weekly(7),
}

@Suppress("DataClassDefaultValues")
@Serializable
data class AssistantSettings(
    val cardReminders: Boolean = true,
    /** Remind this many days before a card payment is due (and again the day before / on the day). */
    val reminderDaysBefore: Int = 3,
    val budgetAlerts: Boolean = true,
    val backupFrequency: BackupFrequency = BackupFrequency.Off,
    /** content:// URI of the backup file the user picked (can live in Google Drive). */
    val backupUri: String? = null,
    val backupFileName: String? = null,
    val lastBackupAtMillis: Long? = null,
    val lastBackupError: String? = null,
    /** Keys of reminders already shown, so each one fires once. */
    val sentKeys: Set<String> = emptySet(),
)
