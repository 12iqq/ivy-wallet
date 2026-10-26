package com.ivy.settings.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.toComposeColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.DuplicatesScreenImpl() {
    val viewModel: DuplicatesViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    DuplicatesUi(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun DuplicatesUi(state: DuplicatesState, onEvent: (DuplicatesEvent) -> Unit) {
    val nav = navigation()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        item {
            IvyToolbar(onBack = { nav.onBackPressed() }) {}
            Text(
                modifier = Modifier.padding(start = 32.dp, top = 8.dp),
                text = stringResource(R.string.find_duplicates),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
                text = stringResource(R.string.duplicates_desc),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }

        if (!state.loading && state.groups.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 32.dp),
                    text = stringResource(R.string.no_duplicates),
                    style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
        }

        items(state.groups, key = { it.key }) { group ->
            GroupCard(
                group = group,
                confirming = state.confirming,
                onDelete = { onEvent(DuplicatesEvent.Delete(it)) },
                onNotDuplicates = { onEvent(DuplicatesEvent.NotDuplicates(group.key)) },
            )
        }

        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun GroupCard(
    group: DuplicateCard,
    confirming: UUID?,
    onDelete: (UUID) -> Unit,
    onNotDuplicates: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(16.dp)
    ) {
        val sign = when (group.type) {
            TransactionType.INCOME -> "+"
            TransactionType.EXPENSE -> "-"
            TransactionType.TRANSFER -> ""
        }
        Text(
            text = "$sign${group.amount.format(group.currency)} ${group.currency}",
            style = UI.typo.nB1.style(
                color = if (group.type == TransactionType.INCOME) Green else UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )
        Text(
            text = "${group.account} · ${group.day.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))}",
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        group.rows.forEach { row ->
            DuplicateRowItem(row = row, confirming = row.id == confirming) { onDelete(row.id) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            modifier = Modifier
                .clip(UI.shapes.rFull)
                .background(UI.colors.medium, UI.shapes.rFull)
                .clickable(onClick = onNotDuplicates)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            text = stringResource(R.string.not_duplicates),
            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun DuplicateRowItem(row: DuplicateRow, confirming: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(row.categoryColor?.toComposeColor() ?: UI.colors.gray, UI.shapes.rFull)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title ?: row.category ?: stringResource(R.string.unspecified),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
            )
            Text(
                text = listOfNotNull(
                    row.time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                    row.category.takeIf { row.title != null },
                ).joinToString(" · "),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
            )
            listOfNotNull(row.description, row.bankDetails).forEach {
                Text(
                    text = it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            modifier = Modifier
                .clip(UI.shapes.rFull)
                .background(if (confirming) Red else UI.colors.medium, UI.shapes.rFull)
                .clickable(onClick = onDelete)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            text = stringResource(if (confirming) R.string.tap_again_to_delete else R.string.delete),
            style = UI.typo.c.style(
                color = if (confirming) UI.colors.pure else Red,
                fontWeight = FontWeight.ExtraBold
            )
        )
    }
}
