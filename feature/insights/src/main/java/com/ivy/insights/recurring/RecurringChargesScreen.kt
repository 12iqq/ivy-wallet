package com.ivy.insights.recurring

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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.insights.ReportCard
import com.ivy.insights.ReportChip
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.onScreenStart
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.toComposeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun BoxWithConstraintsScope.RecurringChargesScreenImpl() {
    val viewModel: RecurringChargesViewModel = screenScopedViewModel()
    val state = viewModel.uiState()
    onScreenStart { viewModel.onEvent(RecurringChargesEvent.Refresh) }
    RecurringChargesUi(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun RecurringChargesUi(state: RecurringChargesState, onEvent: (RecurringChargesEvent) -> Unit) {
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
                text = stringResource(R.string.recurring_charges),
                style = UI.typo.h2.style(fontWeight = FontWeight.Black)
            )
            Text(
                modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
                text = stringResource(R.string.recurring_charges_desc),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }

        if (!state.loading && state.active.isEmpty() && state.stopped.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    text = stringResource(R.string.recurring_none),
                    style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
        }

        if (state.active.isNotEmpty()) {
            item {
                Total(perMonth = state.perMonth, currency = state.currency)
            }
            items(state.active, key = { it.key }) { row ->
                ChargeRow(
                    row = row,
                    currency = state.currency,
                    active = true,
                    expanded = state.expanded == row.key,
                    onEvent = onEvent,
                )
            }
        }

        if (state.stopped.isNotEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(start = 32.dp, top = 24.dp),
                    text = stringResource(R.string.recurring_stopped),
                    style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
                    text = stringResource(R.string.recurring_stopped_desc),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
            items(state.stopped, key = { "stopped:${it.key}" }) { row ->
                ChargeRow(
                    row = row,
                    currency = state.currency,
                    active = false,
                    expanded = state.expanded == row.key,
                    onEvent = onEvent,
                )
            }
        }

        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun Total(perMonth: Double, currency: String) {
    Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.recurring_about_a_month, money(perMonth, currency)),
            style = UI.typo.nH2.style(fontWeight = FontWeight.ExtraBold)
        )
        Text(
            text = stringResource(R.string.recurring_a_year, money(perMonth * MONTHS, currency)),
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun ChargeRow(
    row: RecurringRow,
    currency: String,
    active: Boolean,
    expanded: Boolean,
    onEvent: (RecurringChargesEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .clickable { onEvent(RecurringChargesEvent.Toggle(row.key)) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(row.color?.toComposeColor() ?: UI.colors.gray, UI.shapes.rFull)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = listOfNotNull(
                        stringResource(cadenceName(row.cadence)),
                        stringResource(R.string.recurring_planned).takeIf { row.planned },
                        row.account,
                    ).joinToString(" · "),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = if (active) {
                        stringResource(R.string.recurring_next, day(row.nextDate))
                    } else {
                        stringResource(R.string.recurring_last, day(row.lastDate))
                    },
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (row.varies) {
                        stringResource(R.string.recurring_about, money(row.amount, currency))
                    } else {
                        money(row.amount, currency)
                    },
                    style = UI.typo.nB2.style(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End)
                )
                if (row.cadence != Cadence.Monthly) {
                    Text(
                        text = stringResource(R.string.recurring_a_month, money(row.perMonth, currency)),
                        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
                    )
                }
                row.previousAmount?.let { previous ->
                    Text(
                        text = stringResource(
                            if (row.amount > previous) R.string.recurring_up_from else R.string.recurring_down_from,
                            money(previous, currency)
                        ),
                        style = UI.typo.c.style(
                            color = if (row.amount > previous) Orange else Green,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            row.past.forEach { past ->
                Row(modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 2.dp)) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = past.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = money(past.amount, currency),
                        style = UI.typo.nC.style(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.padding(start = 20.dp)) {
                if (!row.planned) {
                    ReportChip(text = stringResource(R.string.recurring_plan_it), selected = true) {
                        onEvent(RecurringChargesEvent.Plan(row.key))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                ReportChip(text = stringResource(R.string.recurring_not_recurring), selected = false) {
                    onEvent(RecurringChargesEvent.Hide(row.key))
                }
            }
        }
    }
}

/** For the Reports card: what's found, together a month. */
@Immutable
data class RecurringCard(
    val loading: Boolean,
    val currency: String,
    val perMonth: Double,
    val count: Int,
    val unplanned: Int,
)

/** Reports card: what the recurring charges cost a month, and a way in to the list. */
@Composable
fun RecurringChargesCard(card: RecurringCard, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ReportCard(title = stringResource(R.string.recurring_charges), modifier = modifier) {
        if (card.count == 0) {
            Text(
                text = stringResource(if (card.loading) R.string.recurring_looking else R.string.recurring_none),
                style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        } else {
            Text(
                text = stringResource(R.string.recurring_about_a_month, money(card.perMonth, card.currency)),
                style = UI.typo.nB1.style(fontWeight = FontWeight.ExtraBold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.recurring_found, card.count, card.unplanned),
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(12.dp))
        ReportChip(text = stringResource(R.string.recurring_see_all), selected = false, onClick = onOpen)
    }
}

// a string id, not a composable returning the text: those can't be skipped
private fun cadenceName(cadence: Cadence): Int = when (cadence) {
    Cadence.Weekly -> R.string.recurring_weekly
    Cadence.Monthly -> R.string.recurring_monthly
    Cadence.Quarterly -> R.string.recurring_quarterly
    Cadence.Yearly -> R.string.recurring_yearly
}

private fun money(amount: Double, currency: String): String = "${amount.format(currency)} $currency"

private fun day(date: LocalDate): String {
    val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern))
}

private const val MONTHS = 12
