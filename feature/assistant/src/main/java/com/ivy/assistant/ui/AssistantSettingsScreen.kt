package com.ivy.assistant.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivy.assistant.data.BackupFrequency
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvySwitch
import com.ivy.wallet.ui.theme.components.IvyToolbar
import java.text.DateFormat
import java.util.Date

@Composable
fun BoxWithConstraintsScope.AssistantSettingsScreenImpl() {
    val viewModel: AssistantSettingsViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    UI(state = state, onEvent = viewModel::onEvent)
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun BoxWithConstraintsScope.UI(
    state: AssistantSettingsState,
    onEvent: (AssistantSettingsEvent) -> Unit,
) {
    val nav = navigation()
    val context = LocalContext.current
    val settings = state.settings

    var canNotify by remember { mutableStateOf(context.canPostNotifications()) }
    onScreenStart { canNotify = context.canPostNotifications() }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> canNotify = granted }

    val chooseBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onEvent(AssistantSettingsEvent.BackupFileChosen(uri, context.displayName(uri)))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        item {
            IvyToolbar(onBack = { nav.onBackPressed() }) {}
            Text(
                modifier = Modifier.padding(start = 32.dp, top = 8.dp),
                text = stringResource(R.string.reminders_and_backups),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp),
                text = stringResource(R.string.reminders_and_backups_desc),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )

            if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(16.dp))
                IvyButton(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.allow_notifications),
                    iconStart = R.drawable.ic_notification_m,
                ) { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }

            SectionTitle(stringResource(R.string.credit_cards))
            SwitchRow(
                title = stringResource(R.string.card_reminders),
                description = stringResource(R.string.card_reminders_desc),
                checked = settings.cardReminders,
            ) { onEvent(AssistantSettingsEvent.SetCardReminders(it)) }

            if (settings.cardReminders) {
                Spacer(Modifier.height(8.dp))
                DaysBeforeRow(
                    days = settings.reminderDaysBefore,
                    onChange = { onEvent(AssistantSettingsEvent.SetReminderDaysBefore(it)) }
                )
            }

            SectionTitle(stringResource(R.string.budgets))
            SwitchRow(
                title = stringResource(R.string.budget_alerts),
                description = stringResource(R.string.budget_alerts_desc),
                checked = settings.budgetAlerts,
            ) { onEvent(AssistantSettingsEvent.SetBudgetAlerts(it)) }

            SectionTitle(stringResource(R.string.auto_backup))
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 24.dp),
                text = stringResource(R.string.auto_backup_desc),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                BackupFrequency.entries.forEach { frequency ->
                    FrequencyChip(
                        modifier = Modifier.weight(1f),
                        text = when (frequency) {
                            BackupFrequency.Off -> stringResource(R.string.auto_capture_mode_off)
                            BackupFrequency.Daily -> stringResource(R.string.backup_daily)
                            BackupFrequency.Weekly -> stringResource(R.string.backup_weekly)
                        },
                        selected = settings.backupFrequency == frequency,
                    ) {
                        if (frequency != BackupFrequency.Off && settings.backupUri == null) {
                            chooseBackupFile.launch(DefaultBackupName)
                        }
                        onEvent(AssistantSettingsEvent.SetBackupFrequency(frequency))
                    }
                    if (frequency != BackupFrequency.entries.last()) Spacer(Modifier.width(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            InfoRow(
                title = stringResource(R.string.backup_file),
                value = settings.backupFileName ?: stringResource(R.string.not_set),
            ) { chooseBackupFile.launch(DefaultBackupName) }

            Spacer(Modifier.height(8.dp))
            val status = when {
                settings.lastBackupError != null ->
                    stringResource(R.string.backup_error_status, settings.lastBackupError)

                settings.lastBackupAtMillis != null -> stringResource(
                    R.string.last_backup_at,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(settings.lastBackupAtMillis))
                )

                else -> null
            }
            if (status != null) {
                Text(
                    modifier = Modifier.padding(start = 32.dp, end = 24.dp),
                    text = status,
                    style = UI.typo.c.style(
                        color = if (settings.lastBackupError != null) Red else UI.colors.gray,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(8.dp))
            }

            if (settings.backupUri != null) {
                IvyOutlinedButton(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = if (state.backingUp) "…" else stringResource(R.string.backup_now),
                    iconStart = R.drawable.ic_vue_security_shield,
                ) { onEvent(AssistantSettingsEvent.BackupNow) }
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        Spacer(Modifier.height(32.dp))
        Text(
            modifier = Modifier.padding(start = 32.dp, end = 24.dp),
            text = text,
            style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = UI.typo.b2.style(fontWeight = FontWeight.Bold))
            Text(
                text = description,
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(Modifier.width(12.dp))
        IvySwitch(enabled = checked) { onCheckedChange(it) }
    }
}

@Composable
private fun DaysBeforeRow(days: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.remind_days_before, days),
            style = UI.typo.b2.style(fontWeight = FontWeight.SemiBold)
        )
        StepButton("−", enabled = days > AssistantSettingsViewModel.MIN_DAYS_BEFORE) { onChange(days - 1) }
        Spacer(Modifier.width(8.dp))
        StepButton("+", enabled = days < AssistantSettingsViewModel.MAX_DAYS_BEFORE) { onChange(days + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(UI.colors.medium, UI.shapes.rFull)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        text = text,
        style = UI.typo.b1.style(
            color = if (enabled) UI.colors.pureInverse else UI.colors.gray,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun FrequencyChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        modifier = modifier
            .clip(UI.shapes.rFull)
            .background(if (selected) Ivy else UI.colors.medium, UI.shapes.rFull)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        text = text,
        style = UI.typo.b2.style(
            color = if (selected) UI.colors.pure else UI.colors.pureInverse,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    )
}

@Composable
private fun InfoRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = value,
            maxLines = 1,
            style = UI.typo.c.style(
                color = UI.colors.gray,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            )
        )
    }
}

private const val DefaultBackupName = "IvyWalletUAE_auto_backup.zip"

private fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.displayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
