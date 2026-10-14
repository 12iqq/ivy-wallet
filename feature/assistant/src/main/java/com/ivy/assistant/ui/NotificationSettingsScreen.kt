package com.ivy.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvyToolbar

@Composable
fun BoxWithConstraintsScope.NotificationSettingsScreenImpl() {
    val viewModel: NotificationSettingsViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    UI(state = state, onEvent = viewModel::onEvent)
}

@Suppress("LongMethod")
@Composable
private fun UI(
    state: NotificationSettingsState,
    onEvent: (NotificationSettingsEvent) -> Unit,
) {
    val nav = navigation()
    val context = LocalContext.current

    var canNotify by remember { mutableStateOf(context.canNotify()) }
    // once refused, Android stops showing the prompt: send the user to Android's settings instead
    var askedPermission by remember { mutableStateOf(false) }
    onScreenStart { canNotify = context.canNotify() }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { canNotify = context.canNotify() }

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
                text = stringResource(R.string.notification_settings),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp),
                text = stringResource(R.string.notification_settings_desc),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )

            if (!canNotify) {
                Spacer(Modifier.height(16.dp))
                IvyButton(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.allow_notifications),
                    iconStart = R.drawable.ic_notification_m,
                ) {
                    if (context.needsPermissionRequest() && !askedPermission) {
                        askedPermission = true
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openAndroidNotificationSettings()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SwitchRow(
                title = stringResource(R.string.notify_detected),
                description = stringResource(R.string.notify_detected_desc),
                checked = state.detectedTransactions,
            ) { onEvent(NotificationSettingsEvent.SetDetectedTransactions(it)) }

            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.card_reminders),
                description = stringResource(R.string.card_reminders_desc),
                checked = state.cardReminders,
            ) { onEvent(NotificationSettingsEvent.SetCardReminders(it)) }
            if (state.cardReminders) {
                Spacer(Modifier.height(8.dp))
                DaysBeforeRow(
                    days = state.reminderDaysBefore,
                    onChange = { onEvent(NotificationSettingsEvent.SetReminderDaysBefore(it)) }
                )
            }

            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.budget_alerts),
                description = stringResource(R.string.budget_alerts_desc),
                checked = state.budgetAlerts,
            ) { onEvent(NotificationSettingsEvent.SetBudgetAlerts(it)) }

            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.daily_reminder),
                description = stringResource(R.string.daily_reminder_desc),
                checked = state.dailyReminder,
            ) { onEvent(NotificationSettingsEvent.SetDailyReminder(it)) }

            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.backup_alerts),
                description = stringResource(R.string.backup_alerts_desc),
                checked = state.backupAlerts,
            ) { onEvent(NotificationSettingsEvent.SetBackupAlerts(it)) }

            SectionTitle(stringResource(R.string.android_notification_settings_desc))
            IvyOutlinedButton(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.android_notification_settings),
                iconStart = R.drawable.ic_settings,
            ) { context.openAndroidNotificationSettings() }

            Spacer(Modifier.height(120.dp))
        }
    }
}

private fun Context.canNotify(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

/** Android 13+ asks for a permission; otherwise only Android's settings can turn notifications on. */
private fun Context.needsPermissionRequest(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun Context.openAndroidNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
