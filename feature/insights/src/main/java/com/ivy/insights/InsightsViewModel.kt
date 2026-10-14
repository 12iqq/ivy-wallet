package com.ivy.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import arrow.core.Some
import com.ivy.data.model.Category
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transfer
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** A category slice ready to draw: [name] is null for "no category". */
@Immutable
data class CategorySlice(
    val key: String,
    val name: String?,
    val color: Int?,
    val amount: Double,
    val count: Int,
    val share: Double,
)

/** A transaction row in the report. */
@Immutable
data class ReportRow(
    val id: String?,
    val title: String?,
    val categoryName: String?,
    val color: Int?,
    val date: LocalDate,
    val amount: Double,
    val isIncome: Boolean,
)

@Immutable
data class InsightsState(
    val loading: Boolean,
    val currency: String,
    val mode: PeriodMode,
    val period: ReportPeriod,
    val canGoNext: Boolean,
    val kind: FlowKind,
    val selectedCategory: String?,
    val summary: ReportSummary,
    val categories: ImmutableList<CategorySlice>,
    /** Running total per day of the selected month (Month mode). */
    val pace: ImmutableList<Double>,
    val previousPace: ImmutableList<Double>,
    /** Totals per month of the selected year (Year mode). */
    val yearBars: ImmutableList<Double>,
    val topTitles: ImmutableList<TitleTotal>,
    val transactions: ImmutableList<ReportRow>,
    val hasMoreTransactions: Boolean,
    /** Last 12 months, oldest first. */
    val months: ImmutableList<MonthTotals>,
    val netWorth: ImmutableList<NetWorthPoint>,
)

sealed interface InsightsEvent {
    data object Refresh : InsightsEvent
    data class SetMode(val mode: PeriodMode) : InsightsEvent
    data object Previous : InsightsEvent
    data object Next : InsightsEvent
    data class SetKind(val kind: FlowKind) : InsightsEvent

    /** null clears the selection. */
    data class SelectCategory(val key: String?) : InsightsEvent
    data class SelectMonth(val month: YearMonth) : InsightsEvent
    data object ShowMoreTransactions : InsightsEvent
}

@Suppress("TooManyFunctions")
@Stable
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val exchangeAct: ExchangeAct,
) : ComposeViewModel<InsightsState, InsightsEvent>() {

    // loaded data
    private var records: List<FlowRecord> = emptyList()
    private var deltas: List<BalanceDelta> = emptyList()
    private var categories: Map<String, Category> = emptyMap()
    private var currency = ""

    // selection
    private var mode = PeriodMode.Month
    private var anchor: LocalDate = LocalDate.now()
    private var kind = FlowKind.Expense
    private var selectedCategory: String? = null
    private var transactionsShown = TRANSACTIONS_PAGE

    private var state by mutableStateOf(emptyState())

    @Composable
    override fun uiState(): InsightsState = state

    override fun onEvent(event: InsightsEvent) {
        when (event) {
            InsightsEvent.Refresh -> load()
            is InsightsEvent.SetMode -> select { mode = event.mode }
            InsightsEvent.Previous -> select { anchor = shift(anchor, -1) }
            InsightsEvent.Next -> select { if (canGoNext()) anchor = shift(anchor, 1) }
            is InsightsEvent.SetKind -> select { kind = event.kind }
            is InsightsEvent.SelectCategory -> select(keepCategory = true) {
                selectedCategory = event.key.takeIf { it != selectedCategory }
            }

            is InsightsEvent.SelectMonth -> select {
                mode = PeriodMode.Month
                anchor = event.month.atDay(1)
            }

            InsightsEvent.ShowMoreTransactions -> {
                transactionsShown += TRANSACTIONS_PAGE
                recompute()
            }
        }
    }

    /** Changes the selection and recomputes; most changes clear the category filter. */
    private fun select(keepCategory: Boolean = false, change: () -> Unit) {
        change()
        if (!keepCategory) selectedCategory = null
        transactionsShown = TRANSACTIONS_PAGE
        recompute()
    }

    private fun shift(date: LocalDate, by: Long): LocalDate = when (mode) {
        PeriodMode.Month -> date.plusMonths(by)
        PeriodMode.Year -> date.plusYears(by)
    }

    private fun canGoNext(): Boolean {
        val today = LocalDate.now()
        return when (mode) {
            PeriodMode.Month -> YearMonth.from(anchor).isBefore(YearMonth.from(today))
            PeriodMode.Year -> anchor.year < today.year
        }
    }

    private fun currentPeriod(): ReportPeriod = when (mode) {
        PeriodMode.Month -> ReportPeriod.month(YearMonth.from(anchor))
        PeriodMode.Year -> ReportPeriod.year(anchor.year)
    }

    private fun load() {
        viewModelScope.launch {
            val baseCurrency = baseCurrencyAct(Unit)
            val accounts = accountRepository.findAll().associateBy { it.id }
            val loadedCategories = categoryRepository.findAll().associateBy { it.id.value.toString() }
            val transactions = transactionRepository.findAll().filter { it.settled }

            val (loadedRecords, loadedDeltas) = withContext(Dispatchers.Default) {
                // one exchange rate per currency instead of one lookup per transaction
                val rates = mutableMapOf<String, Double>()
                suspend fun toBase(amount: Double, currency: String): Double {
                    if (currency == baseCurrency) return amount
                    val rate = rates.getOrPut(currency) {
                        exchangeAct(
                            ExchangeAct.Input(
                                data = ExchangeData(baseCurrency = baseCurrency, fromCurrency = Some(currency)),
                                amount = BigDecimal.ONE
                            )
                        ).getOrNull()?.toDouble() ?: 0.0
                    }
                    return amount * rate
                }

                val zone = ZoneId.systemDefault()
                val flows = mutableListOf<FlowRecord>()
                val balanceDeltas = mutableListOf<BalanceDelta>()
                val now = Instant.now()
                transactions.forEach { trn ->
                    val date = trn.time.atZone(zone).toLocalDate()
                    // like account balances: future-dated transactions don't count yet
                    val countsForBalance = !trn.time.isAfter(now)
                    fun record(kind: FlowKind, amount: Double): FlowRecord = FlowRecord(
                        date = date,
                        kind = kind,
                        amount = amount,
                        categoryId = trn.category?.value?.toString(),
                        title = trn.title?.value,
                        id = trn.id.value.toString(),
                        epochMillis = trn.time.toEpochMilli(),
                    )
                    when (trn) {
                        is Income -> {
                            val amount = toBase(trn.value.amount.value, trn.value.asset.code)
                            flows += record(FlowKind.Income, amount)
                            if (countsForBalance && accounts[trn.account]?.includeInBalance != false) {
                                balanceDeltas += BalanceDelta(date, amount)
                            }
                        }

                        is Expense -> {
                            val amount = toBase(trn.value.amount.value, trn.value.asset.code)
                            flows += record(FlowKind.Expense, amount)
                            if (countsForBalance && accounts[trn.account]?.includeInBalance != false) {
                                balanceDeltas += BalanceDelta(date, -amount)
                            }
                        }

                        is Transfer -> {
                            // moving money between counted accounts doesn't change the total
                            if (countsForBalance && accounts[trn.fromAccount]?.includeInBalance != false) {
                                val amount = toBase(trn.fromValue.amount.value, trn.fromValue.asset.code)
                                balanceDeltas += BalanceDelta(date, -amount)
                            }
                            if (countsForBalance && accounts[trn.toAccount]?.includeInBalance != false) {
                                val amount = toBase(trn.toValue.amount.value, trn.toValue.asset.code)
                                balanceDeltas += BalanceDelta(date, amount)
                            }
                        }
                    }
                }
                flows to balanceDeltas
            }

            records = loadedRecords
            deltas = loadedDeltas
            categories = loadedCategories
            currency = baseCurrency
            recompute()
        }
    }

    private fun recompute() {
        val today = LocalDate.now()
        val period = currentPeriod()
        val month = YearMonth.from(anchor)
        val category = selectedCategory
        val allTransactions = ReportsCalculator.transactions(records, kind, period, category)

        state = InsightsState(
            loading = false,
            currency = currency,
            mode = mode,
            period = period,
            canGoNext = canGoNext(),
            kind = kind,
            selectedCategory = category,
            summary = ReportsCalculator.summary(records, kind, period, mode, today, category),
            categories = ReportsCalculator.categoryShares(records, kind, period).map { share ->
                val details = categories[share.key]
                CategorySlice(
                    key = share.key,
                    name = details?.name?.value,
                    color = details?.color?.value,
                    amount = share.amount,
                    count = share.count,
                    share = share.share,
                )
            }.toImmutableList(),
            pace = if (mode == PeriodMode.Month) {
                ReportsCalculator.cumulativeByDay(records, kind, month, today, category).toImmutableList()
            } else {
                persistentListOf()
            },
            previousPace = if (mode == PeriodMode.Month) {
                ReportsCalculator.cumulativeByDay(records, kind, month.minusMonths(1), today, category)
                    .toImmutableList()
            } else {
                persistentListOf()
            },
            yearBars = if (mode == PeriodMode.Year) {
                ReportsCalculator.monthsOfYear(records, kind, anchor.year, category).toImmutableList()
            } else {
                persistentListOf()
            },
            topTitles = ReportsCalculator.topTitles(records, kind, period, category).toImmutableList(),
            transactions = allTransactions.take(transactionsShown).map { it.toRow() }.toImmutableList(),
            hasMoreTransactions = allTransactions.size > transactionsShown,
            months = InsightsCalculator.monthlyTotals(records, today).toImmutableList(),
            netWorth = InsightsCalculator.netWorth(deltas, today).toImmutableList(),
        )
    }

    private fun FlowRecord.toRow(): ReportRow {
        val details = categoryId?.let { categories[it] }
        return ReportRow(
            id = id,
            title = title,
            categoryName = details?.name?.value,
            color = details?.color?.value,
            date = date,
            amount = amount,
            isIncome = kind == FlowKind.Income,
        )
    }

    private fun emptyState(): InsightsState = InsightsState(
        loading = true,
        currency = "",
        mode = PeriodMode.Month,
        period = ReportPeriod.month(YearMonth.now()),
        canGoNext = false,
        kind = FlowKind.Expense,
        selectedCategory = null,
        summary = ReportSummary(total = 0.0, previousTotal = 0.0, count = 0, dailyAverage = 0.0),
        categories = persistentListOf(),
        pace = persistentListOf(),
        previousPace = persistentListOf(),
        yearBars = persistentListOf(),
        topTitles = persistentListOf(),
        transactions = persistentListOf(),
        hasMoreTransactions = false,
        months = persistentListOf(),
        netWorth = persistentListOf(),
    )

    private companion object {
        const val TRANSACTIONS_PAGE = 15
    }
}
