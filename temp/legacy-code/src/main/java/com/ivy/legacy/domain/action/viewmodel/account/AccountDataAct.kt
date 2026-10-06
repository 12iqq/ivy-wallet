package com.ivy.wallet.domain.action.viewmodel.account

import arrow.core.toOption
import com.ivy.data.model.CreditCardCycle
import com.ivy.frp.action.FPAction
import com.ivy.frp.action.thenMap
import com.ivy.frp.then
import com.ivy.legacy.datamodel.Account
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import com.ivy.wallet.domain.action.account.CalcAccIncomeExpenseAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class AccountDataAct @Inject constructor(
    private val exchangeAct: ExchangeAct,
    private val calcAccBalanceAct: CalcAccBalanceAct,
    private val calcAccIncomeExpenseAct: CalcAccIncomeExpenseAct
) : FPAction<AccountDataAct.Input, ImmutableList<com.ivy.legacy.data.model.AccountData>>() {

    override suspend fun Input.compose(): suspend () -> ImmutableList<com.ivy.legacy.data.model.AccountData> = suspend {
        accounts
    } thenMap { acc ->
        val balance = calcAccBalanceAct(
            CalcAccBalanceAct.Input(
                account = acc
            )
        ).balance

        val balanceBaseCurrency = if (acc.asset.code != baseCurrency) {
            exchangeAct(
                ExchangeAct.Input(
                    data = ExchangeData(
                        baseCurrency = baseCurrency,
                        fromCurrency = acc.asset.code.toOption()
                    ),
                    amount = balance
                )
            ).orNull()
        } else {
            null
        }

        val incomeExpensePair = calcAccIncomeExpenseAct(
            CalcAccIncomeExpenseAct.Input(
                account = acc,
                // credit cards show the current statement cycle instead of the month
                range = acc.creditCard?.statementDay?.let { statementCycleRange(it) } ?: range,
                includeTransfersInCalc = includeTransfersInCalc
            )
        ).incomeExpensePair

        com.ivy.legacy.data.model.AccountData(
            account = acc,
            balance = balance.toDouble(),
            balanceBaseCurrency = balanceBaseCurrency?.toDouble(),
            monthlyIncome = incomeExpensePair.income.toDouble(),
            monthlyExpenses = incomeExpensePair.expense.toDouble(),
        )
    } then {
        it.toImmutableList()
    }

    private fun statementCycleRange(statementDay: Int): ClosedTimeRange {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = CreditCardCycle.cycleStart(today, statementDay)
        return ClosedTimeRange(
            from = start.atStartOfDay(zone).toInstant(),
            to = today.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }

    data class Input(
        val accounts: ImmutableList<com.ivy.data.model.Account>,
        val baseCurrency: String,
        val range: ClosedTimeRange,
        val includeTransfersInCalc: Boolean = false
    )
}
