package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * One line of the timeline on a person's Together tab (issue #117): everything dated about them. [Entry] wraps the
 * history the full timeline shows; the others are what used to sit in the "Coming up" card, and their past.
 */
sealed interface TogetherItem {
    /** Stable across rebuilds, for list keys. */
    val key: String
    val at: Instant

    /** A meetup, contact, note, news, gift given or share: tap edits, ⋮ deletes (issue #102). */
    data class Entry(val entry: TimelineEntry) : TogetherItem {
        override val key get() = entry.key
        override val at get() = entry.at
    }

    /** A plan still open (upcoming, or past its date and waiting for "How did it go?"), with #100's actions. */
    data class OpenPlan(val plan: Plan, override val at: Instant) : TogetherItem {
        override val key get() = "plan-${plan.id}"
    }

    /** A plan that didn't happen: skipped, cancelled or not attended. One that happened shows as its meetup. */
    data class ClosedPlan(val plan: Plan, override val at: Instant) : TogetherItem {
        override val key get() = "plan-${plan.id}"
    }

    /** An open reminder for them or their circles (issue #99), with Done · Snooze · Edit · Delete. */
    data class OpenReminder(val reminder: Reminder, override val at: Instant) : TogetherItem {
        override val key get() = "reminder-${reminder.id}"
    }

    /** A reminder marked done, at when it was done (or was due). */
    data class DoneReminder(val reminder: Reminder, override val at: Instant) : TogetherItem {
        override val key get() = "reminder-${reminder.id}"
    }

    /** Their birthday on [date]; [age] when the birth year is known. */
    data class Birthday(val date: LocalDate, val age: Int?, override val at: Instant) : TogetherItem {
        override val key get() = "birthday-$date"
    }

    /** One of their moments (an important event). */
    data class Moment(val event: ImportantEvent, override val at: Instant) : TogetherItem {
        override val key get() = "moment-${event.id}"
    }
}

/**
 * The Together timeline (issue #117), top to bottom: "Make plans", then [upcoming] (soonest last, so the nearest sits
 * right above "Today"), then "Today", then [past] (newest first, at most [PersonTimeline.SHOWN]); [hasMore] adds the
 * fade and "Show all".
 */
data class TogetherTimeline(
    val upcoming: List<TogetherItem> = emptyList(),
    val past: List<TogetherItem> = emptyList(),
    val hasMore: Boolean = false,
)

object PersonTimeline {
    /** How many past entries the Together tab shows before "Show all". */
    const val SHOWN = 12

    /** Birthdays and moments this many days ahead sit above "Today", next to plans. */
    const val UPCOMING_DAYS = 30

    /** Date-only things sort as if at noon, like gifts on the full timeline. */
    private val NOON = LocalTime(12, 0)

    /**
     * Builds the Together timeline for [friend]. [timeline] is their history ([Timeline.build]); [plans] every plan with
     * them or a circle they're in (any status); [reminders] theirs and their circles' (open or done); [attachments]
     * what was saved for them (shown when not already on a meetup or contact).
     */
    fun build(
        friend: Friend,
        timeline: List<TimelineEntry>,
        plans: List<Plan>,
        reminders: List<Reminder>,
        attachments: List<Attachment>,
        today: LocalDate,
        zone: TimeZone,
        shown: Int = SHOWN,
    ): TogetherTimeline {
        fun noon(d: LocalDate) = d.atTime(NOON).toInstant(zone)
        val horizon = today.plus(DatePeriod(days = UPCOMING_DAYS))

        val upcoming = buildList<TogetherItem> {
            plans.filter { it.status == PlanStatus.PLANNED }.forEach { add(TogetherItem.OpenPlan(it, noon(it.date))) }
            reminders.filterNot { it.done }.forEach { r ->
                // Undated ones sit furthest up, under "Make plans": they have no day to sort by.
                add(TogetherItem.OpenReminder(r, r.snoozedUntil ?: r.dueAt ?: Instant.DISTANT_FUTURE))
            }
            friend.birthday?.nextOccurrence(today)?.takeIf { it <= horizon }?.let { d ->
                add(TogetherItem.Birthday(d, friend.birthday.ageOn(d), noon(d)))
            }
            friend.importantEvents.filter { it.date in today..horizon }.forEach { add(TogetherItem.Moment(it, noon(it.date))) }
        }.sortedWith(compareByDescending<TogetherItem> { it.at }.thenBy { it.key })

        val shownOnLogs = timeline.mapNotNull {
            when (it) {
                is TimelineEntry.Meetup -> it.attachment?.id
                is TimelineEntry.Contact -> it.attachment?.id
                is TimelineEntry.Share -> it.attachment.id
                else -> null
            }
        }.toSet()
        val history = timeline.map { TogetherItem.Entry(it) }
        val oldest = history.minOfOrNull { it.at.toLocalDate(zone) }
        // Past birthdays back to the oldest thing logged, and always the last year's: they are dated, so they show.
        val since = listOfNotNull(oldest, today.minus(DatePeriod(years = 1))).min()
        val past = buildList<TogetherItem> {
            addAll(history)
            attachments.filter { it.friendId == friend.id && it.id !in shownOnLogs }
                .forEach { add(TogetherItem.Entry(TimelineEntry.Share(it))) }
            plans.filter { it.status in CLOSED && it.date <= today }.forEach { add(TogetherItem.ClosedPlan(it, noon(it.date))) }
            reminders.filter { it.done }.forEach { r ->
                val at = r.completedAt ?: r.dueAt ?: return@forEach
                add(TogetherItem.DoneReminder(r, at))
            }
            friend.birthday?.let { b ->
                (since.year..today.year).map { b.onYear(it) }.filter { it in since..<today }
                    .forEach { d -> add(TogetherItem.Birthday(d, b.ageOn(d), noon(d))) }
            }
            friend.importantEvents.filter { it.date < today }.forEach { add(TogetherItem.Moment(it, noon(it.date))) }
        }.sortedWith(compareByDescending<TogetherItem> { it.at }.thenBy { it.key })

        return TogetherTimeline(upcoming, past.take(shown), past.size > shown)
    }

    private val CLOSED = setOf(PlanStatus.SKIPPED, PlanStatus.CANCELLED, PlanStatus.NOT_ATTENDED)
}
