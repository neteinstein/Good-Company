package com.neteinstein.goodcompany.core.domain.model

import kotlin.time.Instant

/** What a notification is about. Text is rendered (and localised) later by a [NotificationRenderer]. */
sealed interface NotificationContent {
    data class FriendBirthday(val friendName: String, val age: Int?) : NotificationContent

    data class KidBirthday(val kidName: String, val parentName: String, val age: Int?, val likes: List<String>) : NotificationContent

    /** A relative whose birthday reminder is on ([Relative.notifyBirthday]). */
    data class RelativeBirthday(val relativeName: String, val friendName: String, val relation: Relation, val age: Int?) : NotificationContent

    data class EventToday(val friendName: String, val title: String) : NotificationContent

    data class EventSoon(val friendName: String, val title: String, val daysBefore: Int) : NotificationContent

    data class ReminderDue(val title: String, val withNames: List<String>) : NotificationContent

    data class MeetupDue(val friendId: FriendId, val friendName: String, val daysSince: Int?, val everyDays: Int) : NotificationContent

    data class GroupMeetupDue(val groupId: String, val groupName: String, val daysSince: Int?, val everyDays: Int) : NotificationContent

    /** The weekly catch-up note: a few names worth reaching out to (instead of one notification per friend). */
    data class CatchUpDigest(val names: List<String>, val total: Int) : NotificationContent

    /** A planned meetup is today. */
    data class PlanToday(val planId: String, val title: String, val withNames: List<String>) : NotificationContent

    /** The day after a plan: "Did you meet?" — answered with quick actions. */
    data class PlanFollowUp(val planId: String, val title: String, val withNames: List<String>) : NotificationContent
}

/** A local notification to schedule. [id] is stable so rescheduling replaces, never duplicates. */
data class PlannedNotification(val id: String, val at: Instant, val content: NotificationContent)

/** Quick actions offered on catch-up notifications. */
data class NotificationAction(val kind: Kind, val targetId: String, val isGroup: Boolean = false) {
    enum class Kind { MET, CALLED, SNOOZE_WEEK, SNOOZE_WEEKEND, PLAN_HAPPENED, PLAN_SKIPPED }

    val isPlan: Boolean get() = kind == Kind.PLAN_HAPPENED || kind == Kind.PLAN_SKIPPED

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
)
