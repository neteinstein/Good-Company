package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Turns app data into the set of local notifications to schedule over the next [horizonDays].
 * Pure and deterministic: ids are derived from the data, so re-planning replaces instead of duplicating.
 * Text is not decided here — each notification carries [NotificationContent] that the UI layer renders
 * in the user's language.
 *
 * Only friends with `notifyBirthday` get birthday pings (theirs and their kids'); important events,
 * reminders, catch-up cadences (own or circle) and group cadences always notify.
 */
object NotificationPlanner {
    const val DEFAULT_HORIZON_DAYS = 60

    fun plan(
        friends: List<Friend>,
        reminders: List<Reminder>,
        hangouts: List<Hangout>,
        now: Instant,
        zone: TimeZone,
        reminderHour: Int,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
        rules: CadenceRules = CadenceRules(),
        groups: List<FriendGroup> = emptyList(),
    ): List<PlannedNotification> {
        val today = now.toLocalDate(zone)
        val end = today.plus(horizonDays, DateTimeUnit.DAY)
        val hour = reminderHour.coerceIn(0, 23)
        fun LocalDate.atReminderTime(): Instant = atTime(LocalTime(hour, 0)).toInstant(zone)
        val out = mutableListOf<PlannedNotification>()
        fun add(id: String, day: LocalDate, content: NotificationContent) {
            val at = day.atReminderTime()
            if (at > now && day <= end) out += PlannedNotification(id, at, content)
        }

        for (f in friends) {
            if (f.notifyBirthday) {
                f.birthday?.let { b ->
                    val d = b.nextOccurrence(today)
                    add("bday:${f.id.value}:$d", d, NotificationContent.FriendBirthday(f.name, b.ageOn(d)))
                }
                f.kids.forEach { kid ->
                    kid.birthday?.let { b ->
                        val d = b.nextOccurrence(today)
                        add("kid:${kid.id}:$d", d, NotificationContent.KidBirthday(kid.name, f.name, b.ageOn(d), kid.likes))
                    }
                }
            }
            f.importantEvents.forEach { e ->
                add("event:${e.id}:day", e.date, NotificationContent.EventToday(f.name, e.title))
                if (e.remindDaysBefore > 0) {
                    add(
                        "event:${e.id}:before",
                        e.date.minus(e.remindDaysBefore, DateTimeUnit.DAY),
                        NotificationContent.EventSoon(f.name, e.title, e.remindDaysBefore),
                    )
                }
            }
        }

        val byId = friends.associateBy { it.id }
        reminders.filter { !it.done && it.dueAt != null && it.dueAt > now }.forEach { r ->
            out += PlannedNotification(
                id = "reminder:${r.id}",
                at = r.dueAt!!,
                content = NotificationContent.ReminderDue(r.title, r.friendIds.mapNotNull { byId[it]?.name }),
            )
        }

        Relationships.meetupStatuses(friends, hangouts, today, zone, rules).forEach { s ->
            // If already overdue, nudge once tomorrow; otherwise on the due date.
            val day = if (s.dueDate <= today) today.plus(1, DateTimeUnit.DAY) else s.dueDate
            add("meet:${s.friend.id.value}:$day", day, NotificationContent.MeetupDue(s.friend.id, s.friend.name, s.daysSinceLastSeen, s.cadenceDays))
        }

        Relationships.groupStatuses(groups, friends, hangouts, today, zone).forEach { s ->
            val due = s.dueDate ?: return@forEach
            val day = if (due <= today) today.plus(1, DateTimeUnit.DAY) else due
            add(
                "group:${s.group.id}:$day",
                day,
                NotificationContent.GroupMeetupDue(s.group.id, s.group.name, s.daysSince, s.group.meetEveryDays ?: 0),
            )
        }

        return out.sortedBy { it.at }
    }
}
