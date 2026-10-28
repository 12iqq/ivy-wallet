package com.ivy.desktop.reports

import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerTransaction
import com.ivy.desktop.data.TransactionType
import com.ivy.insights.recurring.RecurringCharge
import java.time.YearMonth
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
    private const val PERCENT = 100

    fun transactions(transactions: List<LedgerTransaction>, ledger: Ledger): String = table(
        header = listOf(
            "Date", "Time", "Weekday", "Month", "Type", "Title", "Category", "Amount", "Money in or out",
            "Currency", "Account", "Account type", "To account", "Bank details", "Tags", "Planned payment", "Note",
        ),
        rows = transactions.map { trn ->
            listOf(
                trn.time.toLocalDate().toString(),
                trn.time.format(Time),
                Format.weekday(trn.time.dayOfWeek),
                YearMonth.from(trn.time).toString(),
                typeName(trn.type),
                text(trn.title),
                text(categoryName(trn, ledger)),
                number(trn.amount),
                // spending as a minus, so a column total is what's left; a transfer is neither
                when (trn.type) {
                    TransactionType.INCOME -> number(trn.amount)
                    TransactionType.EXPENSE -> number(-trn.amount)
                    TransactionType.TRANSFER -> ""
                },
                trn.currency,
                text(trn.accountName),
                if (trn.byCard) "Credit card" else "Account",
                text(trn.toAccountName),
                text(trn.bankDetails),
                text(trn.tags.joinToString("; ")),
                if (trn.planned) "Yes" else "No",
                text(trn.description),
            )
        },
    )

    /**
     * The Overview's months: income, spending, what was left and the total balance at the end of each
     * month, then what was spent on each category and with each account.
     */
    fun months(overview: OverviewView, ledger: Ledger): String {
        val balances = overview.netWorth.associate { it.month to it.value }
        return summary(
            first = "Month",
            keys = overview.months.map { it.month },
            name = { Format.month(it) },
            keyOf = { YearMonth.from(it.time) },
            balance = { balances[it] },
            ledger = ledger,
        )
    }

    /** Each year: income, spending and what was left, then what was spent on each category and with each account. */
    fun years(years: List<Int>, ledger: Ledger): String = summary(
        first = "Year",
        keys = years,
        name = { it.toString() },
        keyOf = { it.time.year },
        balance = null,
        ledger = ledger,
    )

    /** One row per month or year, in the main currency like the reports. Transfers aren't income or spending. */
    @Suppress("LongParameterList")
    private fun <K> summary(
        first: String,
        keys: List<K>,
        name: (K) -> String,
        keyOf: (LedgerTransaction) -> K,
        balance: ((K) -> Double?)?,
        ledger: Ledger,
    ): String {
        val flows = ledger.transactions.filter {
            it.type != TransactionType.TRANSFER && it.currency == ledger.baseCurrency
        }
        val spending = flows.filter { it.type == TransactionType.EXPENSE }
        // columns for every category and account money went to, biggest first
        val categories = biggestFirst(spending) { categoryName(it, ledger).orEmpty() }
        val accounts = biggestFirst(spending) { it.accountName }
        val byKey = flows.groupBy(keyOf)
        return table(
            header = listOf(first, "Income", "Spending", "Saved", "Saved %") +
                listOfNotNull("Total balance at the end".takeIf { balance != null }) +
                listOf("Transactions", "Biggest category", "Top place") +
                categories.map { "Spent on $it" } +
                accounts.map { "Spent with $it" },
            rows = keys.map { key ->
                val list = byKey[key].orEmpty()
                val income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val spent = list.filter { it.type == TransactionType.EXPENSE }
                val total = spent.sumOf { it.amount }
                val byCategory = sums(spent) { categoryName(it, ledger).orEmpty() }
                val byAccount = sums(spent) { it.accountName }
                listOf(
                    name(key),
                    number(income),
                    number(total),
                    number(income - total),
                    if (income > 0) "${number((income - total) / income * PERCENT)}%" else "",
                ) +
                    listOfNotNull(balance?.let { it(key)?.let(::number).orEmpty() }) +
                    listOf(
                        list.size.toString(),
                        text(byCategory.maxByOrNull { it.value }?.key),
                        text(topPlace(spent)),
                    ) +
                    categories.map { number(byCategory[it] ?: 0.0) } +
                    accounts.map { number(byAccount[it] ?: 0.0) }
            },
        )
    }

    private fun sums(list: List<LedgerTransaction>, by: (LedgerTransaction) -> String): Map<String, Double> =
        list.groupBy(by).mapValues { (_, group) -> group.sumOf { it.amount } }

    private fun biggestFirst(list: List<LedgerTransaction>, by: (LedgerTransaction) -> String): List<String> =
        sums(list, by).entries.sortedByDescending { it.value }.map { it.key }

    /** The name spent at the most, in the spelling used most often. */
    private fun topPlace(spent: List<LedgerTransaction>): String? = spent
        .filter { !it.title.isNullOrBlank() }
        .groupBy { it.title.orEmpty().trim().lowercase() }
        .maxByOrNull { (_, group) -> group.sumOf { it.amount } }
        ?.value
        ?.groupingBy { it.title.orEmpty().trim() }
        ?.eachCount()
        ?.maxByOrNull { it.value }
        ?.key

    private fun categoryName(trn: LedgerTransaction, ledger: Ledger): String? = when (trn.type) {
        TransactionType.TRANSFER -> null
        else -> trn.categoryId?.let { ledger.categories[it]?.name } ?: Reports.UNCATEGORIZED_NAME
    }

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
