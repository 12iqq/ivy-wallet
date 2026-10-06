package com.ivy.assistant.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs [DailyAssistant] once a day (around 9 AM). WorkManager batches it with other
 * jobs and only wakes the phone once, so it costs almost no battery.
 *
 * Dependencies come from a Hilt entry point, so the worker only needs the default
 * (Context, WorkerParameters) constructor.
 */
class DailyAssistantWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun dailyAssistant(): DailyAssistant
    }

    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            .dailyAssistant()
            .runDailyChecks()
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "Daily assistant failed")
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "daily_assistant"
        private const val RUN_AT_HOUR = 9

        /** Safe to call on every app start: an existing schedule is kept. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var nextRun = now.toLocalDate().atTime(RUN_AT_HOUR, 0)
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)

            val request = PeriodicWorkRequestBuilder<DailyAssistantWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
