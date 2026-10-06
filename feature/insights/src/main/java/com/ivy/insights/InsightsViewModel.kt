package com.ivy.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import arrow.core.Some
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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@Immutable
data class CategoryBar(
    val name: String,
    val color: Int?,
    val thisMonth: Double,
    val lastMonth: Double,
)

@Immutable
data class InsightsState(
    val loading: Boolean,
    val currency: String,
    val months: ImmutableList<MonthTotals>,
    val netWorth: ImmutableList<NetWorthPoint>,
    val categories: ImmutableList<CategoryBar>,
    val averageMonthlySpend: Double,
)

sealed interface InsightsEvent {
    data object Refresh : InsightsEvent
}

@Stable
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val exchangeAct: ExchangeAct,
) : ComposeViewModel<InsightsState, InsightsEvent>() {

    private var state by mutableStateOf(
        InsightsState(
            loading = true,
            currency = "",
            months = persistentListOf(),
            netWorth = persistentListOf(),
            categories = persistentListOf(),
            averageMonthlySpend = 0.0,
        )
    )

    init {
        load()
    }

    @Composable
    override fun uiState(): InsightsState = state

    override fun onEvent(event: InsightsEvent) {
        when (event) {
            InsightsEvent.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val baseCurrency = baseCurrencyAct(Unit)
            val accounts = accountRepository.findAll().associateBy { it.id }
            val categories = categoryRepository.findAll().associateBy { it.id.value.toString() }
            val transactions = transactionRepository.findAll().filter { it.settled }

            val computed = withContext(Dispatchers.Default) {
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
                val deltas = mutableListOf<BalanceDelta>()
                transactions.forEach { trn ->
                    val date = trn.time.atZone(zone).toLocalDate()
                    when (trn) {
                        is Income -> {
                            val amount = toBase(trn.value.amount.value, trn.value.asset.code)
                            flows += FlowRecord(date, FlowKind.Income, amount, trn.category?.value?.toString())
                            if (accounts[trn.account]?.includeInBalance != false) deltas += BalanceDelta(date, amount)
                        }

                        is Expense -> {
                            val amount = toBase(trn.value.amount.value, trn.value.asset.code)
                            flows += FlowRecord(date, FlowKind.Expense, amount, trn.category?.value?.toString())
                            if (accounts[trn.account]?.includeInBalance != false) deltas += BalanceDelta(date, -amount)
                        }

                        is Transfer -> {
                            // moving money between counted accounts doesn't change the total
                            if (accounts[trn.fromAccount]?.includeInBalance != false) {
                                val amount = toBase(trn.fromValue.amount.value, trn.fromValue.asset.code)
                                deltas += BalanceDelta(date, -amount)
                            }
                            if (accounts[trn.toAccount]?.includeInBalance != false) {
                                val amount = toBase(trn.toValue.amount.value, trn.toValue.asset.code)
                                deltas += BalanceDelta(date, amount)
                            }
                        }
                    }
                }

                val today = LocalDate.now(zone)
                val months = InsightsCalculator.monthlyTotals(flows, today)
                InsightsState(
                    loading = false,
                    currency = baseCurrency,
                    months = months.toImmutableList(),
                    netWorth = InsightsCalculator.netWorth(deltas, today).toImmutableList(),
                    categories = InsightsCalculator.categoryChanges(flows, today).map { change ->
                        val category = change.categoryId?.let { categories[it] }
                        CategoryBar(
                            name = category?.name?.value ?: "",
                            color = category?.color?.value,
                            thisMonth = change.thisMonth,
                            lastMonth = change.lastMonth,
                        )
                    }.toImmutableList(),
                    averageMonthlySpend = InsightsCalculator.averageMonthlySpend(months),
                )
            }
            state = computed
        }
    }
}
