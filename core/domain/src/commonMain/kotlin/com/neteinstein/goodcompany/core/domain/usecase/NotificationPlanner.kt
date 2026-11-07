package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
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
 * reminders and plans always notify. Catch-up nudges (friend and group rhythms) are budgeted by [NudgePolicy]:
 * gathered into one weekly digest, or at most one a day, and they quieten down (then stop) when the app isn't
 * being opened. Paused friends never get one.
 */
object NotificationPlanner {
    const val DEFAULT_HORIZON_DAYS = 60

    /** How many days after a plan we keep asking "Did you meet?". */
    const val FOLLOW_UP_DAYS = 7

    /** Names spelled out in the weekly digest ("Ana, Rui and 3 more"). */
    const val DIGEST_NAMES = 3

    private class CatchUp(val dueDate: LocalDate, val name: String, val idPrefix: String, val content: NotificationContent)

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
        plans: List<Plan> = emptyList(),
        interactions: List<Interaction> = emptyList(),
        policy: NudgePolicy = NudgePolicy(weeklyDigest = false),
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

        // --- Plans: a heads-up on the day, then "Did you meet?" the day after (until answered, for a week).
        plans.filter { it.status == PlanStatus.PLANNED }.forEach { p ->
            val names = p.friendIds.mapNotNull { byId[it]?.name }
            add("plan:${p.id}:day", p.date, NotificationContent.PlanToday(p.id, p.title, names))
            val firstAsk = p.date.plus(1, DateTimeUnit.DAY)
            val lastAsk = p.date.plus(FOLLOW_UP_DAYS, DateTimeUnit.DAY)
            var ask = maxOf(firstAsk, today)
            if (ask.atReminderTime() <= now) ask = ask.plus(1, DateTimeUnit.DAY)
            if (ask <= lastAsk) add("plan:${p.id}:ask", ask, NotificationContent.PlanFollowUp(p.id, p.title, names))
        }

        // --- Catch-up nudges, within a budget (see [NudgePolicy]).
        val quietness = policy.quietness(today, zone)
        if (quietness == NudgePolicy.Quietness.SILENT) return out.sortedBy { it.at }

        val catchUps = mutableListOf<CatchUp>()
        Relationships.meetupStatuses(friends, hangouts, today, zone, rules, interactions).forEach { s ->
            catchUps += CatchUp(
                s.dueDate,
                s.friend.name,
                "meet:${s.friend.id.value}",
                NotificationContent.MeetupDue(s.friend.id, s.friend.name, s.daysSinceLastSeen, s.cadenceDays),
            )
        }
        Relationships.groupStatuses(groups, friends, hangouts, today, zone).forEach { s ->
            val due = s.dueDate ?: return@forEach
            catchUps += CatchUp(
                due,
                s.group.name,
                "group:${s.group.id}",
                NotificationContent.GroupMeetupDue(s.group.id, s.group.name, s.daysSince, s.group.meetEveryDays ?: 0),
            )
        }
        if (catchUps.isEmpty()) return out.sortedBy { it.at }

        if (policy.weeklyDigest) {
            val every = if (quietness == NudgePolicy.Quietness.QUIET) 14 else 7
            var day = policy.nextDigestDay(today)
            if (day.atReminderTime() <= now) day = day.plus(7, DateTimeUnit.DAY)
            while (day <= end) {
                val due = catchUps.filter { it.dueDate <= day }.sortedBy { it.dueDate }
                if (due.isNotEmpty()) {
                    add("digest:$day", day, NotificationContent.CatchUpDigest(due.take(DIGEST_NAMES).map { it.name }, due.size))
                }
                day = day.plus(every, DateTimeUnit.DAY)
            }
        } else {
            // One catch-up a day at most (one every few days when the app is being ignored); extra ones wait.
            val gap = if (quietness == NudgePolicy.Quietness.QUIET) 3 else 1
            var nextFree = today
            catchUps.sortedBy { it.dueDate }.forEach { c ->
                // If already overdue, nudge once tomorrow at the earliest; otherwise on the due date.
                var day = maxOf(if (c.dueDate <= today) today.plus(1, DateTimeUnit.DAY) else c.dueDate, nextFree)
                if (day.atReminderTime() <= now) day = day.plus(1, DateTimeUnit.DAY)
                add("${c.idPrefix}:$day", day, c.content)
                nextFree = day.plus(gap, DateTimeUnit.DAY)
            }
        }

        return out.sortedBy { it.at }
    }
}

/**
 * The budget for catch-up nudges. Ignored nudges get quieter, never louder: after [QUIET_AFTER_DAYS] without
 * opening the app they slow down; after [SILENT_AFTER_DAYS] they stop (birthdays, big days, reminders and plans
 * the user set up still arrive).
 */
data class NudgePolicy(
    val weeklyDigest: Boolean = true,
    /** ISO day of week, 1 = Monday … 7 = Sunday. */
    val digestDay: Int = 6,
    val lastOpenedAt: Instant? = null,
) {
    enum class Quietness { NORMAL, QUIET, SILENT }

    fun quietness(today: LocalDate, zone: TimeZone): Quietness {
        val opened = lastOpenedAt?.toLocalDate(zone) ?: return Quietness.NORMAL
        val days = opened.daysUntil(today)
        return when {
            days >= SILENT_AFTER_DAYS -> Quietness.SILENT
            days >= QUIET_AFTER_DAYS -> Quietness.QUIET
            else -> Quietness.NORMAL
        }
    }

    /** The digest day on or after [today]. */
    fun nextDigestDay(today: LocalDate): LocalDate {
        val target = digestDay.coerceIn(1, 7)
        val delta = (target - today.dayOfWeek.isoDayNumber + 7) % 7
        return today.plus(delta, DateTimeUnit.DAY)
    }

    companion object {
        const val QUIET_AFTER_DAYS = 21
        const val SILENT_AFTER_DAYS = 90

        fun from(settings: AppSettings) = NudgePolicy(settings.weeklyDigest, settings.digestDay, settings.lastOpenedAt)
    }
}
