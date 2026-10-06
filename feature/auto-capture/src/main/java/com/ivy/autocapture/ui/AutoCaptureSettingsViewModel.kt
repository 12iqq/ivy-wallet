package com.ivy.autocapture.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.autocapture.data.AutoCaptureConfig
import com.ivy.autocapture.data.AutoCaptureMode
import com.ivy.autocapture.data.AutoCaptureSettingsStore
import com.ivy.autocapture.data.CaptureProcessor
import com.ivy.autocapture.data.CardLink
import com.ivy.autocapture.parser.ParsedMessage
import com.ivy.autocapture.sms.SmsInboxScanner
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.legacy.datamodel.Account
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.account.AccountsAct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class AutoCaptureSettingsState(
    val config: AutoCaptureConfig,
    val accounts: ImmutableList<Account>,
    val hasSmsPermission: Boolean,
    val scanning: Boolean,
    val scanResult: Int?,
    val pendingCount: Int,
    /** null = not tested yet, Result(null) = not recognized */
    val testResult: TestResult?,
)

@Immutable
data class TestResult(val parsed: ParsedMessage?)

@Suppress("DataClassTypedIDs")
sealed interface AutoCaptureSettingsEvent {
    data class SetMode(val mode: AutoCaptureMode) : AutoCaptureSettingsEvent
    data class SetBankEnabled(val bankId: String, val enabled: Boolean) : AutoCaptureSettingsEvent
    data class SetAddWithoutReview(val enabled: Boolean) : AutoCaptureSettingsEvent
    data class AddLink(val bankId: String, val last4: String?, val account: Account) :
        AutoCaptureSettingsEvent

    data class RemoveLink(val link: CardLink) : AutoCaptureSettingsEvent
    data class SetExtraSenders(val bankId: String, val senders: String) : AutoCaptureSettingsEvent
    data class Test(val sender: String, val body: String) : AutoCaptureSettingsEvent
    data object PermissionChanged : AutoCaptureSettingsEvent
    data object ScanInbox : AutoCaptureSettingsEvent
    data object Refresh : AutoCaptureSettingsEvent
}

@Suppress("MagicNumber")
@HiltViewModel
class AutoCaptureSettingsViewModel @Inject constructor(
    private val store: AutoCaptureSettingsStore,
    private val processor: CaptureProcessor,
    private val scanner: SmsInboxScanner,
    private val accountsAct: AccountsAct,
    private val capturedDao: CapturedTransactionDao,
) : ComposeViewModel<AutoCaptureSettingsState, AutoCaptureSettingsEvent>() {

    private var accounts by mutableStateOf<ImmutableList<Account>>(persistentListOf())
    private var hasSmsPermission by mutableStateOf(scanner.hasPermission())
    private var scanning by mutableStateOf(false)
    private var scanResult by mutableStateOf<Int?>(null)
    private var testResult by mutableStateOf<TestResult?>(null)
    private val pendingCountFlow = capturedDao.pendingCountFlow()

    init {
        viewModelScope.launch { accounts = accountsAct(Unit) }
    }

    @Composable
    override fun uiState(): AutoCaptureSettingsState {
        val config by store.config.collectAsState()
        val pendingCount by pendingCountFlow.collectAsState(initial = 0)
        return AutoCaptureSettingsState(
            config = config,
            accounts = accounts,
            hasSmsPermission = hasSmsPermission,
            scanning = scanning,
            scanResult = scanResult,
            pendingCount = pendingCount,
            testResult = testResult,
        )
    }

    override fun onEvent(event: AutoCaptureSettingsEvent) {
        when (event) {
            is AutoCaptureSettingsEvent.SetMode -> store.update { it.copy(mode = event.mode) }
            is AutoCaptureSettingsEvent.SetBankEnabled -> store.update {
                it.copy(
                    enabledBankIds = if (event.enabled) {
                        it.enabledBankIds + event.bankId
                    } else {
                        it.enabledBankIds - event.bankId
                    }
                )
            }

            is AutoCaptureSettingsEvent.SetAddWithoutReview -> store.update {
                it.copy(addWithoutReview = event.enabled)
            }

            is AutoCaptureSettingsEvent.AddLink -> store.update { config ->
                val last4 = event.last4?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
                val link = CardLink(event.bankId, last4, event.account.id.toString())
                config.copy(
                    links = config.links.filterNot { it.bankId == link.bankId && it.last4 == link.last4 } + link
                )
            }

            is AutoCaptureSettingsEvent.RemoveLink -> store.update {
                it.copy(links = it.links - event.link)
            }

            is AutoCaptureSettingsEvent.SetExtraSenders -> store.update { config ->
                val senders = event.senders.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                config.copy(extraSenders = config.extraSenders + (event.bankId to senders))
            }

            is AutoCaptureSettingsEvent.Test -> {
                testResult = TestResult(processor.preview(event.sender, event.body))
            }

            AutoCaptureSettingsEvent.PermissionChanged -> hasSmsPermission = scanner.hasPermission()
            AutoCaptureSettingsEvent.Refresh -> viewModelScope.launch {
                accounts = accountsAct(Unit)
                hasSmsPermission = scanner.hasPermission()
                testResult = null
                scanResult = null
            }
            AutoCaptureSettingsEvent.ScanInbox -> viewModelScope.launch {
                scanning = true
                scanResult = scanner.scan()
                scanning = false
            }
        }
    }
}
