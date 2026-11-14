package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/*
 * Calendar suggestions (decision D3, issue #45). Events are read on the device from the calendars the user chose and
 * live only in memory: they are never stored, never put in a backup and never given to the AI. Only the user's answers
 * ([CalendarDecision]) are kept, and they hold ids, not event details.
 */

/** A calendar on the device the user can let the app read. */
data class DeviceCalendar(
    val id: String,
    val name: String,
    /** The account or source it belongs to ("iCloud", "ana@company.com"); empty when unknown. */
    val account: String = "",
    /** The calendar's own colour as 0xAARRGGBB, when the platform has one. */
    val color: Long? = null,
)

data class CalendarAttendee(val name: String = "", val email: String = "")

/** One occurrence of an event, as read from a device calendar. */
data class CalendarEvent(
    /** Unique per occurrence on this device. */
    val id: String,
    val calendarId: String,
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean = false,
    val attendees: List<CalendarAttendee> = emptyList(),
    /** Shared by every occurrence of a repeating event; null for a one-off. */
    val seriesId: String? = null,
    val calendarName: String = "",
)

@Serializable
enum class CalendarDecisionKind {
    /** "No" for one occurrence. */
    NO,

    /** "Never for this series": no more suggestions from a repeating event. */
    NEVER,
}

/**
 * A remembered answer to a calendar suggestion. [key] is [CalendarDecision.occurrenceKey] for [CalendarDecisionKind.NO]
 * and [CalendarDecision.seriesKey] for [CalendarDecisionKind.NEVER]. Part of the backup.
 */
@Serializable
data class CalendarDecision(val key: String, val kind: CalendarDecisionKind, val decidedAt: Instant) {
    companion object {
        fun occurrenceKey(event: CalendarEvent) = "event:${event.id}@${event.start.epochSeconds}"

        fun seriesKey(event: CalendarEvent): String? = event.seriesId?.let { "series:$it" }
    }
}
