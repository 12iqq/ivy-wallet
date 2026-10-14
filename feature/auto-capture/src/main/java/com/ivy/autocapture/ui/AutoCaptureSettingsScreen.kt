package com.ivy.autocapture.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.ivy.autocapture.data.AutoCaptureMode
import com.ivy.autocapture.data.CardLink
import com.ivy.autocapture.data.SmsCheck
import com.ivy.autocapture.parser.BankProfile
import com.ivy.autocapture.parser.Direction
import com.ivy.autocapture.parser.UaeBanks
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.navigationBarInset
import com.ivy.legacy.utils.onScreenStart
import com.ivy.legacy.utils.toDensityDp
import com.ivy.navigation.AutoCaptureReviewScreen
import com.ivy.navigation.CSVScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GradientGreen
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.ActionsRow
import com.ivy.wallet.ui.theme.components.CloseButton
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyCheckboxWithText
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedTextField
import com.ivy.wallet.ui.theme.components.IvySwitch
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.wallet.ui.theme.gradientCutBackgroundTop
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalSet
import com.ivy.wallet.ui.theme.modal.ModalTitle
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.AutoCaptureSettingsScreenImpl() {
    val viewModel: AutoCaptureSettingsViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    onScreenStart { viewModel.onEvent(AutoCaptureSettingsEvent.Refresh) }
    UI(state = state, onEvent = viewModel::onEvent)
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun BoxWithConstraintsScope.UI(
    state: AutoCaptureSettingsState,
    onEvent: (AutoCaptureSettingsEvent) -> Unit,
) {
    val nav = navigation()
    val config = state.config
    var editingBank by remember { mutableStateOf<BankProfile?>(null) }

    val context = LocalContext.current
    // true once Android refused the permission (e.g. "restricted setting" for sideloaded apps)
    var permissionRefused by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionRefused = result.values.any { granted -> !granted }
        onEvent(AutoCaptureSettingsEvent.PermissionChanged)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        item {
            Spacer(Modifier.height(32.dp))
            Text(
                modifier = Modifier.padding(start = 24.dp),
                text = stringResource(R.string.auto_capture),
                style = UI.typo.h2.style(fontWeight = FontWeight.ExtraBold)
            )
            Text(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
                text = stringResource(R.string.auto_capture_description),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )

            if (state.pendingCount > 0) {
                Spacer(Modifier.height(16.dp))
                IvyButton(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.n_to_review, state.pendingCount),
                    iconStart = R.drawable.ic_planned_payments,
                    backgroundGradient = GradientGreen,
                ) { nav.navigateTo(AutoCaptureReviewScreen) }
            }

            SectionTitle(stringResource(R.string.auto_capture_mode))
        }

        items(AutoCaptureMode.entries.toList()) { mode ->
            ModeOption(
                mode = mode,
                selected = config.mode == mode,
            ) {
                onEvent(AutoCaptureSettingsEvent.SetMode(mode))
                if (mode.readsSms && !state.hasSmsPermission) {
                    permissionLauncher.launch(SMS_PERMISSIONS)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (config.mode.readsSms) {
            item {
                Spacer(Modifier.height(8.dp))
                if (!state.hasSmsPermission && permissionRefused) {
                    InfoCard(text = stringResource(R.string.sms_permission_restricted)) {
                        IvyButton(
                            text = stringResource(R.string.open_app_settings),
                            iconStart = R.drawable.ic_settings,
                        ) { context.openAppDetailsSettings() }
                    }
                } else if (!state.hasSmsPermission) {
                    InfoCard(text = stringResource(R.string.sms_permission_needed)) {
                        IvyButton(
                            text = stringResource(R.string.grant_permission),
                            iconStart = R.drawable.ic_check,
                        ) { permissionLauncher.launch(SMS_PERMISSIONS) }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IvyOutlinedButton(
                            text = if (state.scanning) "…" else stringResource(R.string.scan_recent_sms),
                            iconStart = R.drawable.ic_sync,
                        ) {
                            if (!state.scanning) onEvent(AutoCaptureSettingsEvent.ScanInbox)
                        }
                        state.scanResult?.let {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.scan_result, it),
                                style = UI.typo.c.style(color = Green, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SmsCheckCard(check = config.smsCheck) { onEvent(AutoCaptureSettingsEvent.SetSmsCheck(it)) }

                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.auto_add_without_review),
                    description = stringResource(R.string.auto_add_without_review_desc),
                    checked = config.addWithoutReview,
                ) { onEvent(AutoCaptureSettingsEvent.SetAddWithoutReview(it)) }

                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.read_wallet_notifications),
                    description = stringResource(
                        if (config.smsCheck.scheduled) {
                            R.string.read_wallet_notifications_paused
                        } else {
                            R.string.read_wallet_notifications_desc
                        }
                    ),
                    checked = config.readWalletNotifications,
                ) { onEvent(AutoCaptureSettingsEvent.SetReadWalletNotifications(it)) }

                // re-checked whenever the screen state refreshes (e.g. coming back from Android settings)
                val walletAccess = remember(state) { context.hasNotificationAccess() }
                if (config.readWalletNotifications && !config.smsCheck.scheduled && !walletAccess) {
                    Spacer(Modifier.height(8.dp))
                    InfoCard(text = stringResource(R.string.wallet_access_needed)) {
                        IvyButton(
                            text = stringResource(R.string.open_notification_access),
                            iconStart = R.drawable.ic_settings,
                        ) { context.openNotificationAccessSettings() }
                    }
                }
            }
        }

        if (config.mode.importsStatements) {
            item {
                Spacer(Modifier.height(12.dp))
                IvyOutlinedButton(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.import_statement),
                    iconStart = R.drawable.ic_export_csv,
                ) { nav.navigateTo(CSVScreen(launchedFromOnboarding = false)) }
            }
        }

        if (config.mode.readsSms) {
            item {
                SectionTitle(stringResource(R.string.my_banks))
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = stringResource(R.string.my_banks_desc),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
                Spacer(Modifier.height(12.dp))
            }

            items(UaeBanks.all + UaeBanks.OTHER) { bank ->
                BankRow(
                    bank = bank,
                    enabled = bank.id in config.enabledBankIds,
                    links = config.links.filter { it.bankId == bank.id }.toImmutableList(),
                    accounts = state.accounts,
                    onEnabledChange = {
                        onEvent(AutoCaptureSettingsEvent.SetBankEnabled(bank.id, it))
                        if (it) editingBank = bank
                    },
                    onEdit = { editingBank = bank },
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                SectionTitle(stringResource(R.string.test_a_message))
                TestMessageCard(state = state, onEvent = onEvent)
            }
        }

        item { Spacer(Modifier.height(150.dp)) } // scroll hack
    }

    ActionsRow(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .gradientCutBackgroundTop(UI.colors.pure, LocalDensity.current)
            .padding(bottom = navigationBarInset().toDensityDp())
            .padding(bottom = 24.dp)
    ) {
        Spacer(Modifier.width(20.dp))
        CloseButton { nav.back() }
        Spacer(Modifier.weight(1f))
    }

    BankModal(
        bank = editingBank,
        links = editingBank?.let { bank -> config.links.filter { it.bankId == bank.id } }
            .orEmpty().toImmutableList(),
        extraSenders = editingBank?.let { config.extraSenders[it.id] }.orEmpty().toImmutableList(),
        accounts = state.accounts,
        onEvent = onEvent,
        dismiss = { editingBank = null },
    )
}

private val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.READ_SMS,
)

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
private fun ModeOption(
    mode: AutoCaptureMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val (title, description) = when (mode) {
        AutoCaptureMode.Off ->
            stringResource(R.string.auto_capture_mode_off) to stringResource(R.string.auto_capture_mode_off_desc)

        AutoCaptureMode.Sms ->
            stringResource(R.string.auto_capture_mode_sms) to stringResource(R.string.auto_capture_mode_sms_desc)

        AutoCaptureMode.Statement ->
            stringResource(R.string.auto_capture_mode_statement) to
                stringResource(R.string.auto_capture_mode_statement_desc)

        AutoCaptureMode.Both ->
            stringResource(R.string.auto_capture_mode_both) to stringResource(R.string.auto_capture_mode_both_desc)
    }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .border(2.dp, if (selected) Ivy else UI.colors.medium, UI.shapes.r4)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = UI.typo.b2.style(
                color = if (selected) Ivy else UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )
        Text(
            text = description,
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
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

/** As messages arrive (instant) or every few hours (the app stays closed in between). */
@Composable
private fun SmsCheckCard(check: SmsCheck, onChange: (SmsCheck) -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(vertical = 16.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = stringResource(R.string.sms_check_title),
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SmsCheck.entries.forEach { option ->
                OptionChip(
                    text = when (option) {
                        SmsCheck.OnArrival -> stringResource(R.string.sms_check_on_arrival)
                        SmsCheck.Daily -> stringResource(R.string.sms_check_daily)
                        else -> stringResource(R.string.sms_check_hours, option.hours)
                    },
                    selected = option == check,
                ) { onChange(option) }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = stringResource(
                if (check.scheduled) R.string.sms_check_scheduled_desc else R.string.sms_check_on_arrival_desc
            ),
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun InfoCard(text: String, action: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(20.dp)
    ) {
        Text(text = text, style = UI.typo.b2.style(fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(12.dp))
        action()
    }
}

@Suppress("LongParameterList")
@Composable
private fun BankRow(
    bank: BankProfile,
    enabled: Boolean,
    links: ImmutableList<CardLink>,
    accounts: ImmutableList<Account>,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IvyCheckboxWithText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
                text = bank.displayName,
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
            if (enabled) {
                Text(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clip(UI.shapes.rFull)
                        .clickable { onEdit() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    text = stringResource(R.string.edit),
                    style = UI.typo.c.style(color = Ivy, fontWeight = FontWeight.ExtraBold)
                )
            }
        }
        if (enabled && links.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            links.forEach { link ->
                val account = accounts.firstOrNull { it.id.toString() == link.accountId }
                Text(
                    modifier = Modifier.padding(start = 56.dp, top = 2.dp),
                    text = "${link.last4?.let { "··$it" } ?: stringResource(R.string.any_card)} → " +
                        (account?.name ?: "?"),
                    style = UI.typo.c.style(
                        color = account?.color?.toComposeColor() ?: UI.colors.gray,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun TestMessageCard(
    state: AutoCaptureSettingsState,
    onEvent: (AutoCaptureSettingsEvent) -> Unit,
) {
    var sender by remember { mutableStateOf(TextFieldValue("")) }
    var body by remember { mutableStateOf(TextFieldValue("")) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        IvyOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = sender,
            hint = stringResource(R.string.sender),
        ) { sender = it }
        Spacer(Modifier.height(8.dp))
        IvyOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = body,
            hint = stringResource(R.string.test_a_message_hint),
        ) { body = it }
        Spacer(Modifier.height(12.dp))
        IvyButton(
            text = stringResource(R.string.test_a_message),
            iconStart = R.drawable.ic_check,
            enabled = body.text.isNotBlank(),
        ) { onEvent(AutoCaptureSettingsEvent.Test(sender.text, body.text)) }

        state.testResult?.let { result ->
            Spacer(Modifier.height(12.dp))
            val parsed = result.parsed
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UI.colors.medium, UI.shapes.r4)
                    .padding(16.dp),
                text = if (parsed == null) {
                    stringResource(R.string.not_recognized)
                } else {
                    buildString {
                        append(if (parsed.direction == Direction.Debit) "−" else "+")
                        append(parsed.amount.format(parsed.currency)).append(' ').append(parsed.currency)
                        parsed.merchant?.let { append("\n@ ").append(it) }
                        append("\n").append(UaeBanks.byId(parsed.bankId)?.displayName ?: parsed.bankId)
                        parsed.last4?.let { append(" ··").append(it) }
                        when (parsed.isCreditCard) {
                            true -> append(" (credit card)")
                            false -> append(" (debit/account)")
                            null -> Unit
                        }
                    }
                },
                style = UI.typo.b2.style(
                    color = if (parsed == null) Red else UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/** What a new link catches: one card / account number, or the rest of the bank's messages. */
private enum class LinkTarget { Card, Everything }

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun BoxWithConstraintsScope.BankModal(
    bank: BankProfile?,
    links: ImmutableList<CardLink>,
    extraSenders: ImmutableList<String>,
    accounts: ImmutableList<Account>,
    onEvent: (AutoCaptureSettingsEvent) -> Unit,
    dismiss: () -> Unit,
) {
    var target by remember(bank) { mutableStateOf(LinkTarget.Card) }
    var last4 by remember(bank) { mutableStateOf(TextFieldValue("")) }
    var account by remember(bank) { mutableStateOf<Account?>(null) }
    var senders by remember(bank) { mutableStateOf(TextFieldValue(extraSenders.joinToString(", "))) }

    val digits = last4.text.filter { it.isDigit() }.takeLast(CardDigits)
    // a link is only made once both steps are complete
    val ready = account != null && (target == LinkTarget.Everything || digits.length == CardDigits)
    fun addLink() {
        val chosen = account
        if (bank == null || chosen == null || !ready) return
        onEvent(
            AutoCaptureSettingsEvent.AddLink(
                bankId = bank.id,
                last4 = digits.takeIf { target == LinkTarget.Card },
                account = chosen,
            )
        )
        target = LinkTarget.Card
        last4 = TextFieldValue("")
        account = null
    }

    IvyModal(
        id = remember(bank) { UUID.randomUUID() },
        visible = bank != null,
        dismiss = dismiss,
        PrimaryAction = {
            ModalSet {
                bank?.let { onEvent(AutoCaptureSettingsEvent.SetExtraSenders(it.id, senders.text)) }
                // a finished link that wasn't added yet is saved too
                addLink()
                dismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))
        ModalTitle(text = bank?.displayName.orEmpty())

        ModalLabel(stringResource(R.string.linked_cards))
        if (links.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = stringResource(R.string.no_links_yet),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }
        links.forEach { link ->
            LinkedCardRow(
                link = link,
                account = accounts.firstOrNull { it.id.toString() == link.accountId },
                onRemove = { onEvent(AutoCaptureSettingsEvent.RemoveLink(link)) },
            )
        }

        ModalLabel(stringResource(R.string.add_a_link))
        StepTitle(stringResource(R.string.link_step_which))
        Row(modifier = Modifier.padding(horizontal = 32.dp)) {
            OptionChip(
                text = stringResource(R.string.link_target_card),
                selected = target == LinkTarget.Card,
            ) { target = LinkTarget.Card }
            Spacer(Modifier.width(8.dp))
            OptionChip(
                text = stringResource(R.string.any_card),
                selected = target == LinkTarget.Everything,
            ) { target = LinkTarget.Everything }
        }
        Spacer(Modifier.height(12.dp))
        if (target == LinkTarget.Card) {
            IvyOutlinedTextField(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                value = last4,
                hint = stringResource(R.string.card_ending),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
            ) { last4 = it }
            if (digits.isNotEmpty() && digits.length < CardDigits) {
                Text(
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp),
                    text = stringResource(R.string.enter_four_digits),
                    style = UI.typo.c.style(color = Red, fontWeight = FontWeight.Bold)
                )
            }
        } else {
            Text(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = stringResource(R.string.link_everything_desc, bank?.displayName.orEmpty()),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }

        StepTitle(stringResource(R.string.link_step_account))
        accounts.forEach { option ->
            AccountOption(account = option, selected = option.id == account?.id) { account = option }
        }

        Spacer(Modifier.height(16.dp))
        val chosen = account
        IvyButton(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
            text = if (ready && chosen != null) {
                stringResource(
                    R.string.add_link_preview,
                    if (target == LinkTarget.Card) "··$digits" else stringResource(R.string.any_card),
                    chosen.name
                )
            } else {
                stringResource(R.string.add_link)
            },
            iconStart = R.drawable.ic_plus,
            enabled = ready,
        ) { addLink() }
        if (!ready) {
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp),
                text = stringResource(R.string.add_link_help),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }

        ModalLabel(stringResource(R.string.extra_sender_ids))
        IvyOutlinedTextField(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
            value = senders,
            hint = bank?.senders?.take(2)?.joinToString(", ").orEmpty(),
        ) { senders = it }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ModalLabel(text: String) {
    Text(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp),
        text = text,
        style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
    )
}

@Composable
private fun StepTitle(text: String) {
    Text(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 8.dp),
        text = text,
        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
    )
}

/** "··1234 → Liv" with a remove button. */
@Composable
private fun LinkedCardRow(link: CardLink, account: Account?, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = link.last4?.let { "··$it" } ?: stringResource(R.string.any_card),
            style = UI.typo.nB2.style(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = "→",
            style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(account?.color?.toComposeColor() ?: UI.colors.gray, UI.shapes.rFull)
        )
        Text(
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f),
            text = account?.name ?: "?",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
        Text(
            modifier = Modifier
                .clip(UI.shapes.rFull)
                .clickable(onClick = onRemove)
                .padding(12.dp),
            text = "✕",
            style = UI.typo.b2.style(color = Red, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .background(if (selected) Ivy else UI.colors.medium, UI.shapes.rFull)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        text = text,
        style = UI.typo.b2.style(
            color = if (selected) White else UI.colors.pureInverse,
            fontWeight = FontWeight.Bold
        )
    )
}

/** Picks the account (doesn't save anything by itself). */
@Composable
private fun AccountOption(account: Account, selected: Boolean, onClick: () -> Unit) {
    val color = account.color.toComposeColor()
    val textColor = if (selected) findContrastTextColor(color) else UI.colors.pureInverse
    Row(
        modifier = Modifier
            .padding(horizontal = 32.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(if (selected) color else UI.colors.medium, UI.shapes.r4)
            .border(2.dp, if (selected) color else UI.colors.medium, UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(if (selected) textColor else color, UI.shapes.rFull)
        )
        Text(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
            text = account.name + if (account.isCreditCard) " · " + stringResource(R.string.credit_card) else "",
            style = UI.typo.b2.style(color = textColor, fontWeight = FontWeight.ExtraBold)
        )
        if (selected) {
            Text(text = "✓", style = UI.typo.b1.style(color = textColor, fontWeight = FontWeight.ExtraBold))
        }
    }
}

private const val CardDigits = 4

private fun Context.openAppDetailsSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private fun Context.hasNotificationAccess(): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

private fun Context.openNotificationAccessSettings() {
    startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
