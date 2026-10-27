package com.ivy.desktop

import com.ivy.desktop.data.AccountId
import com.ivy.desktop.data.Backup
import com.ivy.desktop.data.BackupAccount
import com.ivy.desktop.data.BackupCategory
import com.ivy.desktop.data.BackupRule
import com.ivy.desktop.data.BackupSettings
import com.ivy.desktop.data.BackupTransaction
import com.ivy.desktop.data.CategoryId
import com.ivy.desktop.data.RuleId
import com.ivy.desktop.data.TransactionId
import com.ivy.desktop.data.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.random.Random

/** A made-up year and a bit of someone's money, for tests and screenshots. */
object SampleData {
    val today: LocalDate = LocalDate.of(2026, 10, 27)
    val zone: ZoneOffset = ZoneOffset.ofHours(4)

    private class Builder {
        val transactions = mutableListOf<BackupTransaction>()
        private var next = 0

        @Suppress("LongParameterList")
        fun add(
            type: TransactionType,
            amount: Double,
            date: LocalDate,
            title: String?,
            category: String?,
            account: String = "bank",
            toAccount: String? = null,
        ) {
            if (date.isAfter(today)) return
            transactions += BackupTransaction(
                id = TransactionId("t${next++}"),
                accountId = AccountId(account),
                type = type,
                amount = amount,
                toAccountId = toAccount?.let(::AccountId),
                toAmount = null,
                title = title,
                description = null,
                dateTime = date.atTime(12, 0).toInstant(zone).toEpochMilli(),
                categoryId = category?.let(::CategoryId),
                recurringRuleId = null,
                isDeleted = null,
            )
        }
    }

    @Suppress("LongMethod")
    fun backup(): Backup {
        val random = Random(SEED)
        val b = Builder()
        val first = YearMonth.from(today).minusMonths(14)
        b.add(TransactionType.INCOME, 24_000.0, first.atDay(1), "Adjust balance", null)
        b.add(TransactionType.INCOME, 600.0, first.atDay(1), "Adjust balance", null, account = "cash")
        (0..14L).map(first::plusMonths).forEach { month ->
            fun day(d: Int): LocalDate = month.atDay(d.coerceAtMost(month.lengthOfMonth()))
            fun pick(names: List<String>): String = names[random.nextInt(names.size)]
            fun amount(from: Int, to: Int): Double = random.nextInt(from * 100, to * 100) / 100.0
            val monthsAgo = month.until(YearMonth.from(today), java.time.temporal.ChronoUnit.MONTHS)

            b.add(TransactionType.INCOME, 18_500.0, day(25), "Salary", "salary")
            b.add(TransactionType.EXPENSE, 6_500.0, day(1), "Rent", "rent")
            b.add(TransactionType.EXPENSE, amount(420, 760), day(10), "DEWA", "bills")
            b.add(TransactionType.EXPENSE, 389.0, day(8), "du Home Internet", "bills")
            b.add(TransactionType.EXPENSE, 39.0, day(12), "Netflix", "fun", account = "card")
            b.add(
                TransactionType.EXPENSE,
                if (monthsAgo >= 3) 19.99 else 21.99,
                day(15),
                "Spotify",
                "fun",
                account = "card",
            )
            if (monthsAgo >= 5) b.add(TransactionType.EXPENSE, 250.0, day(3), "Fitness First", "health")
            repeat(8) {
                b.add(
                    TransactionType.EXPENSE,
                    amount(35, 460),
                    day(random.nextInt(1, 29)),
                    pick(listOf("Carrefour", "Lulu", "Spinneys", "Union Coop")),
                    "groceries",
                    account = if (random.nextBoolean()) "card" else "bank",
                )
            }
            repeat(6) {
                b.add(
                    TransactionType.EXPENSE,
                    amount(22, 190),
                    day(random.nextInt(1, 29)),
                    pick(listOf("Talabat", "Shake Shack", "Al Mallah", "Starbucks")),
                    "food",
                    account = "card",
                )
            }
            repeat(5) {
                b.add(
                    TransactionType.EXPENSE,
                    amount(12, 140),
                    day(random.nextInt(1, 29)),
                    pick(listOf("Careem", "ENOC", "Salik")),
                    "transport",
                    account = if (random.nextBoolean()) "cash" else "bank",
                )
            }
            repeat(2) {
                b.add(
                    TransactionType.EXPENSE,
                    amount(60, 900),
                    day(random.nextInt(1, 29)),
                    pick(listOf("Amazon", "Noon", "IKEA")),
                    "shopping",
                    account = "card",
                )
            }
            b.add(TransactionType.TRANSFER, 2_400.0, day(28), "Card payment", null, toAccount = "card")
        }

        return Backup(
            accounts = listOf(
                account("bank", "Bank", IVY, order = 0.0),
                account("cash", "Cash", GREEN, order = 1.0),
                account("card", "Credit card", RED, order = 2.0, creditLimit = 15_000.0, dueDay = 28),
            ),
            categories = listOf(
                "groceries" to "Groceries",
                "food" to "Restaurants",
                "transport" to "Transport",
                "bills" to "Bills",
                "rent" to "Rent",
                "shopping" to "Shopping",
                "fun" to "Entertainment",
                "health" to "Health",
                "salary" to "Salary",
            ).mapIndexed { i, (id, name) ->
                BackupCategory(CategoryId(id), name, CATEGORY_COLORS[i % CATEGORY_COLORS.size], i.toDouble(), null)
            },
            transactions = b.transactions,
            plannedPaymentRules = listOf(
                BackupRule(RuleId("rent"), "Rent", TransactionType.EXPENSE, oneTime = false, isDeleted = null),
            ),
            settings = listOf(BackupSettings("AED")),
        )
    }

    @Suppress("LongParameterList")
    private fun account(
        id: String,
        name: String,
        color: Int,
        order: Double,
        creditLimit: Double? = null,
        dueDay: Int? = null,
    ): BackupAccount = BackupAccount(
        id = AccountId(id),
        name = name,
        currency = null,
        color = color,
        orderNum = order,
        includeInBalance = null,
        accountType = creditLimit?.let { "CREDIT_CARD" },
        creditLimit = creditLimit,
        paymentDueDay = dueDay,
        isDeleted = null,
    )

    private const val SEED = 7
    private const val IVY = 0xFF6B4DFF.toInt()
    private const val GREEN = 0xFF14CC9E.toInt()
    private const val RED = 0xFFFF4060.toInt()
    private val CATEGORY_COLORS = listOf(
        0xFF14CC9E.toInt(),
        0xFFF29F30.toInt(),
        0xFF4CC3FF.toInt(),
        0xFF6B4DFF.toInt(),
        0xFF939199.toInt(),
        0xFFFF4060.toInt(),
        0xFFFFC34D.toInt(),
        0xFF2FBF71.toInt(),
        0xFF0A664F.toInt(),
    )
}
