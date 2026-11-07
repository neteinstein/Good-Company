package com.neteinstein.goodcompany.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.neteinstein.goodcompany.R
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HomeWidgetSummary
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHomeWidgetUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.daysUntil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * "Who's next" home-screen widget: the next birthday / big day / plan / reminder and the friend most due a catch-up,
 * with a one-tap "We met". Names are hidden when the user turned on the app lock or private notifications.
 * Refreshed by the system every few hours, after "We met", and live while the app process runs ([keepUpdated]).
 */
class NextUpWidget : AppWidgetProvider(), KoinComponent {
    private val observe: ObserveHomeWidgetUseCase by inject()
    private val checkIn: CheckInUseCase by inject()

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, observe().first(), manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MET) {
            super.onReceive(context, intent)
            return
        }
        val friendId = intent.getStringExtra(EXTRA_FRIEND_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                checkIn(listOf(FriendId(friendId)))
                refresh(context, observe().first())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_MET = "com.neteinstein.goodcompany.widget.MET"
        private const val EXTRA_FRIEND_ID = "friendId"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Re-renders every placed widget whenever the data changes, for as long as the app process lives. */
        @OptIn(FlowPreview::class)
        fun keepUpdated(context: Context, observe: ObserveHomeWidgetUseCase) {
            val app = context.applicationContext
            scope.launch {
                observe().debounce(DEBOUNCE_MS).distinctUntilChanged().collect { refresh(app, it) }
            }
        }

        private fun refresh(context: Context, summary: HomeWidgetSummary) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextUpWidget::class.java))
            if (ids.isNotEmpty()) render(context, summary, manager, ids)
        }

        private fun render(context: Context, summary: HomeWidgetSummary, manager: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(context.packageName, R.layout.widget_next_up)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            launch?.let {
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            }
            if (summary.isPrivate) {
                views.setTextViewText(R.id.widget_next_title, context.getString(R.string.widget_private))
                views.setViewVisibility(R.id.widget_next_when, View.GONE)
                views.setTextViewText(R.id.widget_catch_up, "")
                views.setViewVisibility(R.id.widget_met, View.GONE)
                manager.updateAppWidget(ids, views)
                return
            }

            val next = summary.next
            views.setViewVisibility(R.id.widget_next_when, if (next == null) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_next_title, next?.let { title(context, it) } ?: context.getString(R.string.widget_nothing))
            next?.let { views.setTextViewText(R.id.widget_next_when, whenText(context, summary, it)) }

            val catchUp = summary.catchUp
            if (catchUp == null) {
                views.setTextViewText(R.id.widget_catch_up, context.getString(R.string.widget_caught_up))
                views.setViewVisibility(R.id.widget_met, View.GONE)
            } else {
                views.setTextViewText(R.id.widget_catch_up, context.getString(R.string.widget_see, catchUp.friend.name.substringBefore(' ')))
                views.setViewVisibility(R.id.widget_met, View.VISIBLE)
                val met = Intent(context, NextUpWidget::class.java)
                    .setAction(ACTION_MET)
                    .putExtra(EXTRA_FRIEND_ID, catchUp.friend.id.value)
                views.setOnClickPendingIntent(
                    R.id.widget_met,
                    PendingIntent.getBroadcast(context, 1, met, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            }
            manager.updateAppWidget(ids, views)
        }

        private fun title(context: Context, item: AgendaItem): String = when (item) {
            is AgendaItem.FriendBirthday -> context.getString(R.string.widget_birthday, item.friend.name.substringBefore(' '))
            is AgendaItem.KidBirthday -> context.getString(R.string.widget_kid_birthday, item.kid.name, item.parent.name.substringBefore(' '))
            is AgendaItem.FriendEvent -> context.getString(R.string.widget_event, item.friend.name.substringBefore(' '), item.event.title)
            is AgendaItem.ReminderDue -> item.reminder.title
            is AgendaItem.PlanUpcoming -> item.plan.title
            is AgendaItem.PlanToConfirm -> item.plan.title
            is AgendaItem.MeetupOverdue -> context.getString(R.string.widget_see, item.friend.name.substringBefore(' '))
            is AgendaItem.GroupOverdue -> item.group.name
        }

        private fun whenText(context: Context, summary: HomeWidgetSummary, item: AgendaItem): String =
            when (val days = summary.today.daysUntil(item.date)) {
                0 -> context.getString(R.string.widget_today)
                1 -> context.getString(R.string.widget_tomorrow)
                else -> context.resources.getQuantityString(R.plurals.widget_in_days, days, days)
            }

        private const val DEBOUNCE_MS = 1_000L
    }
}
