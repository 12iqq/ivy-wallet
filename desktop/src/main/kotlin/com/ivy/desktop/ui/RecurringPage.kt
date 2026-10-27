package com.ivy.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.reports.Format
import com.ivy.desktop.reports.Reports
import com.ivy.insights.recurring.Cadence
import com.ivy.insights.recurring.RecurringCharge
import java.time.LocalDate

/** Subscriptions, bills and fees that keep coming back, like Reports → Recurring charges on the phone. */
@Composable
internal fun RecurringPage(
    ledger: Ledger,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val found = remember(ledger, today) { Reports.recurring(ledger, today) }
    val active = found.filter { it.active }
    val stopped = found.filter { !it.active }
    val perMonth = active.sumOf { it.perMonth }
    val currency = ledger.baseCurrency
    val scroll = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(PagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTitle(
                title = "Recurring charges",
                subtitle = "Charges that come back every week, month, three months or year, found by their name.",
            )
            if (found.isEmpty()) Note("No recurring charges found yet.")
            if (active.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(
                        label = "About a month",
                        value = Format.money(perMonth, currency),
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        label = "A year",
                        value = Format.money(perMonth * MONTHS, currency),
                        modifier = Modifier.weight(1f),
                    )
                    Stat(
                        label = "Found",
                        value = "${active.size}",
                        note = "${active.count { !it.planned }} not planned in the phone app",
                        modifier = Modifier.weight(1f),
                    )
                }
                Panel(modifier = Modifier.fillMaxWidth()) {
                    active.forEach { ChargeLine(it, ledger, today, currency) }
                }
            }
            if (stopped.isNotEmpty()) {
                Panel(modifier = Modifier.fillMaxWidth(), title = "Not seen lately") {
                    Note("Late for their usual date, for example a subscription you cancelled.")
                    Spacer(Modifier.height(4.dp))
                    stopped.forEach { ChargeLine(it, ledger, today, currency) }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scroll),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun ChargeLine(
    charge: RecurringCharge,
    ledger: Ledger,
    today: LocalDate,
    currency: String,
) {
    val category = charge.categoryId?.let { ledger.categories[CategoryId(it)] }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(colorOf(category?.color))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = charge.name, style = MaterialTheme.typography.bodyLarge)
            Note(
                listOfNotNull(
                    cadenceName(charge.cadence),
                    "Planned".takeIf { charge.planned },
                    if (charge.active) {
                        "next about ${Format.day(charge.nextDate, today)}"
                    } else {
                        "last ${Format.day(charge.lastDate, today)}"
                    },
                ).joinToString(" · ")
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val price = Format.money(charge.amount, currency)
            Text(text = if (charge.varies) "about $price" else price, style = NumberStyle)
            if (charge.cadence != Cadence.Monthly) {
                Note("${Format.money(charge.perMonth, currency)} a month")
            }
            charge.previousAmount?.let { previous ->
                val up = charge.amount > previous
                Note(
                    text = "${if (up) "Up" else "Down"} from ${Format.money(previous, currency)}",
                    color = if (up) Orange else Green,
                )
            }
        }
    }
}

private fun cadenceName(cadence: Cadence): String = when (cadence) {
    Cadence.Weekly -> "Weekly"
    Cadence.Monthly -> "Monthly"
    Cadence.Quarterly -> "Every 3 months"
    Cadence.Yearly -> "Yearly"
}

private const val MONTHS = 12
