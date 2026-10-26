package com.ivy.insights.recurring

import arrow.core.Some
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** The recurring charges found in your expenses, amounts in [currency] (the base currency). */
data class RecurringData(
    val currency: String,
    val charges: List<RecurringCharge>,
)

/** Reads your expenses and finds the [RecurringCharges] in them, without the ones you hid. */
class RecurringChargesLoader @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val ruleDao: PlannedPaymentRuleDao,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val exchangeAct: ExchangeAct,
    private val sharedPrefs: SharedPrefs,
) {
    suspend fun load(today: LocalDate = LocalDate.now()): RecurringData {
        val currency = baseCurrencyAct(Unit)
        val accountCurrencies = accountDao.findAll().associate { it.id to (it.currency ?: currency) }
        val plannedNames = ruleDao.findAll()
            .filter { !it.oneTime && it.type == TransactionType.EXPENSE }
            .mapNotNull { rule -> rule.title?.takeIf { it.isNotBlank() }?.let(RecurringCharges::key) }
            .toSet()
        val hidden = hidden()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        // one exchange rate per currency instead of one lookup per transaction
        val rates = mutableMapOf<String, Double>()
        val charges = transactionDao.findAllByType(TransactionType.EXPENSE).mapNotNull { trn ->
            val time = trn.dateTime?.takeUnless { it.isAfter(now) } ?: return@mapNotNull null
            val name = trn.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val accountCurrency = accountCurrencies[trn.accountId] ?: currency
            val rate = if (accountCurrency == currency) {
                1.0
            } else {
                rates.getOrPut(accountCurrency) { rate(currency, accountCurrency) }
            }
            Charge(
                name = name,
                amount = trn.amount * rate,
                accountAmount = trn.amount,
                date = time.atZone(zone).toLocalDate(),
                categoryId = trn.categoryId?.toString(),
                accountId = trn.accountId.toString(),
                planned = trn.recurringRuleId != null,
            )
        }
        val found = withContext(Dispatchers.Default) { RecurringCharges.find(charges, today, plannedNames) }
        return RecurringData(currency = currency, charges = found.filter { it.key !in hidden })
    }

    /** "Not recurring": hidden from now on. */
    fun hide(key: String) {
        sharedPrefs.putString(SharedPrefs.HIDDEN_RECURRING_CHARGES, (hidden() + key).sorted().joinToString("\n"))
    }

    private fun hidden(): Set<String> = sharedPrefs.getString(SharedPrefs.HIDDEN_RECURRING_CHARGES, null)
        ?.split('\n')
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()

    private suspend fun rate(baseCurrency: String, from: String): Double = exchangeAct(
        ExchangeAct.Input(
            data = ExchangeData(baseCurrency = baseCurrency, fromCurrency = Some(from)),
            amount = BigDecimal.ONE
        )
    ).getOrNull()?.toDouble() ?: 0.0
}
