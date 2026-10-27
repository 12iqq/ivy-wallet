package com.ivy.desktop.reports

import com.ivy.desktop.data.AccountId
import com.ivy.desktop.data.Backup
import com.ivy.desktop.data.BackupAccount
import com.ivy.desktop.data.BackupCategory
import com.ivy.desktop.data.BackupSettings
import com.ivy.desktop.data.BackupTransaction
import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.Ledger
import com.ivy.desktop.data.LedgerBuilder
import com.ivy.desktop.data.TransactionId
import com.ivy.desktop.data.TransactionType
import com.ivy.insights.FlowKind
import com.ivy.insights.PeriodMode
import com.ivy.insights.ReportsCalculator
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.assertEquals

class ReportsTest {

    private val today = LocalDate.of(2026, 10, 27)
    private val october = ReportChoice(PeriodMode.Month, YearMonth.of(2026, 10), FlowKind.Expense, null)

    private val ledger: Ledger = LedgerBuilder.build(
        Backup(
            accounts = listOf(
                account("bank", creditCard = false),
                account("card", creditCard = true, limit = 5000.0, dueDay = 31),
            ),
            categories = listOf(
                BackupCategory(CategoryId("food"), "Food", -65536, 1.0, null),
                BackupCategory(CategoryId("bills"), "Bills", -16776961, 2.0, null),
            ),
            transactions = listOf(
                trn("t1", TransactionType.EXPENSE, 100.0, LocalDate.of(2026, 10, 2), "food", "Bakery"),
                trn("t2", TransactionType.EXPENSE, 300.0, LocalDate.of(2026, 10, 5), "bills", "Internet"),
                trn("t3", TransactionType.EXPENSE, 50.0, LocalDate.of(2026, 10, 9), "food", "Bakery "),
                trn("t4", TransactionType.EXPENSE, 25.0, LocalDate.of(2026, 10, 20), null, "Parking", "card"),
                trn("t5", TransactionType.EXPENSE, 200.0, LocalDate.of(2026, 9, 14), "food", "Bakery"),
                trn("t6", TransactionType.INCOME, 9000.0, LocalDate.of(2026, 10, 1), null, "Salary"),
            ),
            plannedPaymentRules = emptyList(),
            settings = listOf(BackupSettings("AED")),
        ),
        ZoneOffset.ofHours(4),
        today.atTime(18, 0).toInstant(ZoneOffset.ofHours(4)),
    )

    @Test
    fun `a month's spending by category, place and day`() {
        val report = Reports.report(ledger, october, today)

        assertEquals(475.0, report.summary.total)
        assertEquals(200.0, report.summary.previousTotal)
        assertEquals(4, report.summary.count)
        assertEquals(listOf("Bills", "Food", Reports.UNCATEGORIZED_NAME), report.categories.map { it.name })
        assertEquals(listOf(300.0, 150.0, 25.0), report.categories.map { it.amount })
        assertEquals(ReportsCalculator.UNCATEGORIZED, report.categories.last().key)
        assertEquals(listOf("Internet", "Bakery", "Parking"), report.places.map { it.title })
        assertEquals(listOf("t4", "t3", "t2", "t1"), report.transactions.map { it.id.value })
        val pace = report.chart as MonthPace
        assertEquals(27, pace.current.size)
        assertEquals(475.0, pace.current.last())
        assertEquals(200.0, pace.previous.last())
    }

    @Test
    fun `one category, and a whole year`() {
        val food = Reports.report(ledger, october.copy(categoryKey = "food"), today)
        assertEquals(150.0, food.summary.total)
        assertEquals(listOf("t3", "t1"), food.transactions.map { it.id.value })

        val year = Reports.report(ledger, october.copy(mode = PeriodMode.Year), today)
        assertEquals(675.0, year.summary.total)
        assertEquals(200.0, (year.chart as YearMonths).totals[8])
        assertEquals("2026", Format.period(year.period, PeriodMode.Year))
        assertEquals("October 2026", Format.period(Reports.period(october), PeriodMode.Month))
        assertEquals(YearMonth.of(2026, 9), Reports.step(october, -1).month)
    }

    @Test
    fun `overview has balances, cards and the last months`() {
        val overview = Reports.overview(ledger, today)

        assertEquals(9000.0 - 100 - 300 - 50 - 200 - 25, overview.totalBalance)
        assertEquals(listOf("bank"), overview.accounts.map { it.id.value })
        val card = overview.cards.single()
        assertEquals(25.0, card.owed)
        assertEquals(0.005, card.used)
        assertEquals(LocalDate.of(2026, 10, 31), card.nextDue)
        assertEquals(12, overview.months.size)
        assertEquals(475.0, overview.months.last().expense)
    }

    @Test
    fun `search matches name, category and account`() {
        assertEquals(listOf("t3", "t1", "t5"), Reports.search(ledger, "bakery").map { it.id.value })
        assertEquals(listOf("t2"), Reports.search(ledger, "bills").map { it.id.value })
        assertEquals(listOf("t4"), Reports.search(ledger, "card park").map { it.id.value })
        assertEquals(6, Reports.search(ledger, "  ").size)
    }

    @Test
    fun `next due date`() {
        assertEquals(LocalDate.of(2026, 10, 27), Reports.nextDue(27, today))
        assertEquals(LocalDate.of(2026, 11, 5), Reports.nextDue(5, today))
        assertEquals(LocalDate.of(2027, 2, 28), Reports.nextDue(31, LocalDate.of(2027, 2, 1)))
    }

    @Test
    fun `money and dates like the phone`() {
        assertEquals("1,234.50 AED", Format.money(1234.5, "AED"))
        assertEquals("42%", Format.percent(0.4196))
        assertEquals("27 Oct", Format.day(today, today))
        assertEquals("3 Jan 2025", Format.day(LocalDate.of(2025, 1, 3), today))
    }

    private fun account(id: String, creditCard: Boolean, limit: Double? = null, dueDay: Int? = null): BackupAccount =
        BackupAccount(
            id = AccountId(id),
            name = id,
            currency = null,
            color = null,
            orderNum = null,
            includeInBalance = null,
            accountType = if (creditCard) "CREDIT_CARD" else null,
            creditLimit = limit,
            paymentDueDay = dueDay,
            isDeleted = null,
        )

    @Suppress("LongParameterList")
    private fun trn(
        id: String,
        type: TransactionType,
        amount: Double,
        date: LocalDate,
        category: String?,
        title: String,
        account: String = "bank",
    ): BackupTransaction = BackupTransaction(
        id = TransactionId(id),
        accountId = AccountId(account),
        type = type,
        amount = amount,
        toAccountId = null,
        toAmount = null,
        title = title,
        description = null,
        dateTime = date.atTime(9, 0).toInstant(ZoneOffset.ofHours(4)).toEpochMilli(),
        categoryId = category?.let(::CategoryId),
        recurringRuleId = null,
        isDeleted = null,
    )
}
