package com.ivy.home.quickadd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.action.transaction.HistoryTrnsAct
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** A [QuickAddTemplate] with the names and colors needed to show it. */
@Immutable
data class QuickAddChip(
    val template: QuickAddTemplate,
    val accountName: String,
    val currency: String,
    val categoryName: String?,
    val color: Int?,
)

@Immutable
data class QuickAddState(
    val chips: ImmutableList<QuickAddChip>,
)

sealed interface QuickAddEvent {
    data class Add(val chip: QuickAddChip, val amount: Double, val onAdded: () -> Unit) : QuickAddEvent
    data object Refresh : QuickAddEvent
}

@Stable
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val historyTrnsAct: HistoryTrnsAct,
    private val accountsAct: AccountsAct,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionMapper: TransactionMapper,
    private val baseCurrencyAct: BaseCurrencyAct,
) : ComposeViewModel<QuickAddState, QuickAddEvent>() {

    private var chips by mutableStateOf<ImmutableList<QuickAddChip>>(persistentListOf())

    init {
        load()
    }

    @Composable
    override fun uiState(): QuickAddState = QuickAddState(chips = chips)

    override fun onEvent(event: QuickAddEvent) {
        when (event) {
            is QuickAddEvent.Add -> viewModelScope.launch {
                add(event.chip, event.amount)
                event.onAdded()
                load()
            }

            QuickAddEvent.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val now = Instant.now()
            val history = historyTrnsAct(
                ClosedTimeRange(from = now.minus(Duration.ofDays(LOOKBACK_DAYS)), to = now)
            )
            val records = history.mapNotNull { trn ->
                // planned payments (rent, salary...) already have their own shortcut
                if (trn.metadata.recurringRuleId != null) return@mapNotNull null
                val title = trn.title?.value ?: return@mapNotNull null
                val daysAgo = Duration.between(trn.time, now).toDays()
                when (trn) {
                    is Expense -> QuickAddRecord(
                        title = title,
                        categoryId = trn.category?.value?.toString(),
                        accountId = trn.account.value.toString(),
                        isIncome = false,
                        amount = trn.value.amount.value,
                        daysAgo = daysAgo,
                    )

                    is Income -> QuickAddRecord(
                        title = title,
                        categoryId = trn.category?.value?.toString(),
                        accountId = trn.account.value.toString(),
                        isIncome = true,
                        amount = trn.value.amount.value,
                        daysAgo = daysAgo,
                    )

                    else -> null
                }
            }

            val baseCurrency = baseCurrencyAct(Unit)
            val accounts = accountsAct(Unit).associateBy { it.id.toString() }
            val categories = categoryRepository.findAll().associateBy { it.id.value.toString() }
            chips = QuickAddSuggestions.suggest(records).mapNotNull { template ->
                val account = accounts[template.accountId] ?: return@mapNotNull null
                val category = template.categoryId?.let { categories[it] }
                QuickAddChip(
                    template = template,
                    accountName = account.name,
                    currency = account.currency ?: baseCurrency,
                    categoryName = category?.name?.value,
                    color = category?.color?.value,
                )
            }.toImmutableList()
        }
    }

    private suspend fun add(chip: QuickAddChip, amount: Double) {
        val template = chip.template
        Transaction(
            accountId = UUID.fromString(template.accountId),
            type = if (template.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
            amount = amount.toBigDecimal(),
            title = template.title,
            categoryId = template.categoryId?.let(UUID::fromString),
            dateTime = Instant.now(),
        ).toDomain(transactionMapper)?.let { transactionRepository.save(it) }
    }

    private companion object {
        const val LOOKBACK_DAYS = 180L
    }
}
