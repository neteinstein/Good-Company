package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** What a notification is about. Text is rendered (and localised) later by a [NotificationRenderer]. */
sealed interface NotificationContent {
    data class FriendBirthday(val friendName: String, val age: Int?) : NotificationContent

    data class KidBirthday(val kidName: String, val parentName: String, val age: Int?, val likes: List<String>) : NotificationContent

    /** A relative whose birthday reminder is on ([Relative.notifyBirthday]). */
    data class RelativeBirthday(val relativeName: String, val friendName: String, val relation: Relation, val age: Int?) : NotificationContent

    /** A couple's anniversary (issue #39): [years] together on the day. */
    data class Anniversary(val firstName: String, val secondName: String, val years: Int?) : NotificationContent

    data class EventToday(val friendName: String, val title: String) : NotificationContent

    data class EventSoon(val friendName: String, val title: String, val daysBefore: Int) : NotificationContent

    /** [reminderId] is the target of its Done and Snooze actions (issue #99). */
    data class ReminderDue(val reminderId: String, val title: String, val withNames: List<String>) : NotificationContent

    data class MeetupDue(val friendId: FriendId, val friendName: String, val daysSince: Int?, val everyDays: Int) : NotificationContent

    data class GroupMeetupDue(val groupId: String, val groupName: String, val daysSince: Int?, val everyDays: Int) : NotificationContent

    /** The weekly catch-up note: a few names worth reaching out to (instead of one notification per friend). */
    data class CatchUpDigest(val names: List<String>, val total: Int) : NotificationContent

    /** A planned meetup is today. [occurrence] is set for a recurring plan (the day of this occurrence). */
    data class PlanToday(
        val planId: String,
        val title: String,
        val withNames: List<String>,
        val occurrence: LocalDate? = null,
    ) : NotificationContent

    /**
     * The day after a plan: "Did you meet?" — answered with quick actions. A circle plan ([isCircle]) can't be logged
     * from the notification (you tick who came in the app), so it only offers "We didn't go". A group or circle plan
     * ([isGroup]) also offers "I didn't go" (issue #100).
     */
    data class PlanFollowUp(
        val planId: String,
        val title: String,
        val withNames: List<String>,
        val occurrence: LocalDate? = null,
        val isCircle: Boolean = false,
        /** With more than one person or a circle: "I didn't go" is offered too (issue #100). */
        val isGroup: Boolean = isCircle,
    ) : NotificationContent {
        /** The notification actions' target: this occurrence. */
        val target: String get() = PlanOccurrence(planId, occurrence).encode()

        /**
         * Its quick actions: "Yes" (not for a circle plan, which asks who came in the app), "It didn't happen", and
         * "I didn't go" for a group or circle plan (issue #100).
         */
        val actions: List<NotificationAction>
            get() = listOfNotNull(
                NotificationAction(NotificationAction.Kind.PLAN_HAPPENED, target).takeUnless { isCircle },
                NotificationAction(NotificationAction.Kind.PLAN_SKIPPED, target),
                NotificationAction(NotificationAction.Kind.PLAN_NOT_ATTENDED, target).takeIf { isGroup },
            )
    }

    /**
     * The day's calendar summary (D3): events with friends that ended, worth logging. [eventTitle] is set when there's
     * just one ([total] = 1). Shown on this device only.
     */
    data class CalendarMeetups(val names: List<String>, val eventTitle: String?, val total: Int) : NotificationContent

    /**
     * Issue #98: a reminder whose time has passed and that isn't done yet. Asked again at the reminder hour each day
     * (persistent while "Keep important ones on screen" is on) until it is done.
     */
    data class ReminderOverdue(val reminderId: String, val title: String, val withNames: List<String>) : NotificationContent

    /** Issue #98: Settings → Notifications → "Send a test notification". */
    data object Test : NotificationContent

    /** Issue #116: the daily "Who did you catch up with today?"; a tap opens the Log popup. */
    data object DailyLog : NotificationContent

    /** Which switch in Settings → Notifications this belongs to (and, on Android, its channel). */
    val type: NotificationType
        get() = when (this) {
            is FriendBirthday, is KidBirthday, is RelativeBirthday, is Anniversary -> NotificationType.BIRTHDAYS
            is EventToday, is EventSoon -> NotificationType.EVENTS
            is ReminderDue, is ReminderOverdue, Test -> NotificationType.REMINDERS
            is PlanToday, is PlanFollowUp -> NotificationType.PLANS
            is MeetupDue, is GroupMeetupDue, is CatchUpDigest -> NotificationType.CATCH_UPS
            is CalendarMeetups -> NotificationType.CALENDAR
            DailyLog -> NotificationType.DAILY_LOG
        }
}

/** The kinds of notification the user can switch on and off (issue #98). */
enum class NotificationType { BIRTHDAYS, EVENTS, REMINDERS, PLANS, CATCH_UPS, CALENDAR, DAILY_LOG }

/**
 * Where a notification is posted on Android (issue #98): Reminders · Birthdays & big days · Plans · Catch-ups (low)
 * · Important (high, for the persistent ones). iOS has no channels; [IMPORTANT] is its time-sensitive level.
 */
enum class NotificationChannel {
    REMINDERS,
    BIRTHDAYS,
    PLANS,
    CATCH_UPS,
    IMPORTANT,
    ;

    companion object {
        fun of(type: NotificationType, persistent: Boolean): NotificationChannel = when {
            persistent -> IMPORTANT
            else -> when (type) {
                NotificationType.BIRTHDAYS, NotificationType.EVENTS -> BIRTHDAYS
                NotificationType.REMINDERS, NotificationType.DAILY_LOG -> REMINDERS
                NotificationType.PLANS -> PLANS
                NotificationType.CATCH_UPS, NotificationType.CALENDAR -> CATCH_UPS
            }
        }
    }
}

/**
 * A local notification to schedule. [id] is stable so rescheduling replaces, never duplicates.
 *
 * Issue #98: [persistent] ones stay on screen (Android: ongoing; iOS: time-sensitive) until acted on, and go away by
 * themselves at [until]. Notifications that share a [slot] replace each other on screen (an overdue reminder asked
 * again the next day shows once, not twice). A posted [whileOpen] one is taken off the screen as soon as its slot is no
 * longer planned: the reminder was done or deleted, the plan answered.
 */
data class PlannedNotification(
    val id: String,
    val at: Instant,
    val content: NotificationContent,
    val persistent: Boolean = false,
    val until: Instant? = null,
    val slot: String = id,
    val whileOpen: Boolean = false,
    /** Issue #116: an app link (`goodcompany://…`) a tap opens instead of just the app. */
    val link: String? = null,
) {
    val channel: NotificationChannel get() = NotificationChannel.of(content.type, persistent)
}

/** Quick actions offered on notifications: catch-ups, plans and reminders ([targetId] is the reminder's id, issue #99). */
data class NotificationAction(val kind: Kind, val targetId: String, val isGroup: Boolean = false) {
    enum class Kind { MET, CALLED, SNOOZE_WEEK, SNOOZE_WEEKEND, PLAN_HAPPENED, PLAN_SKIPPED, PLAN_NOT_ATTENDED, REMINDER_DONE, REMINDER_SNOOZE }

    val isPlan: Boolean get() = kind == Kind.PLAN_HAPPENED || kind == Kind.PLAN_SKIPPED || kind == Kind.PLAN_NOT_ATTENDED

    /** Compact form for platform payloads (Android intent extras, iOS userInfo). */
    fun encode(): String = "${kind.name}|${if (isGroup) "g" else "f"}|$targetId"

    companion object {
        fun decode(raw: String): NotificationAction? {
            val parts = raw.split('|', limit = 3)
            if (parts.size != 3) return null
            val kind = Kind.entries.firstOrNull { it.name == parts[0] } ?: return null
            return NotificationAction(kind, parts[2], isGroup = parts[1] == "g")
        }
    }
}

data class RenderedAction(val action: NotificationAction, val label: String)

/** A notification with its final, localised text. */
data class RenderedNotification(
    val id: String,
    val at: Instant,
    val title: String,
    val body: String,
    val actions: List<RenderedAction> = emptyList(),
    /** What the lock screen shows instead of [title]/[body] when notifications are private. Null = not private. */
    val publicText: String? = null,
    val channel: NotificationChannel = NotificationChannel.REMINDERS,
    /** Stays on screen until acted on (issue #98); see [PlannedNotification.persistent]. */
    val persistent: Boolean = false,
    /** When a persistent notification goes away by itself (the end of its day). */
    val until: Instant? = null,
    /** On-screen identity: a newer notification with the same slot replaces the older one. */
    val slot: String = id,
    /** Taken off the screen once its slot is no longer planned; see [PlannedNotification.whileOpen]. */
    val whileOpen: Boolean = false,
    /** What a tap opens; see [PlannedNotification.link]. */
    val link: String? = null,
)
