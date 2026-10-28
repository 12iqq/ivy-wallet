package com.ivy.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.TransactionType
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.Reports
import java.time.LocalDate

/** Every paid transaction, newest first, with a search box. */
@Composable
internal fun TransactionsPage(
    ledger: Ledger,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(ledger, query) { Reports.search(ledger, query) }
    val listState = rememberLazyListState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = PagePadding, top = PagePadding, end = PagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(title = "Transactions", subtitle = "${results.size} transactions")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by name, note, category or account") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = PagePadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (results.isEmpty()) {
                    item { Note("No transactions match.") }
                }
                items(results, key = { it.id.value }) { trn ->
                    val category = trn.categoryId?.let { ledger.categories[it] }
                    val from = when (trn.type) {
                        TransactionType.TRANSFER -> "${trn.accountName} → ${trn.toAccountName.orEmpty()}"
                        else -> trn.accountName
                    }
                    TransactionRow(
                        date = Format.day(trn.time.toLocalDate(), today),
                        title = trn.title?.takeIf { it.isNotBlank() }
                            ?: category?.name
                            ?: if (trn.type == TransactionType.TRANSFER) "Transfer" else Reports.UNCATEGORIZED_NAME,
                        details = listOfNotNull(category?.name, from, trn.description?.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        amount = Format.money(trn.amount, trn.currency),
                        type = trn.type,
                        categoryColor = colorOf(category?.color),
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
    }
}
