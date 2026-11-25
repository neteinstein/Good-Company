package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A meetup you've planned. It doesn't count as "met" until you confirm it happened: the day after, the app asks
 * "Did you meet?" and a yes turns it into a check-in.
 *
 * A plan can be with a whole circle ([circleId], F20) as well as with individual people ([friendIds]); at least one
 * of the two is set. With a [repeat] it is a series (issue #43): [date] is then the **open occurrence** (the oldest
 * one not answered yet), and answering or skipping it moves [date] on to the next one ([closeOccurrence]).
 *
 * A plan can have a time of day ([time], issue #167), the same for every occurrence. It only shows on screens and in the
 * notification text and makes Add to calendar a timed event; without one everything treats the plan as a day.
 *
 * A plan made from a shared maps link (issue #170) keeps that [mapLink] and, when the link had them, the coordinates.
 */
@Serializable
data class Plan(
    val id: String,
    val friendIds: List<FriendId>,
    val title: String,
    val date: LocalDate,
    val place: String? = null,
    /** The AI idea this came from, if any. */
    val ideaId: String? = null,
    val status: PlanStatus = PlanStatus.PLANNED,
    val createdAt: Instant,
    /** The circle the plan is with (its members are asked about, unticked, after each occurrence). */
    val circleId: String? = null,
    /** What you're doing (F3); carried into the check-in. */
    val activity: HangoutActivity? = null,
    /** One of your own activities, by name (issue #121), when [activity] is null. */
    val activityName: String? = null,
    val repeat: Repeat? = null,
    /** Later occurrences of the series taken out one by one ("Skip this one"), after [date]. */
    val skippedDates: List<LocalDate> = emptyList(),
    /** The time of day (issue #167), for every occurrence; null = just the day. */
    val time: LocalTime? = null,
    /**
     * The maps link the plan was made from (issue #170, a share), opened in the maps app from the plan's menu. The app
     * never fetches it.
     */
    val mapLink: String? = null,
    /** The place's coordinates when that link carried them (issue #170), so the meetup logged when it closes gets them. */
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** [latitude] and [longitude] together, or null. */
    val location: GeoPoint? get() = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

    val isRecurring: Boolean get() = repeat != null

    val activityChoice: ActivityChoice? get() = ActivityChoice.of(activity, activityName)

    /** With more than one person or a circle: only then "I didn't go" is offered (issue #100). */
    val isGroup: Boolean get() = circleId != null || friendIds.size > 1

    /** The open occurrences in [from]..[to], oldest first: [date] and, for a series, the ones after it. */
    fun occurrences(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (status != PlanStatus.PLANNED) return emptyList()
        val rule = repeat ?: return listOf(date).filter { it in from..to }
        val out = mutableListOf<LocalDate>()
        var d: LocalDate? = date
        while (d != null && d <= to && out.size < MAX_OCCURRENCES) {
            if (d >= from && d !in skippedDates) out += d
            d = rule.after(d)
        }
        return out
    }

    /** The open occurrence after [day] (null: the series has ended, or this is a one-off plan). */
    fun nextOccurrenceAfter(day: LocalDate): LocalDate? {
        val rule = repeat ?: return null
        var d = rule.after(day)
        while (d != null && d in skippedDates) d = rule.after(d)
        return d
    }

    /**
     * The plan once the occurrence on [day] is answered ([happened]) or skipped. The open one moves the series on to
     * the next occurrence; a later one is just taken out. A one-off plan (or the last of a series) closes.
     */
    fun closeOccurrence(day: LocalDate, happened: Boolean): Plan =
        closeOccurrence(day, if (happened) PlanStatus.HAPPENED else PlanStatus.SKIPPED)

    /**
     * The plan once the occurrence on [day] is closed as [status] (issue #100: happened, didn't happen, I didn't go,
     * cancelled). The open one moves the series on to the next occurrence; a later one is just taken out. A one-off
     * plan (or the last of a series) closes with [status].
     */
    fun closeOccurrence(day: LocalDate, status: PlanStatus): Plan {
        require(status != PlanStatus.PLANNED) { "A plan closes with an answer" }
        val closed = copy(status = status)
        if (repeat == null) return if (day == date) closed else this
        if (day != date) {
            return if (day > date && day in occurrences(day, day)) copy(skippedDates = (skippedDates + day).sorted()) else this
        }
        val next = nextOccurrenceAfter(date) ?: return closed
        return copy(date = next, skippedDates = skippedDates.filter { it > next })
    }
}

/** A guard against runaway loops (a daily plan over a long window). */
private const val MAX_OCCURRENCES = 400

/**
 * Where a plan stands (issue #100). Only [PLANNED] is open. [HAPPENED] logged a check-in; [SKIPPED] is "Didn't happen";
 * [NOT_ATTENDED] is "I didn't go" (a group or circle plan met without me: no check-in, no warmth, no rhythm);
 * [CANCELLED] is kept and shown struck through. Stored by name with no `CHECK` (issue #72); an unknown name reads as
 * [PLANNED].
 */
enum class PlanStatus { PLANNED, HAPPENED, SKIPPED, CANCELLED, NOT_ATTENDED }

/** The answers in the "How did it go?" sheet (issue #100). */
enum class PlanAnswer { HAPPENED, DIDNT_HAPPEN, NOT_ATTENDED, CANCEL, DELETE }

enum class RepeatUnit { DAY, WEEK, MONTH }

/**
 * "Every [every] days / weeks / months", counted from [start] (the first occurrence) so month ends stay put
 * (31 Jan → 28 Feb → 31 Mar), up to [until] when set.
 */
@Serializable
data class Repeat(
    val every: Int,
    val unit: RepeatUnit,
    val start: LocalDate,
    val until: LocalDate? = null,
) {
    init {
        require(every >= 1) { "every must be at least 1" }
    }

    /** The [index]th occurrence (0 = [start]). */
    fun occurrence(index: Int): LocalDate = when (unit) {
        RepeatUnit.DAY -> start.plus(index * every, DateTimeUnit.DAY)
        RepeatUnit.WEEK -> start.plus(index * every * DAYS_PER_WEEK, DateTimeUnit.DAY)
        RepeatUnit.MONTH -> start.plus(index * every, DateTimeUnit.MONTH)
    }

    /** The first occurrence after [day], or null when that's past [until]. */
    fun after(day: LocalDate): LocalDate? {
        val step = when (unit) {
            RepeatUnit.DAY -> every
            RepeatUnit.WEEK -> every * DAYS_PER_WEEK
            RepeatUnit.MONTH -> every
        }
        val elapsed = if (unit == RepeatUnit.MONTH) start.monthsUntil(day) else start.daysUntil(day)
        var index = maxOf(0, elapsed / step)
        var d = occurrence(index)
        while (d <= day) d = occurrence(++index)
        return d.takeIf { until == null || it <= until }
    }
}

private const val DAYS_PER_WEEK = 7

/** One occurrence of a plan, as a compact id for notification actions: `<planId>@<date>`. */
data class PlanOccurrence(val planId: String, val date: LocalDate?) {
    fun encode(): String = if (date == null) planId else "$planId@$date"

    companion object {
        fun decode(raw: String): PlanOccurrence {
            val at = raw.lastIndexOf('@')
            if (at < 0) return PlanOccurrence(raw, null)
            val date = runCatching { LocalDate.parse(raw.substring(at + 1)) }.getOrNull() ?: return PlanOccurrence(raw, null)
            return PlanOccurrence(raw.substring(0, at), date)
        }
    }
}
