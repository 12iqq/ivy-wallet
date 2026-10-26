package com.ivy.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.model.Category
import com.ivy.data.repository.CategoryRepository
import com.ivy.domain.features.Features
import com.ivy.legacy.data.AppBaseData
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.temp.toLegacyDomain
import com.ivy.ui.ComposeViewModel
import com.ivy.ui.R
import com.ivy.ui.transactionlook.TagTextSize
import com.ivy.ui.transactionlook.TransactionLook
import com.ivy.ui.transactionlook.TransactionLookStore
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Immutable
data class TransactionLookState(
    val look: TransactionLook,
    val colorAccountTags: Boolean,
    /** null until loaded */
    val baseData: AppBaseData?,
    /** Your latest spend and a transfer between your accounts, to preview the look on. */
    val samples: ImmutableList<Transaction>,
)

sealed interface TransactionLookEvent {
    data class SetColorAccountTags(val enabled: Boolean) : TransactionLookEvent
    data class SetAccountTagSize(val size: TagTextSize) : TransactionLookEvent
    data class SetCategoryTagSize(val size: TagTextSize) : TransactionLookEvent
    data class SetAccountOnRight(val onRight: Boolean) : TransactionLookEvent
}

@Stable
@HiltViewModel
class TransactionLookViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val features: Features,
    private val store: TransactionLookStore,
    private val accountsAct: AccountsAct,
    private val categoryRepository: CategoryRepository,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val transactionDao: TransactionDao,
) : ComposeViewModel<TransactionLookState, TransactionLookEvent>() {

    private var baseData by mutableStateOf<AppBaseData?>(null)
    private var samples by mutableStateOf<ImmutableList<Transaction>>(persistentListOf())

    init {
        viewModelScope.launch {
            val accounts = accountsAct(Unit)
            val categories = categoryRepository.findAll().toImmutableList()
            baseData = AppBaseData(baseCurrency = baseCurrencyAct(Unit), accounts = accounts, categories = categories)
            samples = samples(accounts, categories)
        }
    }

    @Composable
    override fun uiState(): TransactionLookState {
        val look by store.look.collectAsState()
        return TransactionLookState(
            look = look,
            colorAccountTags = features.showAccountColorsInTransactions.asEnabledState(),
            baseData = baseData,
            samples = samples,
        )
    }

    override fun onEvent(event: TransactionLookEvent) {
        when (event) {
            // the same switch as "Colorful account labels" in Advanced features
            is TransactionLookEvent.SetColorAccountTags -> viewModelScope.launch {
                features.showAccountColorsInTransactions.set(context, event.enabled)
            }

            is TransactionLookEvent.SetAccountTagSize -> store.setAccountTagSize(event.size)
            is TransactionLookEvent.SetCategoryTagSize -> store.setCategoryTagSize(event.size)
            is TransactionLookEvent.SetAccountOnRight -> store.setAccountOnRight(event.onRight)
        }
    }

    private suspend fun samples(accounts: List<Account>, categories: List<Category>): ImmutableList<Transaction> {
        val now = Instant.now()
        // your own latest spend shows both tags as they really look
        val spend = transactionDao.findAllBetweenAndType(now.minus(SampleWindow), now, TransactionType.EXPENSE)
            .firstOrNull { trn ->
                trn.categoryId != null && !trn.title.isNullOrBlank() && accounts.any { it.id == trn.accountId }
            }
            ?.toLegacyDomain()
            ?: accounts.firstOrNull()?.let { account ->
                Transaction(
                    accountId = account.id,
                    type = TransactionType.EXPENSE,
                    amount = SampleAmount.toBigDecimal(),
                    title = context.getString(R.string.transaction_look_sample),
                    categoryId = categories.firstOrNull()?.id?.value,
                    dateTime = now,
                )
            }
        val transfer = if (accounts.size >= 2) {
            Transaction(
                accountId = accounts[0].id,
                toAccountId = accounts[1].id,
                type = TransactionType.TRANSFER,
                amount = SampleAmount.toBigDecimal(),
                dateTime = now,
            )
        } else {
            null
        }
        return listOfNotNull(spend, transfer).toImmutableList()
    }

    private companion object {
        val SampleWindow: Duration = Duration.ofDays(120)
        const val SampleAmount = 25.5
    }
}
