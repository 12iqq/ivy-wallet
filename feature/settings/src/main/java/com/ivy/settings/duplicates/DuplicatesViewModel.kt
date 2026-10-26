package com.ivy.settings.duplicates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@Suppress("DataClassTypedIDs")
@Immutable
data class DuplicateRow(
    val id: UUID,
    val title: String?,
    val category: String?,
    val categoryColor: Int?,
    val time: Instant,
    val description: String?,
    val bankDetails: String?,
)

@Immutable
data class DuplicateCard(
    val key: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String,
    val account: String,
    val day: LocalDate,
    val rows: ImmutableList<DuplicateRow>,
)

@Suppress("DataClassTypedIDs")
@Immutable
data class DuplicatesState(
    val loading: Boolean,
    val groups: ImmutableList<DuplicateCard>,
    /** The row whose Delete was tapped once: a second tap deletes it. */
    val confirming: UUID?,
)

@Suppress("DataClassTypedIDs")
sealed interface DuplicatesEvent {
    data class Delete(val transactionId: UUID) : DuplicatesEvent
    data class NotDuplicates(val key: String) : DuplicatesEvent
}

@Stable
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionWriter: WriteTransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val settingsDao: SettingsDao,
    private val sharedPrefs: SharedPrefs,
) : ComposeViewModel<DuplicatesState, DuplicatesEvent>() {

    private var state by mutableStateOf(DuplicatesState(loading = true, groups = persistentListOf(), confirming = null))

    init {
        load()
    }

    @Composable
    override fun uiState(): DuplicatesState = state

    override fun onEvent(event: DuplicatesEvent) {
        when (event) {
            is DuplicatesEvent.Delete -> if (state.confirming == event.transactionId) {
                viewModelScope.launch {
                    transactionWriter.deleteById(event.transactionId)
                    load()
                }
            } else {
                state = state.copy(confirming = event.transactionId)
            }

            is DuplicatesEvent.NotDuplicates -> {
                sharedPrefs.putString(SharedPrefs.IGNORED_DUPLICATES, (ignored() + event.key).joinToString(SEPARATOR))
                state = state.copy(groups = state.groups.filter { it.key != event.key }.toImmutableList())
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val transactions = transactionDao.findAll().filter { it.dateTime != null }
            val byId = transactions.associateBy { it.id }
            val accounts = accountDao.findAll().associateBy { it.id }
            val categories = categoryDao.findAll().associateBy { it.id }
            val baseCurrency = settingsDao.findFirst().currency
            val entries = transactions.mapNotNull { trn ->
                DuplicateEntry(
                    id = trn.id,
                    type = trn.type,
                    accountId = trn.accountId,
                    toAccountId = trn.toAccountId,
                    amount = trn.amount,
                    time = trn.dateTime ?: return@mapNotNull null,
                )
            }
            val groups = DuplicateFinder.groups(entries, zone, ignored()).map { group ->
                val first = group.entries.first()
                val account = accounts[first.accountId]
                DuplicateCard(
                    key = group.key,
                    type = first.type,
                    amount = first.amount,
                    currency = account?.currency ?: baseCurrency,
                    account = account?.name.orEmpty(),
                    day = group.day,
                    rows = group.entries.mapNotNull { byId[it.id] }.map { trn ->
                        val category = trn.categoryId?.let { categories[it] }
                        DuplicateRow(
                            id = trn.id,
                            title = trn.title?.takeIf { it.isNotBlank() },
                            category = category?.name,
                            categoryColor = category?.color,
                            time = trn.dateTime ?: Instant.EPOCH,
                            description = trn.description?.takeIf { it.isNotBlank() },
                            bankDetails = trn.bankDetails?.takeIf { it.isNotBlank() },
                        )
                    }.toImmutableList(),
                )
            }
            state = DuplicatesState(loading = false, groups = groups.toImmutableList(), confirming = null)
        }
    }

    private fun ignored(): Set<String> = sharedPrefs.getString(SharedPrefs.IGNORED_DUPLICATES, null)
        ?.split(SEPARATOR)
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()

    private companion object {
        const val SEPARATOR = "\n"
    }
}
