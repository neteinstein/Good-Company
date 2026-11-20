package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * "Remind me to book tickets for X to go with Y." It can be about people, a circle ([circleId], issue #88), or both,
 * like a [Plan]. A circle reminder is for the circle's members as they are now ([peopleIds]), so someone who joins later
 * sees it too; a deleted circle reads as none, and the reminder keeps its own people.
 *
 * Issue #99: it works like a reminders app. It can be snoozed ([snoozedUntil] moves when it shows and rings, [dueAt]
 * stays), repeat ([repeat]: done rolls it to its next date instead of closing it), be [important] (kept on screen when
 * "Keep important ones on screen" is on, issue #98), and remembers when it was done ([completedAt]). Completing one
 * never asks "did you meet?": a reminder is not a meetup, even when it names people.
 */
@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val friendIds: List<FriendId> = emptyList(),
    val dueAt: Instant? = null,
    val done: Boolean = false,
    val createdAt: Instant,
    /** Missing in backups made before circle reminders: those restore with none. */
    val circleId: String? = null,
    /** Until when it is snoozed: it shows and rings then instead of at [dueAt]. Missing in older backups. */
    val snoozedUntil: Instant? = null,
    /** Kept on screen while it is due (issue #98's persistent notifications). */
    val important: Boolean = false,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    /** When it was marked done; null while open (and for reminders done before issue #99). */
    val completedAt: Instant? = null,
) {
    /** Who it is for: its own people, then the current members of its circle (when that circle is in [circles]). */
    fun peopleIds(circles: List<FriendGroup>): List<FriendId> {
        val circle = circleId?.let { id -> circles.firstOrNull { it.id == id } } ?: return friendIds
        return (friendIds + circle.memberIds).distinct()
    }

    /** Whether it is for [friend] ([peopleIds]). */
    fun isFor(friend: FriendId, circles: List<FriendGroup>): Boolean = friend in peopleIds(circles)

    /** When it shows and rings: the snooze when there is one, else its due time; null = no date. */
    val nextAt: Instant? get() = snoozedUntil ?: dueAt

    val isSnoozed: Boolean get() = snoozedUntil != null

    val isRepeating: Boolean get() = repeat != ReminderRepeat.NONE && dueAt != null

    /**
     * The next due time of a repeating reminder: one [repeat] step after [dueAt], at the same local time, and past
     * [now] (a reminder left for weeks skips the dates it missed). Null for one that doesn't repeat or has no date.
     */
    fun nextDueAfter(now: Instant, zone: TimeZone): Instant? {
        val due = dueAt ?: return null
        val start = due.toLocalDateTime(zone)
        var step = 1
        while (true) {
            val date = when (repeat) {
                ReminderRepeat.NONE -> return null
                ReminderRepeat.DAILY -> start.date.plus(step, DateTimeUnit.DAY)
                ReminderRepeat.WEEKLY -> start.date.plus(step * DAYS_PER_WEEK, DateTimeUnit.DAY)
                // The 31st falls on the last day of a shorter month (and stays there from then on).
                ReminderRepeat.MONTHLY -> start.date.plus(step, DateTimeUnit.MONTH)
                ReminderRepeat.YEARLY -> start.date.plus(step, DateTimeUnit.YEAR)
            }
            val next = kotlinx.datetime.LocalDateTime(date, start.time).toInstant(zone)
            if (next > now) return next
            step++
        }
    }
}

private const val DAYS_PER_WEEK = 7

/** How often a reminder comes back (issue #99). Stored by name; an unknown name reads as [NONE]. */
enum class ReminderRepeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** "Snooze" on a reminder (issue #99): the presets, or a time picked by hand. */
sealed interface ReminderSnooze {
    data object OneHour : ReminderSnooze

    /** This evening at [com.neteinstein.goodcompany.core.domain.usecase.SnoozeReminderUseCase.TONIGHT_HOUR]. */
    data object Tonight : ReminderSnooze

    /** Tomorrow morning. */
    data object Tomorrow : ReminderSnooze

    /** A week from today, in the morning. */
    data object NextWeek : ReminderSnooze

    data class Until(val at: Instant) : ReminderSnooze
}
