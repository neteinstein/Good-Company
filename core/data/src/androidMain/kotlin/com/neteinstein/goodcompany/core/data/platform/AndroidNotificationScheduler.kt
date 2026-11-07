package com.neteinstein.goodcompany.core.data.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neteinstein.goodcompany.core.data.R
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Schedules each notification as a delayed one-off WorkManager job (inexact timing is fine for "say happy
 * birthday" and avoids the SCHEDULE_EXACT_ALARM special permission). Catch-up notifications carry quick
 * actions ("We met", "Called", "Next week"), handled by [NotificationActionReceiver] without opening the app.
 */
internal class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<RenderedNotification>) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        val now = Clock.System.now()
        notifications.take(MAX_SCHEDULED).forEach { n ->
            val data = Data.Builder()
                .putString(KEY_ID, n.id)
                .putString(KEY_TITLE, n.title)
                .putString(KEY_BODY, n.body)
                .putStringArray(KEY_ACTIONS, n.actions.map { it.action.encode() }.toTypedArray())
                .putStringArray(KEY_ACTION_LABELS, n.actions.map { it.label }.toTypedArray())
                .build()
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay((n.at - now).inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(data)
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
        const val KEY_ACTIONS = "actions"
        const val KEY_ACTION_LABELS = "actionLabels"
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
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                AndroidNotificationScheduler.CHANNEL_ID,
                ctx.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val id = inputData.getString(AndroidNotificationScheduler.KEY_ID).orEmpty()
        val notificationId = id.hashCode()
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val content = launch?.let {
            PendingIntent.getActivity(ctx, notificationId, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val body = inputData.getString(AndroidNotificationScheduler.KEY_BODY)
        val builder = NotificationCompat.Builder(ctx, AndroidNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_good_company)
            .setContentTitle(inputData.getString(AndroidNotificationScheduler.KEY_TITLE))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        val actions = inputData.getStringArray(AndroidNotificationScheduler.KEY_ACTIONS).orEmpty()
        val labels = inputData.getStringArray(AndroidNotificationScheduler.KEY_ACTION_LABELS).orEmpty()
        actions.zip(labels).take(3).forEachIndexed { i, (encoded, label) ->
            val intent = Intent(ctx, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION)
                .putExtra(NotificationActionReceiver.EXTRA_ACTION, encoded)
                .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            val pending = PendingIntent.getBroadcast(
                ctx,
                notificationId * 10 + i,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, label, pending)
        }
        NotificationManagerCompat.from(ctx).notify(notificationId, builder.build())
        return Result.success()
    }
}

/** Handles notification quick actions in the background (no activity), then dismisses the notification. */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    private val handle: HandleNotificationActionUseCase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)?.let(NotificationAction::decode) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        scope.launch {
            try {
                handle(action)
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION = "com.neteinstein.goodcompany.NOTIFICATION_ACTION"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
