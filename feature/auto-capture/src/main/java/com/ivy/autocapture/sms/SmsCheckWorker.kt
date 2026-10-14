package com.ivy.autocapture.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivy.autocapture.data.SmsCheck
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Reads new bank SMS from the inbox every few hours. Used instead of [BankSmsReceiver]
 * when the user chose to check on a schedule: nothing of the app runs in between.
 */
class SmsCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun smsInboxScanner(): SmsInboxScanner
    }

    override suspend fun doWork(): Result {
        try {
            EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
                .smsInboxScanner()
                .checkNew()
        } catch (e: Exception) {
            // the next scheduled check picks up from the same point
            Timber.e(e, "Scheduled SMS check failed")
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "auto_add_sms_check"

        fun schedule(context: Context, every: SmsCheck) {
            val request = PeriodicWorkRequestBuilder<SmsCheckWorker>(every.hours.toLong(), TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
