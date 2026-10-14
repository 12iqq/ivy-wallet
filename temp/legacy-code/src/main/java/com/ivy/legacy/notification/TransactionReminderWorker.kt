package com.ivy.wallet.domain.deprecated.logic.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.stringRes
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.domain.AppStarter
import com.ivy.legacy.utils.atEndOfDay
import com.ivy.ui.R
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Daily "don't forget to log today's transactions" reminder.
 *
 * Dependencies come from a Hilt entry point: the project doesn't run the androidx
 * hilt-compiler, so an @HiltWorker could never be created (the reminder silently failed).
 */
class TransactionReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun transactionDao(): TransactionDao
        fun notificationService(): NotificationService
        fun sharedPrefs(): SharedPrefs
        fun appStarter(): AppStarter
        fun timeProvider(): TimeProvider
        fun timeConverter(): TimeConverter
    }

    private val deps: Deps by lazy {
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
    }
    private val transactionDao: TransactionDao get() = deps.transactionDao()
    private val notificationService: NotificationService get() = deps.notificationService()
    private val sharedPrefs: SharedPrefs get() = deps.sharedPrefs()
    private val appStarter: AppStarter get() = deps.appStarter()
    private val timeProvider: TimeProvider get() = deps.timeProvider()
    private val timeConverter: TimeConverter get() = deps.timeConverter()

    companion object {
        const val MINIMUM_TRANSACTIONS_PER_DAY = 1
    }

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val transactionsToday = with(timeConverter) {
            transactionDao.findAllBetween(
                startDate = timeProvider.localDateNow().atStartOfDay().toUTC(),
                endDate = timeProvider.localDateNow().atEndOfDay().toUTC(),
            )
        }

        val showNotifications = fetchShowNotifications()

        // Double check is needed because the user can switch off notifications in settings after it has been scheduled to show notifications for the next day
        if (transactionsToday.size < MINIMUM_TRANSACTIONS_PER_DAY && showNotifications) {
            // Have less than 1 two transactions today, remind them

            val notification = notificationService
                .defaultIvyNotification(
                    channel = IvyNotificationChannel.TRANSACTION_REMINDER,
                    priority = NotificationCompat.PRIORITY_HIGH
                )
                .setContentTitle(applicationContext.getString(R.string.app_display_name))
                .setContentText(randomText())
                .setContentIntent(
                    PendingIntent.getActivity(
                        applicationContext,
                        1,
                        appStarter.getRootIntent(),
                        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_UPDATE_CURRENT
                                or PendingIntent.FLAG_IMMUTABLE
                    )
                )

            notificationService.showNotification(notification, 1)
        }

        return@withContext Result.success()
    }

    private fun randomText(): String =
        listOf(
            stringRes(R.string.notification_1),
            stringRes(R.string.notification_2),
            stringRes(R.string.notification_3),
        ).shuffled().first()

    private fun fetchShowNotifications(): Boolean =
        sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)
}
