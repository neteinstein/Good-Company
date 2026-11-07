package com.neteinstein.goodcompany.core.domain.model

import kotlin.time.Instant

/** What a notification is about. Text is rendered (and localised) later by a [NotificationRenderer]. */
sealed interface NotificationContent {
    data class FriendBirthday(val friendName: String, val age: Int?) : NotificationContent

    data class KidBirthday(val kidName: String, val parentName: String, val age: Int?, val likes: List<String>) : NotificationContent

    data class EventToday(val friendName: String, val title: String) : NotificationContent

    data class EventSoon(val friendName: String, val title: String, val daysBefore: Int) : NotificationContent

    data class ReminderDue(val title: String, val withNames: List<String>) : NotificationContent

    data class MeetupDue(val friendId: FriendId, val friendName: String, val daysSince: Int?, val everyDays: Int) : NotificationContent

    data class GroupMeetupDue(val groupId: String, val groupName: String, val daysSince: Int?, val everyDays: Int) : NotificationContent
}

/** A local notification to schedule. [id] is stable so rescheduling replaces, never duplicates. */
data class PlannedNotification(val id: String, val at: Instant, val content: NotificationContent)

/** Quick actions offered on catch-up notifications. */
data class NotificationAction(val kind: Kind, val targetId: String, val isGroup: Boolean = false) {
    enum class Kind { MET, CALLED, SNOOZE_WEEK, SNOOZE_WEEKEND }

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
)
