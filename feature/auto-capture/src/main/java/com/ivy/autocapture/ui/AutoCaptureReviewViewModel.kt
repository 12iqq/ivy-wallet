package com.ivy.autocapture.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.autocapture.data.CaptureProcessor
import com.ivy.autocapture.parser.UaeBanks
import com.ivy.data.db.dao.write.CapturedTransactionDao
import com.ivy.data.db.entity.CapturedTransactionEntity
import com.ivy.data.model.Category
import com.ivy.data.repository.CategoryRepository
import com.ivy.legacy.datamodel.Account
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.account.AccountsAct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Immutable
data class ReviewItem(
    val captured: CapturedTransactionEntity,
    val bankName: String,
    val account: Account?,
    val category: Category?,
    val title: String?,
    val possibleDuplicate: Boolean,
)

@Immutable
data class AutoCaptureReviewState(
    val items: ImmutableList<ReviewItem>,
    val accounts: ImmutableList<Account>,
    val categories: ImmutableList<Category>,
)

sealed interface AutoCaptureReviewEvent {
    data class Add(val item: ReviewItem) : AutoCaptureReviewEvent
    data class Skip(val item: ReviewItem) : AutoCaptureReviewEvent
    data object AddAll : AutoCaptureReviewEvent
    data class SetAccount(val capturedId: UUID, val account: Account) : AutoCaptureReviewEvent
    data class SetCategory(val capturedId: UUID, val category: Category?) : AutoCaptureReviewEvent
    data class SetTitle(val capturedId: UUID, val title: String) : AutoCaptureReviewEvent
    data object Refresh : AutoCaptureReviewEvent
}

@HiltViewModel
class AutoCaptureReviewViewModel @Inject constructor(
    private val capturedDao: CapturedTransactionDao,
    private val processor: CaptureProcessor,
    private val accountsAct: AccountsAct,
    private val categoryRepository: CategoryRepository,
) : ComposeViewModel<AutoCaptureReviewState, AutoCaptureReviewEvent>() {

    private var items by mutableStateOf<ImmutableList<ReviewItem>>(persistentListOf())
    private var accounts by mutableStateOf<ImmutableList<Account>>(persistentListOf())
    private var categories by mutableStateOf<ImmutableList<Category>>(persistentListOf())

    // choices the user made on screen before pressing "Add"
    private val chosenAccounts = mutableMapOf<UUID, Account>()
    private val chosenCategories = mutableMapOf<UUID, Category?>()
    private val chosenTitles = mutableMapOf<UUID, String>()

    init {
        viewModelScope.launch {
            accounts = accountsAct(Unit)
            categories = categoryRepository.findAll().toImmutableList()
            capturedDao.pendingFlow().collect { pending ->
                items = pending.map { it.toReviewItem() }.toImmutableList()
            }
        }
    }

    @Composable
    override fun uiState(): AutoCaptureReviewState = AutoCaptureReviewState(
        items = items,
        accounts = accounts,
        categories = categories,
    )

    override fun onEvent(event: AutoCaptureReviewEvent) {
        when (event) {
            is AutoCaptureReviewEvent.Add -> viewModelScope.launch { add(event.item) }
            is AutoCaptureReviewEvent.Skip -> viewModelScope.launch {
                processor.dismiss(event.item.captured.id)
            }

            AutoCaptureReviewEvent.AddAll -> viewModelScope.launch {
                items.filter { it.account != null && !it.possibleDuplicate }.forEach { add(it) }
            }

            is AutoCaptureReviewEvent.SetAccount -> {
                chosenAccounts[event.capturedId] = event.account
                refresh(event.capturedId)
            }

            is AutoCaptureReviewEvent.SetCategory -> {
                chosenCategories[event.capturedId] = event.category
                refresh(event.capturedId)
            }

            AutoCaptureReviewEvent.Refresh -> viewModelScope.launch {
                accounts = accountsAct(Unit)
                categories = categoryRepository.findAll().toImmutableList()
                items = capturedDao.findPending().map { it.toReviewItem() }.toImmutableList()
            }

            is AutoCaptureReviewEvent.SetTitle -> {
                chosenTitles[event.capturedId] = event.title
                refresh(event.capturedId)
            }
        }
    }

    private suspend fun add(item: ReviewItem) {
        val account = item.account ?: return
        processor.add(
            captured = item.captured,
            accountId = account.id,
            categoryId = item.category?.id?.value,
            title = item.title,
        )
    }

    private fun refresh(capturedId: UUID) {
        viewModelScope.launch {
            items = items.map {
                if (it.captured.id == capturedId) it.captured.toReviewItem() else it
            }.toImmutableList()
        }
    }

    private suspend fun CapturedTransactionEntity.toReviewItem(): ReviewItem {
        val accountsNow = accounts.ifEmpty { accountsAct(Unit).also { accounts = it } }
        val categoriesNow = categories.ifEmpty {
            categoryRepository.findAll().toImmutableList().also { categories = it }
        }
        return ReviewItem(
            captured = this,
            bankName = UaeBanks.byId(bankId)?.displayName ?: bankId,
            account = chosenAccounts[id] ?: accountsNow.firstOrNull { it.id == accountId },
            category = if (chosenCategories.containsKey(id)) {
                chosenCategories[id]
            } else {
                categoriesNow.firstOrNull { it.id.value == categoryId }
            },
            title = chosenTitles[id] ?: merchant,
            possibleDuplicate = processor.isPossibleDuplicate(this),
        )
    }
}
