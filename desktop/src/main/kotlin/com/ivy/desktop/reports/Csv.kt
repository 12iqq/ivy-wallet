package com.ivy.desktop.reports

import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerTransaction
import com.ivy.desktop.data.TransactionType
import com.ivy.insights.recurring.RecurringCharge
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a page shows as a spreadsheet file (CSV) for Excel or Google Sheets. */
object Csv {
    /** Tells Excel the file is UTF-8, so names in Arabic come through */
    private const val BOM = "\uFEFF"
    private const val NEWLINE = "\r\n"

    /** A leading = + - or @ makes a spreadsheet treat text as a formula */
    private val FormulaStart = setOf('=', '+', '-', '@', '\t', '\r')

    /** A field with one of these needs quotes around it */
    private val NeedsQuotes = setOf(',', '"', '\n', '\r')
    private val Time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun transactions(transactions: List<LedgerTransaction>, ledger: Ledger): String = table(
        header = listOf(
            "Date", "Time", "Type", "Title", "Category", "Account", "To account", "Amount", "Currency", "Note",
        ),
        rows = transactions.map { trn ->
            listOf(
                trn.time.toLocalDate().toString(),
                trn.time.format(Time),
                typeName(trn.type),
                text(trn.title),
                text(trn.categoryId?.let { ledger.categories[it]?.name }),
                text(trn.accountName),
                text(trn.toAccountName),
                number(trn.amount),
                trn.currency,
                text(trn.description),
            )
        },
    )

    /** The Overview's charts as numbers: each month's income, spending and total balance. */
    fun months(overview: OverviewView): String = table(
        header = listOf("Month", "Income", "Spending", "Saved", "Total balance at the end of the month"),
        rows = overview.months.zip(overview.netWorth) { month, balance ->
            listOf(
                Format.month(month.month),
                number(month.income),
                number(month.expense),
                number(month.income - month.expense),
                number(balance.value),
            )
        },
    )

    fun recurring(charges: List<RecurringCharge>, ledger: Ledger): String = table(
        header = listOf("Name", "Category", "How often", "Amount", "A month", "Last", "Next", "Still coming"),
        rows = charges.map { charge ->
            listOf(
                text(charge.name),
                text(charge.categoryId?.let { ledger.categories[CategoryId(it)]?.name }),
                Format.cadence(charge.cadence),
                number(charge.amount),
                number(charge.perMonth),
                charge.lastDate.toString(),
                charge.nextDate.toString(),
                if (charge.active) "Yes" else "No",
            )
        },
    )

    private fun typeName(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "Income"
        TransactionType.EXPENSE -> "Expense"
        TransactionType.TRANSFER -> "Transfer"
    }

    private fun table(header: List<String>, rows: List<List<String>>): String =
        (listOf(header) + rows).joinToString(separator = NEWLINE, prefix = BOM, postfix = NEWLINE) { row ->
            row.joinToString(",", transform = ::field)
        }

    /** Quotes a field with a comma, a quote or a line break in it. */
    private fun field(value: String): String =
        if (value.any { it in NeedsQuotes }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    /** A name or a note, with a ' in front if a spreadsheet would read it as a formula. */
    private fun text(value: String?): String {
        val text = value.orEmpty()
        return if (text.firstOrNull() in FormulaStart) "'$text" else text
    }

    /** 1234.50: a dot for decimals and no thousands commas, which every spreadsheet reads as a number */
    private fun number(value: Double): String = String.format(Locale.ENGLISH, "%.2f", value)
}
