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
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.ivy.autocapture.data.AutoCaptureMode
import com.ivy.autocapture.data.CardLink
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
                SwitchRow(
                    title = stringResource(R.string.auto_add_without_review),
                    description = stringResource(R.string.auto_add_without_review_desc),
                    checked = config.addWithoutReview,
                ) { onEvent(AutoCaptureSettingsEvent.SetAddWithoutReview(it)) }
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
    var last4 by remember(bank) { mutableStateOf(TextFieldValue("")) }
    var senders by remember(bank) { mutableStateOf(TextFieldValue(extraSenders.joinToString(", "))) }

    IvyModal(
        id = remember(bank) { UUID.randomUUID() },
        visible = bank != null,
        dismiss = dismiss,
        PrimaryAction = {
            ModalSet {
                bank?.let { onEvent(AutoCaptureSettingsEvent.SetExtraSenders(it.id, senders.text)) }
                dismiss()
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))
        ModalTitle(text = bank?.displayName.orEmpty())
        Spacer(Modifier.height(16.dp))

        links.forEach { link ->
            val account = accounts.firstOrNull { it.id.toString() == link.accountId }
            Row(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "${link.last4?.let { "··$it" } ?: stringResource(R.string.any_card)} → " +
                        (account?.name ?: "?"),
                    style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                )
                Text(
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .clickable { onEvent(AutoCaptureSettingsEvent.RemoveLink(link)) }
                        .padding(8.dp),
                    text = "✕",
                    style = UI.typo.b2.style(color = Red)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.link_card_to_account),
            style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(12.dp))
        accounts.forEach { account ->
            val color = account.color.toComposeColor()
            Text(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(UI.shapes.r4)
                    .background(color, UI.shapes.r4)
                    .clickable {
                        bank?.let {
                            onEvent(
                                AutoCaptureSettingsEvent.AddLink(
                                    bankId = it.id,
                                    last4 = last4.text.takeIf { text -> text.isNotBlank() },
                                    account = account
                                )
                            )
                        }
                        last4 = TextFieldValue("")
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                text = account.name + if (account.isCreditCard) " · " + stringResource(R.string.credit_card) else "",
                style = UI.typo.b2.style(color = findContrastTextColor(color), fontWeight = FontWeight.ExtraBold)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.extra_sender_ids),
            style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.height(8.dp))
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

private fun Context.openAppDetailsSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
