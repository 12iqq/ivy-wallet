package com.ivy.insights.recurring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.model.IntervalType
import com.ivy.legacy.datamodel.PlannedPaymentRule
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.deprecated.logic.PlannedPaymentsGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** A charge's date and amount, for the list under an opened row. */
@Immutable
data class PastCharge(val date: LocalDate, val amount: Double)

@Immutable
data class RecurringRow(
    val key: String,
    val name: String,
    val cadence: Cadence,
    val amount: Double,
    val varies: Boolean,
    val previousAmount: Double?,
    val perMonth: Double,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val color: Int?,
    val category: String?,
    val account: String?,
    val planned: Boolean,
    val past: ImmutableList<PastCharge>,
)

@Immutable
data class RecurringChargesState(
    val loading: Boolean,
    val currency: String,
    /** What the active ones cost a month, together. */
    val perMonth: Double,
    val active: ImmutableList<RecurringRow>,
    val stopped: ImmutableList<RecurringRow>,
    /** The opened row. */
    val expanded: String?,
)

sealed interface RecurringChargesEvent {
    data object Refresh : RecurringChargesEvent
    data class Toggle(val key: String) : RecurringChargesEvent

    /** Makes it a recurring planned payment, from its next date. */
    data class Plan(val key: String) : RecurringChargesEvent
    data class Hide(val key: String) : RecurringChargesEvent
}

@Stable
@HiltViewModel
class RecurringChargesViewModel @Inject constructor(
    private val loader: RecurringChargesLoader,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val ruleWriter: WritePlannedPaymentRuleDao,
    private val plannedPaymentsGenerator: PlannedPaymentsGenerator,
) : ComposeViewModel<RecurringChargesState, RecurringChargesEvent>() {

    private var found: List<RecurringCharge> = emptyList()

    private var state by mutableStateOf(
        RecurringChargesState(
            loading = true,
            currency = "",
            perMonth = 0.0,
            active = persistentListOf(),
            stopped = persistentListOf(),
            expanded = null,
        )
    )

    @Composable
    override fun uiState(): RecurringChargesState = state

    override fun onEvent(event: RecurringChargesEvent) {
        when (event) {
            RecurringChargesEvent.Refresh -> load()
            is RecurringChargesEvent.Toggle -> state = state.copy(
                expanded = event.key.takeIf { it != state.expanded }
            )

            is RecurringChargesEvent.Plan -> plan(event.key)
            is RecurringChargesEvent.Hide -> {
                loader.hide(event.key)
                load()
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val data = loader.load()
            val categories = categoryDao.findAll().associateBy { it.id.toString() }
            val accounts = accountDao.findAll().associateBy { it.id.toString() }
            found = data.charges
            val rows = data.charges.map { charge ->
                val category = charge.categoryId?.let { categories[it] }
                RecurringRow(
                    key = charge.key,
                    name = charge.name,
                    cadence = charge.cadence,
                    amount = charge.amount,
                    varies = charge.varies,
                    previousAmount = charge.previousAmount,
                    perMonth = charge.perMonth,
                    lastDate = charge.lastDate,
                    nextDate = charge.nextDate,
                    color = category?.color,
                    category = category?.name,
                    account = charge.accountId?.let { accounts[it]?.name },
                    planned = charge.planned,
                    past = charge.charges.take(PAST_SHOWN).map { PastCharge(it.date, it.amount) }.toImmutableList(),
                )
            }
            val active = data.charges.filter { it.active }
            state = state.copy(
                loading = false,
                currency = data.currency,
                perMonth = active.sumOf { it.perMonth },
                active = rows.filter { row -> active.any { it.key == row.key } }.toImmutableList(),
                stopped = rows.filter { row -> active.none { it.key == row.key } }.toImmutableList(),
            )
        }
    }

    private fun plan(key: String) {
        val charge = found.firstOrNull { it.key == key && !it.planned } ?: return
        val accountId = charge.accountId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        viewModelScope.launch {
            val (intervalN, intervalType) = when (charge.cadence) {
                Cadence.Weekly -> 1 to IntervalType.WEEK
                Cadence.Monthly -> 1 to IntervalType.MONTH
                Cadence.Quarterly -> QUARTER_MONTHS to IntervalType.MONTH
                Cadence.Yearly -> 1 to IntervalType.YEAR
            }
            val rule = PlannedPaymentRule(
                startDate = charge.nextDate.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant(),
                intervalN = intervalN,
                intervalType = intervalType,
                oneTime = false,
                type = TransactionType.EXPENSE,
                accountId = accountId,
                amount = charge.planAmount,
                categoryId = charge.categoryId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                title = charge.name,
            )
            ruleWriter.save(rule.toEntity())
            plannedPaymentsGenerator.generate(rule)
            load()
        }
    }

    private companion object {
        const val PAST_SHOWN = 6
        const val QUARTER_MONTHS = 3
    }
}
