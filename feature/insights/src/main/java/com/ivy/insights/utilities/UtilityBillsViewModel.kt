package com.ivy.insights.utilities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.db.dao.write.UtilityBillDao
import com.ivy.domain.utilities.UtilityBillPlanner
import com.ivy.domain.utilities.UtilityBillReader
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class UtilityBillsState(
    val enabled: Boolean,
    /** Each new bill becomes a planned payment */
    val planBills: Boolean,
    val hasPermission: Boolean,
    val loading: Boolean,
    val accounts: ImmutableList<String>,
    val selectedAccount: String?,
    /** null when the selected account has no bills */
    val report: UtilityReport?,
)

sealed interface UtilityBillsEvent {
    data object Refresh : UtilityBillsEvent
    data class SetEnabled(val enabled: Boolean) : UtilityBillsEvent
    data class SetPlanBills(val enabled: Boolean) : UtilityBillsEvent
    data class SelectAccount(val account: String) : UtilityBillsEvent

    /** The SMS permission prompt was answered. */
    data object PermissionChanged : UtilityBillsEvent
}

@Stable
@HiltViewModel
class UtilityBillsViewModel @Inject constructor(
    private val sharedPrefs: SharedPrefs,
    private val reader: UtilityBillReader,
    private val planner: UtilityBillPlanner,
    private val dao: UtilityBillDao,
) : ComposeViewModel<UtilityBillsState, UtilityBillsEvent>() {

    private var bills: List<UtilityBill> = emptyList()
    private var selectedAccount: String? = null

    private var state by mutableStateOf(
        UtilityBillsState(
            enabled = sharedPrefs.getBoolean(SharedPrefs.TRACK_UTILITY_BILLS, false),
            planBills = planBills(),
            hasPermission = reader.hasPermission(),
            loading = true,
            accounts = persistentListOf(),
            selectedAccount = null,
            report = null,
        )
    )

    @Composable
    override fun uiState(): UtilityBillsState = state

    override fun onEvent(event: UtilityBillsEvent) {
        when (event) {
            UtilityBillsEvent.Refresh, UtilityBillsEvent.PermissionChanged -> load()
            is UtilityBillsEvent.SetEnabled -> {
                sharedPrefs.putBoolean(SharedPrefs.TRACK_UTILITY_BILLS, event.enabled)
                // flip the switch now; the bills follow once the texts are read
                state = state.copy(enabled = event.enabled)
                load()
            }

            is UtilityBillsEvent.SetPlanBills -> {
                sharedPrefs.putBoolean(SharedPrefs.PLAN_UTILITY_BILLS, event.enabled)
                state = state.copy(planBills = event.enabled)
                // the bill that's due now gets its planned payment right away
                if (event.enabled) viewModelScope.launch { planner.planDueBills() }
            }

            is UtilityBillsEvent.SelectAccount -> {
                selectedAccount = event.account
                recompute()
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            // the history stays when tracking is off; it's only hidden
            if (enabled()) reader.readInbox()
            bills = dao.findAll().mapNotNull(UtilityCalculator::toBill)
            recompute()
        }
    }

    private fun recompute() {
        val accounts = UtilityCalculator.accounts(bills)
        val account = selectedAccount?.takeIf { it in accounts } ?: accounts.firstOrNull()
        state = UtilityBillsState(
            enabled = enabled(),
            planBills = planBills(),
            hasPermission = reader.hasPermission(),
            loading = false,
            accounts = accounts.toImmutableList(),
            selectedAccount = account,
            report = UtilityCalculator.report(bills.filter { it.account == account }, LocalDate.now()),
        )
    }

    private fun enabled(): Boolean = sharedPrefs.getBoolean(SharedPrefs.TRACK_UTILITY_BILLS, false)

    private fun planBills(): Boolean = sharedPrefs.getBoolean(SharedPrefs.PLAN_UTILITY_BILLS, true)
}
