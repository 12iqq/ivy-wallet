package com.ivy.desktop.data

import com.ivy.insights.BalanceDelta
import com.ivy.insights.FlowKind
import com.ivy.insights.FlowRecord
import com.ivy.insights.recurring.Charge
import com.ivy.insights.recurring.RecurringCharges
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// The IDs are the typed ones from Backup.kt, but DataClassTypedIDs only knows value classes from the same file
@Suppress("DataClassTypedIDs")
data class LedgerAccount(
    val id: AccountId,
    val name: String,
    val currency: String,
    val color: Int?,
    /** Settled transactions up to now, like the phone shows it */
    val balance: Double,
    val includeInBalance: Boolean,
    val creditCard: Boolean,
    val creditLimit: Double?,
    val paymentDueDay: Int?,
)

@Suppress("DataClassTypedIDs")
data class LedgerCategory(
    val id: CategoryId,
    val name: String,
    val color: Int?,
)

/** A paid transaction, as the lists show it. */
@Suppress("DataClassTypedIDs")
data class LedgerTransaction(
    val id: TransactionId,
    val type: TransactionType,
    val time: LocalDateTime,
    val title: String?,
    val description: String?,
    /** The shop as the bank's SMS named it */
    val bankDetails: String?,
    val accountName: String,
    /** Paid with a credit card */
    val byCard: Boolean,
    /** Transfers: where the money went */
    val toAccountName: String?,
    val categoryId: CategoryId?,
    val tags: List<String>,
    /** Paid as a planned payment */
    val planned: Boolean,
    /** In [currency], the account's currency */
    val amount: Double,
    val currency: String,
)

/** What the desktop app shows, built once from a backup. */
data class Ledger(
    val baseCurrency: String,
    /** The day of the first transaction; null without any */
    val since: LocalDate?,
    /** In the phone's order */
    val accounts: List<LedgerAccount>,
    val categories: Map<CategoryId, LedgerCategory>,
    /** Newest first */
    val transactions: List<LedgerTransaction>,
    /** Incomes and expenses in the base currency, for the phone's report maths */
    val records: List<FlowRecord>,
    /** Changes to the total balance, for the balance over time */
    val balanceDeltas: List<BalanceDelta>,
    /** Expenses up to now, for recurring charges */
    val charges: List<Charge>,
    /** Names of the recurring planned payments, so recurring charges know which are planned */
    val plannedNames: Set<String>,
    /**
     * Currencies of accounts that aren't in the base currency. Without exchange rates their amounts
     * can't be added up, so the reports leave them out.
     */
    val otherCurrencies: Set<String>,
)

object LedgerBuilder {
    private const val CREDIT_CARD = "CREDIT_CARD"
    private const val DEFAULT_CURRENCY = "AED"

    fun build(backup: Backup, zone: ZoneId, now: Instant): Ledger {
        val baseCurrency = backup.settings?.firstOrNull()?.currency ?: DEFAULT_CURRENCY
        val accounts = backup.accounts.orEmpty().filter { it.isDeleted != true }.sortedBy { it.orderNum ?: 0.0 }
        val byId = accounts.associateBy { it.id }
        val settled = backup.transactions.orEmpty()
            .filter { it.isDeleted != true && it.dateTime != null && it.accountId in byId }
            .sortedByDescending { it.dateTime }
        val flows = Flows(byId, baseCurrency, zone, now)
        settled.forEach(flows::add)
        val tags = tagsOf(backup)

        return Ledger(
            baseCurrency = baseCurrency,
            since = settled.lastOrNull()?.let { LocalDateTime.ofInstant(it.instant(), zone).toLocalDate() },
            accounts = accounts.map { account ->
                LedgerAccount(
                    id = account.id,
                    name = account.name,
                    currency = account.currency ?: baseCurrency,
                    color = account.color,
                    balance = flows.balances[account.id] ?: 0.0,
                    includeInBalance = account.includeInBalance != false,
                    creditCard = account.accountType == CREDIT_CARD,
                    creditLimit = account.creditLimit,
                    paymentDueDay = account.paymentDueDay,
                )
            },
            categories = backup.categories.orEmpty()
                .filter { it.isDeleted != true }
                .sortedBy { it.orderNum ?: 0.0 }
                .associate { it.id to LedgerCategory(it.id, it.name, it.color) },
            transactions = settled.map { trn ->
                LedgerTransaction(
                    id = trn.id,
                    type = trn.type,
                    time = LocalDateTime.ofInstant(trn.instant(), zone),
                    title = trn.title,
                    description = trn.description,
                    bankDetails = trn.bankDetails,
                    accountName = byId[trn.accountId]?.name.orEmpty(),
                    byCard = byId[trn.accountId]?.accountType == CREDIT_CARD,
                    toAccountName = trn.toAccountId?.let { byId[it]?.name },
                    categoryId = trn.categoryId,
                    tags = tags[trn.id].orEmpty(),
                    planned = trn.recurringRuleId != null,
                    amount = trn.amount,
                    currency = byId[trn.accountId]?.currency ?: baseCurrency,
                )
            },
            records = flows.records,
            balanceDeltas = flows.deltas,
            charges = flows.charges,
            plannedNames = plannedNames(backup.plannedPaymentRules.orEmpty()),
            otherCurrencies = accounts.mapNotNull { it.currency }.filter { it != baseCurrency }.toSet(),
        )
    }

    /** The names of each transaction's tags */
    private fun tagsOf(backup: Backup): Map<TransactionId, List<String>> {
        val names = backup.tags.orEmpty().filter { it.isDeleted != true }.associate { it.id to it.name }
        return backup.tagAssociations.orEmpty()
            .filter { it.isDeleted != true }
            .groupBy({ it.associatedId }, { names[it.tagId] })
            .mapValues { (_, tags) -> tags.filterNotNull().sorted() }
    }

    private fun plannedNames(rules: List<BackupRule>): Set<String> = rules
        .filter { it.isDeleted != true && it.oneTime != true && it.type == TransactionType.EXPENSE }
        .mapNotNull { rule -> rule.title?.takeIf { it.isNotBlank() } }
        .map(RecurringCharges::key)
        .toSet()
}

private fun BackupTransaction.instant(): Instant = Instant.ofEpochMilli(requireNotNull(dateTime))

/** Walks the settled transactions once: balances, report records, total balance changes, charges. */
private class Flows(
    private val accounts: Map<AccountId, BackupAccount>,
    private val baseCurrency: String,
    private val zone: ZoneId,
    private val now: Instant,
) {
    val balances = mutableMapOf<AccountId, Double>()
    val records = mutableListOf<FlowRecord>()
    val deltas = mutableListOf<BalanceDelta>()
    val charges = mutableListOf<Charge>()

    fun add(trn: BackupTransaction) {
        val instant = trn.instant()
        val date = instant.atZone(zone).toLocalDate()
        // like the phone: a transaction dated in the future doesn't count for balances yet
        val happened = !instant.isAfter(now)
        when (trn.type) {
            TransactionType.INCOME -> {
                if (inBase(trn.accountId)) records += record(trn, FlowKind.Income, date, instant)
                if (happened) move(trn.accountId, trn.amount, date)
            }

            TransactionType.EXPENSE -> {
                if (inBase(trn.accountId)) {
                    records += record(trn, FlowKind.Expense, date, instant)
                    if (happened) charge(trn, date)
                }
                if (happened) move(trn.accountId, -trn.amount, date)
            }

            TransactionType.TRANSFER -> if (happened) {
                move(trn.accountId, -trn.amount, date)
                trn.toAccountId?.takeIf { it in accounts }?.let { move(it, trn.toAmount ?: trn.amount, date) }
            }
        }
    }

    private fun inBase(id: AccountId): Boolean = (accounts[id]?.currency ?: baseCurrency) == baseCurrency

    private fun move(id: AccountId, amount: Double, date: LocalDate) {
        balances.merge(id, amount, Double::plus)
        // the total balance only has accounts that count towards it, and only what's in the base currency
        if (accounts[id]?.includeInBalance != false && inBase(id)) deltas += BalanceDelta(date, amount)
    }

    private fun record(trn: BackupTransaction, kind: FlowKind, date: LocalDate, instant: Instant): FlowRecord =
        FlowRecord(
            date = date,
            kind = kind,
            amount = trn.amount,
            categoryId = trn.categoryId?.value,
            title = trn.title,
            id = trn.id.value,
            epochMillis = instant.toEpochMilli(),
        )

    private fun charge(trn: BackupTransaction, date: LocalDate) {
        val name = trn.title?.takeIf { it.isNotBlank() } ?: return
        charges += Charge(
            name = name,
            amount = trn.amount,
            accountAmount = trn.amount,
            date = date,
            categoryId = trn.categoryId?.value,
            accountId = trn.accountId.value,
            planned = trn.recurringRuleId != null,
        )
    }
}
