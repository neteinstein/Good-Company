package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Hangout
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
 *
 * Only friends with `notifyBirthday` get birthday pings (theirs and their kids'); important events,
 * reminders and "meet every N days" rules always notify.
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
    ): List<PlannedNotification> {
        val today = now.toLocalDate(zone)
        val end = today.plus(horizonDays, DateTimeUnit.DAY)
        val hour = reminderHour.coerceIn(0, 23)
        fun LocalDate.atReminderTime(): Instant = atTime(LocalTime(hour, 0)).toInstant(zone)
        val out = mutableListOf<PlannedNotification>()
        fun add(id: String, day: LocalDate, title: String, body: String) {
            val at = day.atReminderTime()
            if (at > now && day <= end) out += PlannedNotification(id, at, title, body)
        }

        for (f in friends) {
            if (f.notifyBirthday) {
                f.birthday?.let { b ->
                    val d = b.nextOccurrence(today)
                    val age = b.ageOn(d)
                    add(
                        "bday:${f.id.value}:$d",
                        d,
                        "🎂 ${f.name}'s birthday",
                        if (age != null) "${f.name} turns $age today — say hi!" else "It's ${f.name}'s birthday today — say hi!",
                    )
                }
                f.kids.forEach { kid ->
                    kid.birthday?.let { b ->
                        val d = b.nextOccurrence(today)
                        val age = b.ageOn(d)
                        val likes = kid.likes.takeIf { it.isNotEmpty() }?.joinToString(prefix = " Likes: ")
                        add(
                            "kid:${kid.id}:$d",
                            d,
                            "🎈 ${kid.name} (${f.name}'s kid)",
                            (if (age != null) "Turns $age today." else "Birthday today.") + (likes ?: ""),
                        )
                    }
                }
            }
            f.importantEvents.forEach { e ->
                add("event:${e.id}:day", e.date, "💬 Ping ${f.name}", "${e.title} is today. A quick message goes a long way.")
                if (e.remindDaysBefore > 0) {
                    add(
                        "event:${e.id}:before",
                        e.date.minus(e.remindDaysBefore, DateTimeUnit.DAY),
                        "📅 ${f.name}: ${e.title}",
                        "Coming up in ${e.remindDaysBefore} day(s).",
                    )
                }
            }
        }

        val byId = friends.associateBy { it.id }
        reminders.filter { !it.done && it.dueAt != null && it.dueAt > now }.forEach { r ->
            val names = r.friendIds.mapNotNull { byId[it]?.name }
            out += PlannedNotification(
                id = "reminder:${r.id}",
                at = r.dueAt!!,
                title = "⏰ ${r.title}",
                body = if (names.isEmpty()) "Reminder" else "With ${names.joinToString()}",
            )
        }

        Relationships.meetupStatuses(friends, hangouts, today, zone).forEach { s ->
            // If already overdue, nudge once tomorrow; otherwise on the due date.
            val day = if (s.dueDate <= today) today.plus(1, DateTimeUnit.DAY) else s.dueDate
            val since = s.daysSinceLastSeen?.let { "It's been $it days" } ?: "You haven't logged a meetup yet"
            add(
                "meet:${s.friend.id.value}:$day",
                day,
                "👋 Time to see ${s.friend.name}",
                "$since — you wanted to meet every ${s.friend.meetEveryDays} days.",
            )
        }

        return out.sortedBy { it.at }
    }
}
