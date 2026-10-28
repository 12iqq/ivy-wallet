package com.ivy.desktop.reports

import com.ivy.desktop.SampleData
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.data.LedgerTransaction
import com.ivy.desktop.data.TransactionId
import com.ivy.desktop.data.TransactionType
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvTest {

    private val ledger = LedgerBuilder.build(
        SampleData.backup(),
        SampleData.zone,
        SampleData.today.atTime(18, 0).toInstant(SampleData.zone),
    )

    @Test
    fun `transactions as rows Excel can read`() {
        val trn = LedgerTransaction(
            id = TransactionId("x"),
            type = TransactionType.EXPENSE,
            time = LocalDateTime.of(2026, 10, 3, 9, 5),
            title = "Dinner, with \"friends\"",
            description = "=SUM(A1)",
            accountName = "Bank",
            toAccountName = null,
            categoryId = ledger.categories.keys.first(),
            amount = 1234.5,
            currency = "AED",
        )

        val lines = Csv.transactions(listOf(trn), ledger).split("\r\n")

        // the mark that tells Excel it's UTF-8, then the header
        assertEquals("\uFEFFDate,Time,Type,Title,Category,Account,To account,Amount,Currency,Note", lines[0])
        val category = ledger.categories.values.first().name
        assertEquals(
            "2026-10-03,09:05,Expense,\"Dinner, with \"\"friends\"\"\",$category,Bank,,1234.50,AED,'=SUM(A1)",
            lines[1],
        )
        // every line ends with a line break, Windows style
        assertEquals("", lines.last())
    }

    @Test
    fun `the overview's months with income, spending and balance`() {
        val overview = Reports.overview(ledger, SampleData.today)

        val lines = Csv.months(overview).trimEnd().split("\r\n")

        assertEquals(13, lines.size)
        assertTrue(lines[0].endsWith("Month,Income,Spending,Saved,Total balance at the end of the month"))
        val october = overview.months.last()
        val parts = lines.last().split(",")
        assertEquals("October 2026", parts[0])
        assertEquals("%.2f".format(Locale.ENGLISH, october.income), parts[1])
        assertEquals("%.2f".format(Locale.ENGLISH, october.expense), parts[2])
    }

    @Test
    fun `recurring charges with how often and when next`() {
        val found = Reports.recurring(ledger, SampleData.today)

        val lines = Csv.recurring(found, ledger).trimEnd().split("\r\n")

        assertEquals(found.size + 1, lines.size)
        assertTrue(lines.drop(1).any { it.startsWith("Netflix,") && ",Monthly," in it })
    }
}
