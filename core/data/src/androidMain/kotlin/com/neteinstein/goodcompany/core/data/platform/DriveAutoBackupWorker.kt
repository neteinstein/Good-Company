package com.neteinstein.goodcompany.core.data.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neteinstein.goodcompany.core.domain.usecase.AutoBackupOutcome
import com.neteinstein.goodcompany.core.domain.usecase.RunAutoBackupUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * The automatic Drive backup (issue #166, Android only). Fires about once a day on a network; [RunAutoBackupUseCase]
 * decides whether a backup is due (Daily / Weekly / Off), runs it and records how it went for Settings.
 */
class DriveAutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val runAutoBackup: RunAutoBackupUseCase by inject()

    override suspend fun doWork(): Result {
        val outcome = runCatching { runAutoBackup() }.getOrNull()
        // A lost connection is worth a few more tries today; anything else waits for tomorrow (it's recorded).
        return if (outcome is AutoBackupOutcome.Failed && outcome.retrySoon && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "drive-auto-backup"
        private const val MAX_RETRIES = 3

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveAutoBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
