package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Turns app data into the set of local notifications to schedule over the next [horizonDays].
 * Pure and deterministic: ids are derived from the data, so re-planning replaces instead of duplicating.
 * Text is not decided here — each notification carries [NotificationContent] that the UI layer renders
 * in the user's language.
 *
 * Only people with `notifyBirthday` get birthday pings (a kid has their own, F9); a relative's birthday pings only
 * when that relative's own `notifyBirthday` is on, an anniversary when its link's reminder is on; important events,
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

    /** The calendar summary comes at this hour, or a little after the day's last matched event ends. */
    const val CALENDAR_HOUR = 20
    const val CALENDAR_DELAY_MINUTES = 30

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
        links: List<PersonLink> = emptyList(),
        calendar: List<CalendarSuggestion> = emptyList(),
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

        val parents = KidsAsPeople.parentsById(links, friends)
        for (f in friends) {
            if (f.notifyBirthday) {
                f.birthday?.let { b ->
                    val d = b.nextOccurrence(today)
                    // A kid (F9) has their own reminder; the ping says whose kid they are (same id as before they were people).
                    val parent = parents[f.id]?.firstOrNull()
                    if (parent != null) {
                        add("kid:${f.id.value}:$d", d, NotificationContent.KidBirthday(f.displayName, parent.displayName, b.ageOn(d), f.likes))
                    } else {
                        add("bday:${f.id.value}:$d", d, NotificationContent.FriendBirthday(f.displayName, b.ageOn(d)))
                    }
                }
            }
            // Relatives have their own switch (on by default, brief decision F10), like the friend's own; never throttled.
            f.relatives.filter { it.notifyBirthday }.forEach { r ->
                r.birthday?.let { b ->
                    val d = b.nextOccurrence(today)
                    add("relative:${f.id.value}:${r.id}:$d", d, NotificationContent.RelativeBirthday(r.name, f.displayName, r.relation, b.ageOn(d)))
                }
            }
            f.importantEvents.forEach { e ->
                add("event:${e.id}:day", e.date, NotificationContent.EventToday(f.displayName, e.title))
                if (e.remindDaysBefore > 0) {
                    add(
                        "event:${e.id}:before",
                        e.date.minus(e.remindDaysBefore, DateTimeUnit.DAY),
                        NotificationContent.EventSoon(f.displayName, e.title, e.remindDaysBefore),
                    )
                }
            }
        }

        // Anniversaries (issue #39): on the day, like a birthday; never throttled.
        PersonLinks.anniversaries(links, friends).forEach { (link, a, b) ->
            val d = link.nextAnniversary(today) ?: return@forEach
            add("anniversary:${link.id}:$d", d, NotificationContent.Anniversary(a.displayName, b.displayName, link.yearsOn(d)))
        }

        val byId = friends.associateBy { it.id }
        reminders.filter { !it.done && it.dueAt != null && it.dueAt > now }.forEach { r ->
            out += PlannedNotification(
                id = "reminder:${r.id}",
                at = r.dueAt!!,
                content = NotificationContent.ReminderDue(r.title, r.friendIds.mapNotNull { byId[it]?.displayName }),
            )
        }

        // --- Plans: a heads-up on the day of each occurrence, then "Did you meet?" the day after (until answered, for
        // a week). Never throttled (gotcha 16). A circle plan names the circle.
        val circlesById = groups.associateBy { it.id }
        plans.filter { it.status == PlanStatus.PLANNED }.forEach { p ->
            val names = listOfNotNull(p.circleId?.let { circlesById[it]?.name }) + p.friendIds.mapNotNull { byId[it]?.displayName }
            val isCircle = p.circleId != null && p.circleId in circlesById
            p.occurrences(today.minus(FOLLOW_UP_DAYS, DateTimeUnit.DAY), end).forEach { day ->
                val occurrence = PlanOccurrence(p.id, day.takeIf { p.isRecurring })
                val key = occurrence.encode()
                add("plan:$key:day", day, NotificationContent.PlanToday(p.id, p.title, names, occurrence.date))
                val firstAsk = day.plus(1, DateTimeUnit.DAY)
                val lastAsk = day.plus(FOLLOW_UP_DAYS, DateTimeUnit.DAY)
                var ask = maxOf(firstAsk, today)
                if (ask.atReminderTime() <= now) ask = ask.plus(1, DateTimeUnit.DAY)
                if (ask <= lastAsk) {
                    add("plan:$key:ask", ask, NotificationContent.PlanFollowUp(p.id, p.title, names, occurrence.date, isCircle))
                }
            }
        }

        // --- Catch-up nudges, within a budget (see [NudgePolicy]).
        val quietness = policy.quietness(today, zone)
        if (quietness == NudgePolicy.Quietness.SILENT) return out.sortedBy { it.at }

        // --- Calendar suggestions (D3): at most one summary a day, in the evening once that day's matched events are
        // over. Within the same budget as catch-ups: silent when the app has been ignored for a long time.
        calendar.filter { it.event.end > now }.groupBy { it.event.end.toLocalDate(zone) }.forEach { (day, items) ->
            if (day > end) return@forEach
            val evening = day.atTime(LocalTime(CALENDAR_HOUR, 0)).toInstant(zone)
            val at = maxOf(evening, items.maxOf { it.event.end } + CALENDAR_DELAY_MINUTES.minutes)
            val names = items.flatMap { s -> s.friends.map { it.name } }.distinct()
            out += PlannedNotification(
                "calendar:$day",
                at,
                NotificationContent.CalendarMeetups(names.take(DIGEST_NAMES), items.singleOrNull()?.event?.title, items.size),
            )
        }

        val catchUps = mutableListOf<CatchUp>()
        Relationships.meetupStatuses(friends, hangouts, today, zone, rules, interactions).forEach { s ->
            catchUps += CatchUp(
                s.dueDate,
                s.friend.displayName,
                "meet:${s.friend.id.value}",
                NotificationContent.MeetupDue(s.friend.id, s.friend.displayName, s.daysSinceLastSeen, s.cadenceDays),
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
