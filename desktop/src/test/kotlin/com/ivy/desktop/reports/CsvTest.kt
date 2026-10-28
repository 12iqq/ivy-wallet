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
    fun `transactions as rows Excel can read, with everything about each one`() {
        val trn = LedgerTransaction(
            id = TransactionId("x"),
            type = TransactionType.EXPENSE,
            time = LocalDateTime.of(2026, 10, 3, 9, 5),
            title = "Dinner, with \"friends\"",
            description = "=SUM(A1)",
            bankDetails = "DINNER PLACE DXB",
            accountName = "Bank",
            byCard = true,
            toAccountName = null,
            categoryId = ledger.categories.keys.first(),
            tags = listOf("Friends", "Weekend"),
            planned = false,
            amount = 1234.5,
            currency = "AED",
        )

        val lines = Csv.transactions(listOf(trn), ledger).split("\r\n")

        // the mark that tells Excel it's UTF-8, then the header
        assertEquals(
            "\uFEFFDate,Time,Weekday,Month,Type,Title,Category,Amount,Money in or out,Currency,Account," +
                "Account type,To account,Bank details,Tags,Planned payment,Note",
            lines[0],
        )
        val category = ledger.categories.values.first().name
        assertEquals(
            "2026-10-03,09:05,Saturday,2026-10,Expense,\"Dinner, with \"\"friends\"\"\",$category,1234.50," +
                "-1234.50,AED,Bank,Credit card,,DINNER PLACE DXB,Friends; Weekend,No,'=SUM(A1)",
            lines[1],
        )
        // every line ends with a line break, Windows style
        assertEquals("", lines.last())
    }

    @Test
    fun `tags and bank details come from the backup`() {
        val enoc = ledger.transactions.first { it.title == "ENOC" }
        val onCard = ledger.transactions.first { it.byCard && it.type == TransactionType.EXPENSE }

        assertEquals(listOf("Work"), enoc.tags)
        assertEquals(onCard.title?.uppercase() + " DUBAI AE", onCard.bankDetails)
    }

    @Test
    fun `each month with its totals, then each category and account`() {
        val overview = Reports.overview(ledger, SampleData.today, allTime = false)

        val lines = Csv.months(overview, ledger).trimEnd().split("\r\n")

        assertEquals(13, lines.size)
        val header = lines[0].split(",")
        assertEquals(
            listOf(
                "Month", "Income", "Spending", "Saved", "Saved %", "Total balance at the end", "Transactions",
                "Biggest category", "Top place",
            ),
            header.take(9).map { it.removePrefix("\uFEFF") },
        )
        assertEquals("Spent on Rent", header[9])
        assertTrue("Spent with Bank" in header)
        val october = overview.months.last()
        val row = lines.last().split(",")
        assertEquals("October 2026", row[0])
        assertEquals("%.2f".format(Locale.ENGLISH, october.income), row[1])
        assertEquals("%.2f".format(Locale.ENGLISH, october.expense), row[2])
        assertEquals("Rent", row[7])
        // what was spent on each category adds up to the month's spending
        val categories = header.indices.filter { header[it].startsWith("Spent on ") }
        assertEquals(october.expense, categories.sumOf { row[it].toDouble() }, 0.01)
    }

    @Test
    fun `each year`() {
        val lines = Csv.years(listOf(2025, 2026), ledger).trimEnd().split("\r\n")

        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("2025,"))
        assertTrue(lines[2].startsWith("2026,"))
    }

    @Test
    fun `recurring charges with how often and when next`() {
        val found = Reports.recurring(ledger, SampleData.today)

        val lines = Csv.recurring(found, ledger).trimEnd().split("\r\n")

        assertEquals(found.size + 1, lines.size)
        assertTrue(lines.drop(1).any { it.startsWith("Netflix,") && ",Monthly," in it })
    }
}
