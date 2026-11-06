package com.neteinstein.goodcompany.core.data.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.neteinstein.goodcompany.core.data.R
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import kotlin.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Schedules each planned notification as a delayed one-off WorkManager job (inexact timing is fine for
 * "say happy birthday" and avoids the SCHEDULE_EXACT_ALARM special permission).
 */
internal class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<PlannedNotification>) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        val now = Clock.System.now()
        notifications.take(MAX_SCHEDULED).forEach { n ->
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay((n.at - now).inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to n.id, KEY_TITLE to n.title, KEY_BODY to n.body))
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("notification:${n.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    internal companion object {
        const val TAG = "goodcompany-notification"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val CHANNEL_ID = "reminders"
        const val MAX_SCHEDULED = 200
    }
}

/** Posts one reminder notification. Opens the app's launcher activity on tap. */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                AndroidNotificationScheduler.CHANNEL_ID,
                ctx.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val content = launch?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val id = inputData.getString(AndroidNotificationScheduler.KEY_ID).orEmpty()
        val notification = NotificationCompat.Builder(ctx, AndroidNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_good_company)
            .setContentTitle(inputData.getString(AndroidNotificationScheduler.KEY_TITLE))
            .setContentText(inputData.getString(AndroidNotificationScheduler.KEY_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(inputData.getString(AndroidNotificationScheduler.KEY_BODY)))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(ctx).notify(id.hashCode(), notification)
        return Result.success()
    }
}
