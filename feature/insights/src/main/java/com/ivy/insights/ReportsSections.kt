package com.ivy.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.toComposeColor
import kotlinx.collections.immutable.ImmutableList
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** A bordered card with a title, like the other Ivy cards. */
@Composable
fun ReportCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .border(2.dp, UI.colors.medium, UI.shapes.r4)
            .padding(16.dp)
    ) {
        Text(text = title, style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold))
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ReportChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        modifier = modifier
            .clip(UI.shapes.rFull)
            .background(if (selected) Ivy else UI.colors.medium, UI.shapes.rFull)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        text = text,
        style = UI.typo.b2.style(
            color = if (selected) UI.colors.pure else UI.colors.pureInverse,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    )
}

/** Category legend: tap a row to filter the report to that category. */
@Composable
fun CategoryList(
    slices: ImmutableList<CategorySlice>,
    selectedKey: String?,
    currency: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unspecified = stringResource(R.string.unspecified)
    Column(modifier = modifier) {
        slices.forEach { slice ->
            val color = slice.color?.toComposeColor() ?: UI.colors.gray
            val selected = slice.key == selectedKey
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(UI.shapes.r4)
                    .background(if (selected) UI.colors.medium else Color.Transparent, UI.shapes.r4)
                    .clickable { onSelect(slice.key) }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color, UI.shapes.rFull)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        modifier = Modifier.weight(1f),
                        text = slice.name ?: unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${(slice.share * PERCENT).toInt()}%",
                        style = UI.typo.nC.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = slice.amount.format(currency),
                        style = UI.typo.nB2.style(fontWeight = FontWeight.ExtraBold)
                    )
                }
                Spacer(Modifier.height(6.dp))
                ShareBar(fraction = slice.share.toFloat(), color = color)
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = stringResource(R.string.n_transactions, slice.count),
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

/** Top places / payees by total. */
@Composable
fun TopTitlesList(
    titles: ImmutableList<TitleTotal>,
    currency: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val max = titles.maxOfOrNull { it.amount }?.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier) {
        titles.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.width(24.dp),
                    text = "${index + 1}",
                    style = UI.typo.nC.style(color = UI.colors.gray, fontWeight = FontWeight.Bold)
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "×${item.count}",
                    style = UI.typo.nC.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.amount.format(currency),
                    style = UI.typo.nB2.style(fontWeight = FontWeight.ExtraBold)
                )
            }
            Spacer(Modifier.height(6.dp))
            ShareBar(
                modifier = Modifier.padding(start = 24.dp),
                fraction = (item.amount / max).toFloat(),
                color = color
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Transactions behind the numbers; tap one to open it. */
@Composable
fun ReportTransactions(
    rows: ImmutableList<ReportRow>,
    currency: String,
    hasMore: Boolean,
    onOpen: (ReportRow) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unspecified = stringResource(R.string.unspecified)
    val dateFormat = DateTimeFormatter.ofPattern("d MMM")
    Column(modifier = modifier) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(UI.shapes.r4)
                    .clickable { onOpen(row) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(row.color?.toComposeColor() ?: UI.colors.gray, UI.shapes.rFull)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.title ?: row.categoryName ?: unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = listOfNotNull(row.date.format(dateFormat), row.categoryName.takeIf { row.title != null })
                            .joinToString(" · "),
                        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                    )
                }
                Text(
                    text = (if (row.isIncome) "+" else "-") + row.amount.format(currency),
                    style = UI.typo.nB2.style(
                        color = if (row.isIncome) Green else UI.colors.pureInverse,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }
        }
        if (hasMore) {
            Spacer(Modifier.height(8.dp))
            ReportChip(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                text = stringResource(R.string.show_more),
                selected = false,
                onClick = onShowMore
            )
        }
    }
}

/** Month-by-month totals (Cashew's "past months"); tap a month to open it. */
@Composable
fun MonthHistory(
    months: ImmutableList<MonthTotals>,
    selected: YearMonth?,
    currency: String,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = DateTimeFormatter.ofPattern("MMM yyyy")
    Column(modifier = modifier) {
        months.asReversed().forEach { month ->
            val net = month.income - month.expense
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(UI.shapes.r4)
                    .background(if (month.month == selected) UI.colors.medium else Color.Transparent, UI.shapes.r4)
                    .clickable { onSelect(month.month) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = month.month.format(format),
                    style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${month.expense.format(currency)}",
                        style = UI.typo.nC.style(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "+${month.income.format(currency)}",
                        style = UI.typo.nC.style(color = Green, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    modifier = Modifier.width(NetColumnWidth.dp),
                    text = (if (net >= 0) "+" else "") + net.format(currency),
                    style = UI.typo.nB2.style(
                        color = if (net >= 0) Green else Red,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.End
                    )
                )
            }
        }
    }
}

private const val PERCENT = 100
private const val NetColumnWidth = 96
