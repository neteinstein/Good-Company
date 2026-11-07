package com.neteinstein.goodcompany.core.data.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.WeeklyIdeasOutcome
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Background trigger for the weekly AI suggestions. Fires daily; [RunWeeklyIdeasUseCase] itself
 * enforces the 7-day cadence, the opt-ins and the API-key requirement.
 */
class WeeklyIdeasWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val runWeeklyIdeas: RunWeeklyIdeasUseCase by inject()

    override suspend fun doWork(): Result = when (runWeeklyIdeas()) {
        is WeeklyIdeasOutcome.Failed -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        else -> Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "weekly-ideas"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyIdeasWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
