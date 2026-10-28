package com.ivy.desktop.data

import com.ivy.insights.FlowKind
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals

class LedgerTest {

    private val zone: ZoneId = ZoneOffset.ofHours(4)
    private val now: Instant = at(2026, 10, 27)

    @Test
    fun `balances count income, spending and transfers up to now`() {
        val ledger = ledger(
            transactions = listOf(
                trn("t1", TransactionType.INCOME, 5000.0, at(2026, 10, 1)),
                trn("t2", TransactionType.EXPENSE, 120.0, at(2026, 10, 5)),
                trn("t3", TransactionType.TRANSFER, 1000.0, at(2026, 10, 10), toAccount = "card"),
                // dated in the future: not in the balance yet
                trn("t4", TransactionType.EXPENSE, 50.0, at(2026, 11, 2)),
                // a planned payment that isn't paid yet
                trn("t5", TransactionType.EXPENSE, 75.0, null),
                trn("t6", TransactionType.EXPENSE, 9.0, at(2026, 10, 6), deleted = true),
            )
        )

        val balances = ledger.accounts.associate { it.id.value to it.balance }
        assertEquals(3880.0, balances["bank"])
        assertEquals(1000.0, balances["card"])
        assertEquals(listOf("t4", "t3", "t2", "t1"), ledger.transactions.map { it.id.value })
        // reports count the future-dated one, like the phone
        assertEquals(
            listOf(FlowKind.Income, FlowKind.Expense, FlowKind.Expense),
            ledger.records.map { it.kind }.sorted(),
        )
        assertEquals(LocalDate.of(2026, 10, 10), ledger.balanceDeltas.first { it.amount == -1000.0 }.date)
    }

    @Test
    fun `a transfer can arrive as a different amount`() {
        val ledger = ledger(
            transactions = listOf(
                trn("t1", TransactionType.TRANSFER, 100.0, at(2026, 10, 3), toAccount = "card", toAmount = 99.5),
            )
        )
        assertEquals(99.5, ledger.accounts.single { it.id.value == "card" }.balance)
    }

    @Test
    fun `an account in another currency stays out of reports and the total`() {
        val ledger = ledger(
            accounts = listOf(account("bank"), account("usd", currency = "USD")),
            transactions = listOf(
                trn("t1", TransactionType.EXPENSE, 10.0, at(2026, 10, 3)),
                trn("t2", TransactionType.EXPENSE, 20.0, at(2026, 10, 3), account = "usd"),
            )
        )

        assertEquals(setOf("USD"), ledger.otherCurrencies)
        assertEquals(listOf(10.0), ledger.records.map { it.amount })
        assertEquals(listOf(-10.0), ledger.balanceDeltas.map { it.amount })
        assertEquals(-20.0, ledger.accounts.single { it.id.value == "usd" }.balance)
    }

    @Test
    fun `recurring charges get past expenses with a name, and the planned payments' names`() {
        val ledger = ledger(
            transactions = listOf(
                trn("t1", TransactionType.EXPENSE, 39.0, at(2026, 10, 12), title = "Streaming"),
                trn("t2", TransactionType.EXPENSE, 12.0, at(2026, 10, 13)),
                trn("t3", TransactionType.EXPENSE, 39.0, at(2026, 11, 12), title = "Streaming"),
            ),
            rules = listOf(
                BackupRule(RuleId("r1"), "  Rent ", TransactionType.EXPENSE, oneTime = false, isDeleted = null),
                BackupRule(RuleId("r2"), "Laptop", TransactionType.EXPENSE, oneTime = true, isDeleted = null),
            ),
        )

        assertEquals(listOf("Streaming"), ledger.charges.map { it.name })
        assertEquals(setOf("rent"), ledger.plannedNames)
    }

    private fun ledger(
        accounts: List<BackupAccount> = listOf(account("bank"), account("card")),
        transactions: List<BackupTransaction>,
        rules: List<BackupRule> = emptyList(),
    ): Ledger = LedgerBuilder.build(
        Backup(
            accounts = accounts,
            categories = emptyList(),
            transactions = transactions,
            plannedPaymentRules = rules,
            settings = listOf(BackupSettings("AED")),
            tags = null,
            tagAssociations = null,
        ),
        zone,
        now,
    )

    private fun account(id: String, currency: String? = null): BackupAccount = BackupAccount(
        id = AccountId(id),
        name = id,
        currency = currency,
        color = null,
        orderNum = null,
        includeInBalance = null,
        accountType = null,
        creditLimit = null,
        paymentDueDay = null,
        isDeleted = null,
    )

    @Suppress("LongParameterList")
    private fun trn(
        id: String,
        type: TransactionType,
        amount: Double,
        time: Instant?,
        account: String = "bank",
        toAccount: String? = null,
        toAmount: Double? = null,
        title: String? = null,
        deleted: Boolean = false,
    ): BackupTransaction = BackupTransaction(
        id = TransactionId(id),
        accountId = AccountId(account),
        type = type,
        amount = amount,
        toAccountId = toAccount?.let(::AccountId),
        toAmount = toAmount,
        title = title,
        description = null,
        bankDetails = null,
        dateTime = time?.toEpochMilli(),
        categoryId = null,
        recurringRuleId = null,
        isDeleted = deleted,
    )

    private fun at(year: Int, month: Int, day: Int): Instant =
        LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant()
}
