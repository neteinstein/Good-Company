package com.neteinstein.goodcompany.core.data.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neteinstein.goodcompany.core.data.R
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.NotificationChannel
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
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
 *
 * Issue #98: one channel per kind ([AndroidChannels]); persistent ones are ongoing in the "Important" channel and
 * share an on-screen slot (the notification tag), so an overdue reminder asked again replaces itself. A posted
 * "while open" one is cancelled once its slot is no longer planned (the reminder was done, the plan answered).
 */
internal class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<RenderedNotification>) {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG)
        dismissClosed(notifications.mapTo(HashSet()) { it.slot })
        val now = Clock.System.now()
        notifications.take(MAX_SCHEDULED).forEach { n ->
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay((n.at - now).inWholeMilliseconds.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(n.toData())
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("notification:${n.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun showNow(notification: RenderedNotification) {
        val request = OneTimeWorkRequestBuilder<NotificationWorker>().setInputData(notification.toData()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("notification-now:${notification.id}", ExistingWorkPolicy.REPLACE, request)
    }

    private fun dismissClosed(planned: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.activeNotifications
                .filter { it.notification.extras.getBoolean(EXTRA_WHILE_OPEN) && it.tag != null && it.tag !in planned }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    internal companion object {
        const val TAG = "goodcompany-notification"
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_PUBLIC = "public"
        const val KEY_ACTIONS = "actions"
        const val KEY_ACTION_LABELS = "actionLabels"
        const val KEY_CHANNEL = "channel"
        const val KEY_PERSISTENT = "persistent"
        const val KEY_UNTIL = "until"
        const val KEY_SLOT = "slot"
        const val KEY_WHILE_OPEN = "whileOpen"
        const val EXTRA_WHILE_OPEN = "goodcompany.whileOpen"
        const val MAX_SCHEDULED = 200

        /** Every posted notification uses id 0 under its slot as the tag; the slot is what tells them apart. */
        const val NOTIFICATION_ID = 0

        fun RenderedNotification.toData(): Data = Data.Builder()
            .putString(KEY_ID, id)
            .putString(KEY_TITLE, title)
            .putString(KEY_BODY, body)
            .putString(KEY_PUBLIC, publicText)
            .putStringArray(KEY_ACTIONS, actions.map { it.action.encode() }.toTypedArray())
            .putStringArray(KEY_ACTION_LABELS, actions.map { it.label }.toTypedArray())
            .putString(KEY_CHANNEL, channel.name)
            .putBoolean(KEY_PERSISTENT, persistent)
            .putLong(KEY_UNTIL, until?.toEpochMilliseconds() ?: 0L)
            .putString(KEY_SLOT, slot)
            .putBoolean(KEY_WHILE_OPEN, whileOpen)
            .build()
    }
}

/** The Android channels (issue #98), created once; users can tune each in system settings. */
internal object AndroidChannels {
    /** Kept from before #98 so people's settings for it carry over. */
    fun id(channel: NotificationChannel): String = when (channel) {
        NotificationChannel.REMINDERS -> "reminders"
        NotificationChannel.BIRTHDAYS -> "birthdays"
        NotificationChannel.PLANS -> "plans"
        NotificationChannel.CATCH_UPS -> "catch_ups"
        NotificationChannel.IMPORTANT -> "important"
    }

    fun ensure(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        NotificationChannel.entries.forEach { channel ->
            val (name, importance) = when (channel) {
                NotificationChannel.REMINDERS -> R.string.notification_channel_reminders to NotificationManagerCompat.IMPORTANCE_DEFAULT
                NotificationChannel.BIRTHDAYS -> R.string.notification_channel_birthdays to NotificationManagerCompat.IMPORTANCE_DEFAULT
                NotificationChannel.PLANS -> R.string.notification_channel_plans to NotificationManagerCompat.IMPORTANCE_DEFAULT
                NotificationChannel.CATCH_UPS -> R.string.notification_channel_catch_ups to NotificationManagerCompat.IMPORTANCE_LOW
                NotificationChannel.IMPORTANT -> R.string.notification_channel_important to NotificationManagerCompat.IMPORTANCE_HIGH
            }
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(id(channel), importance).setName(context.getString(name)).build(),
            )
        }
    }
}

/**
 * Whether the app may post notifications. Below Android 13 there is no runtime permission (issue #98: Android 12L got
 * nothing because POST_NOTIFICATIONS always read as denied), so only the app's switch in system settings counts.
 */
internal fun Context.mayPostNotifications(): Boolean {
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** Posts one notification. Opens the app's launcher activity on tap. */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ctx.mayPostNotifications()) return Result.success()
        val until = inputData.getLong(AndroidNotificationScheduler.KEY_UNTIL, 0L)
        val now = System.currentTimeMillis()
        // Ran late (the device was off): a persistent one whose day is over isn't worth showing.
        if (until in 1..now) return Result.success()
        AndroidChannels.ensure(ctx)
        post(ctx, until, now)
        return Result.success()
    }

    // Checked just above with mayPostNotifications (which lint can't follow).
    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, until: Long, now: Long) {
        val id = inputData.getString(AndroidNotificationScheduler.KEY_ID).orEmpty()
        val slot = inputData.getString(AndroidNotificationScheduler.KEY_SLOT) ?: id
        val persistent = inputData.getBoolean(AndroidNotificationScheduler.KEY_PERSISTENT, false)
        val channel = inputData.getString(AndroidNotificationScheduler.KEY_CHANNEL)
            ?.let { name -> NotificationChannel.entries.firstOrNull { it.name == name } }
            ?: NotificationChannel.REMINDERS
        val channelId = AndroidChannels.id(channel)
        val requestCode = slot.hashCode()
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val content = launch?.let {
            PendingIntent.getActivity(ctx, requestCode, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val body = inputData.getString(AndroidNotificationScheduler.KEY_BODY)
        val builder = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_stat_good_company)
            .setContentTitle(inputData.getString(AndroidNotificationScheduler.KEY_TITLE))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (persistent) {
            // Stays until tapped or acted on (Android 14+ lets people swipe it away; the next re-plan brings back
            // the ones still open). Re-posting the same slot updates it quietly.
            builder.setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            if (until > now) builder.setTimeoutAfter(until - now)
        }
        if (inputData.getBoolean(AndroidNotificationScheduler.KEY_WHILE_OPEN, false)) {
            builder.addExtras(android.os.Bundle().apply { putBoolean(AndroidNotificationScheduler.EXTRA_WHILE_OPEN, true) })
        }
        // Private mode: the lock screen shows only a neutral line; details appear once unlocked.
        inputData.getString(AndroidNotificationScheduler.KEY_PUBLIC)?.let { publicText ->
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(ctx, channelId)
                        .setSmallIcon(R.drawable.ic_stat_good_company)
                        .setContentTitle(publicText)
                        .build(),
                )
        }
        val actions = inputData.getStringArray(AndroidNotificationScheduler.KEY_ACTIONS).orEmpty()
        val labels = inputData.getStringArray(AndroidNotificationScheduler.KEY_ACTION_LABELS).orEmpty()
        actions.zip(labels).take(3).forEachIndexed { i, (encoded, label) ->
            val intent = Intent(ctx, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION)
                .putExtra(NotificationActionReceiver.EXTRA_ACTION, encoded)
                .putExtra(NotificationActionReceiver.EXTRA_SLOT, slot)
            val pending = PendingIntent.getBroadcast(
                ctx,
                requestCode * 10 + i,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, label, pending)
        }
        NotificationManagerCompat.from(ctx).notify(slot, AndroidNotificationScheduler.NOTIFICATION_ID, builder.build())
    }
}

/**
 * Handles notification quick actions in the background (no activity), dismisses the notification, then re-plans so
 * the schedule reflects the answer even when the app isn't open (issue #98).
 */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    private val handle: HandleNotificationActionUseCase by inject()
    private val keepScheduled: KeepNotificationsScheduledUseCase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)?.let(NotificationAction::decode) ?: return
        val slot = intent.getStringExtra(EXTRA_SLOT)
        val pending = goAsync()
        scope.launch {
            try {
                handle(action)
                slot?.let { NotificationManagerCompat.from(context).cancel(it, AndroidNotificationScheduler.NOTIFICATION_ID) }
                runCatching { keepScheduled.once() }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION = "com.neteinstein.goodcompany.NOTIFICATION_ACTION"
        const val EXTRA_ACTION = "action"
        const val EXTRA_SLOT = "slot"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/**
 * Issue #98: re-plans the notifications once a day, so they keep arriving (and persistent ones come back) when the
 * app isn't opened. Planning used to run only while the UI was alive.
 */
class ReplanNotificationsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val keepScheduled: KeepNotificationsScheduledUseCase by inject()

    override suspend fun doWork(): Result = runCatching { keepScheduled.once() }
        .fold(onSuccess = { Result.success() }, onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() })

    companion object {
        private const val UNIQUE_NAME = "replan-notifications"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReplanNotificationsWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
