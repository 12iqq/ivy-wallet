package com.ivy.assistant.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.data.model.Expense
import com.ivy.data.repository.AccountRepository
import com.ivy.data.temp.migration.getAccountId
import com.ivy.data.temp.migration.getValue
import com.ivy.domain.AppStarter
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.data.model.TimePeriod
import com.ivy.legacy.data.model.toCloseTimeRange
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.Budget
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import com.ivy.wallet.domain.action.budget.BudgetsAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.action.transaction.HistoryTrnsAct
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import com.ivy.wallet.domain.pure.transaction.trnCurrency
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the once-a-day background job does:
 * credit card due reminders, budget alerts and the automatic backup.
 * Each part is independent - one failing doesn't stop the others.
 */
@Suppress("LongParameterList")
@Singleton
class DailyAssistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AssistantSettingsStore,
    private val accountRepository: AccountRepository,
    private val calcAccBalanceAct: CalcAccBalanceAct,
    private val accountsAct: AccountsAct,
    private val budgetsAct: BudgetsAct,
    private val historyTrnsAct: HistoryTrnsAct,
    private val exchangeAct: ExchangeAct,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val ivyContext: IvyWalletCtx,
    private val sharedPrefs: SharedPrefs,
    private val timeConverter: TimeConverter,
    private val timeProvider: TimeProvider,
    private val backupDataUseCase: BackupDataUseCase,
    private val notificationService: NotificationService,
    private val appStarter: AppStarter,
) {

    suspend fun runDailyChecks() {
        val today = LocalDate.now(ZoneId.systemDefault())
        store.update { it.copy(sentKeys = ReminderRules.pruneKeys(it.sentKeys, today)) }
        val settings = store.current()

        if (settings.cardReminders) {
            runCatching { checkCards(today, settings) }.onFailure { Timber.e(it, "Card reminders failed") }
        }
        if (settings.budgetAlerts) {
            runCatching { checkBudgets() }.onFailure { Timber.e(it, "Budget alerts failed") }
        }
        if (ReminderRules.isBackupDue(
                now = Instant.now(),
                lastBackup = settings.lastBackupAtMillis?.let(Instant::ofEpochMilli),
                frequency = settings.backupFrequency,
            )
        ) {
            backupNow()
        }
    }

    /** @return true when the backup was written. */
    suspend fun backupNow(): Boolean {
        val uri = store.current().backupUri?.let(Uri::parse) ?: return false
        return try {
            backupDataUseCase.exportToFile(zipFileUri = uri)
            store.update {
                it.copy(lastBackupAtMillis = System.currentTimeMillis(), lastBackupError = null)
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Automatic backup failed")
            val firstFailure = store.current().lastBackupError == null
            store.update { it.copy(lastBackupError = e.message ?: e.javaClass.simpleName) }
            if (firstFailure) {
                notify(
                    id = BACKUP_NOTIFICATION_ID,
                    title = context.getString(R.string.backup_failed_title),
                    text = context.getString(R.string.backup_failed_text),
                )
            }
            false
        }
    }

    private suspend fun checkCards(today: LocalDate, settings: AssistantSettings) {
        accountRepository.findAll()
            .filter { it.isCreditCard }
            .forEach { account ->
                val balance = calcAccBalanceAct(CalcAccBalanceAct.Input(account = account)).balance
                val owed = -balance.toDouble()
                val reminder = ReminderRules.cardReminder(
                    accountId = account.id.value.toString(),
                    owed = owed,
                    details = account.creditCard,
                    today = today,
                    daysBefore = settings.reminderDaysBefore,
                    sentKeys = store.current().sentKeys,
                ) ?: return@forEach

                val currency = account.asset.code
                val amount = "${owed.format(currency)} $currency"
                val text = when (reminder.daysLeft) {
                    0 -> context.getString(R.string.card_due_today, amount)
                    1 -> context.getString(R.string.card_due_tomorrow, amount)
                    else -> context.getString(R.string.card_due_in_days, amount, reminder.daysLeft)
                }
                notify(
                    id = CARD_NOTIFICATION_BASE + Math.floorMod(account.id.value.hashCode(), NOTIFICATION_SPREAD),
                    title = account.name.value,
                    text = text,
                )
                store.update { it.copy(sentKeys = it.sentKeys + reminder.key) }
            }
    }

    private suspend fun checkBudgets() {
        val budgets = budgetsAct(Unit)
        if (budgets.isEmpty()) return

        val startDayOfMonth = ivyContext.initStartDayOfMonthInMemory(sharedPrefs = sharedPrefs)
        val range = TimePeriod.currentMonth(startDayOfMonth = startDayOfMonth)
            .toRange(startDateOfMonth = startDayOfMonth, timeConverter, timeProvider)
            .toCloseTimeRange()
        val periodStart = range.from.atZone(ZoneId.systemDefault()).toLocalDate()
        val transactions = historyTrnsAct(range)
        val accounts = accountsAct(Unit)
        val baseCurrency = baseCurrencyAct(Unit)

        budgets.forEach { budget ->
            val spent = spent(budget, transactions, accounts, baseCurrency)
            val alert = ReminderRules.budgetAlert(
                budgetId = budget.id.toString(),
                periodStart = periodStart,
                budgetAmount = budget.amount,
                spent = spent,
                sentKeys = store.current().sentKeys,
            ) ?: return@forEach

            val text = when (alert.level) {
                ReminderRules.BudgetLevel.Almost -> context.getString(
                    R.string.budget_almost_text,
                    spent.format(baseCurrency),
                    budget.amount.format(baseCurrency),
                    baseCurrency
                )

                ReminderRules.BudgetLevel.Over -> context.getString(
                    R.string.budget_over_text,
                    spent.format(baseCurrency),
                    budget.amount.format(baseCurrency),
                    baseCurrency
                )
            }
            notify(
                id = BUDGET_NOTIFICATION_BASE + Math.floorMod(budget.id.hashCode(), NOTIFICATION_SPREAD),
                title = budget.name,
                text = text,
            )
            store.update { it.copy(sentKeys = it.sentKeys + alert.coveredKeys) }
        }
    }

    /** Same rules as the Budgets screen: expenses only, filtered by the budget's accounts/categories. */
    private suspend fun spent(
        budget: Budget,
        transactions: List<com.ivy.data.model.Transaction>,
        accounts: List<Account>,
        baseCurrency: String,
    ): Double {
        val accountsFilter = budget.parseAccountIds()
        val categoryFilter = budget.parseCategoryIds()
        return transactions
            .filterIsInstance<Expense>()
            .filter { accountsFilter.isEmpty() || accountsFilter.contains(it.getAccountId()) }
            .filter { categoryFilter.isEmpty() || categoryFilter.contains(it.category?.value) }
            .sumOf { expense ->
                exchangeAct(
                    ExchangeAct.Input(
                        data = ExchangeData(
                            baseCurrency = baseCurrency,
                            fromCurrency = trnCurrency(expense, accounts, baseCurrency)
                        ),
                        amount = expense.getValue()
                    )
                ).orNull()?.toDouble() ?: 0.0
            }
    }

    private fun notify(id: Int, title: String, text: String) {
        val notification = notificationService
            .defaultIvyNotification(channel = IvyNotificationChannel.REMINDERS)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    id,
                    appStarter.getRootIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        notificationService.showNotification(notification, id)
    }

    private companion object {
        const val BACKUP_NOTIFICATION_ID = 5100
        const val CARD_NOTIFICATION_BASE = 5200
        const val BUDGET_NOTIFICATION_BASE = 5400
        const val NOTIFICATION_SPREAD = 100
    }
}
