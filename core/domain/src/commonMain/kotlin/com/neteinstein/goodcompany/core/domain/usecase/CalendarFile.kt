package com.neteinstein.goodcompany.core.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A minimal iCalendar (RFC 5545) file with one event, for "Add to calendar" on the web: all day, or (issue #167) at
 * [time] for [minutes]. A timed event is written in floating local time (no zone), so it lands at that time in the
 * calendar of whoever opens it, the same time the app shows.
 */
object CalendarFile {
    fun allDayEvent(uid: String, title: String, date: LocalDate, location: String?, notes: String?, stamp: Instant): String =
        event(uid, title, date, null, location, notes, stamp)

    fun event(
        uid: String,
        title: String,
        date: LocalDate,
        time: LocalTime?,
        location: String?,
        notes: String?,
        stamp: Instant,
        minutes: Int = DEFAULT_MINUTES,
    ): String {
        val utc = stamp.toLocalDateTime(TimeZone.UTC)
        val dtStamp = "${date8(utc.date)}T${two(utc.hour)}${two(utc.minute)}${two(utc.second)}Z"
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:-//Good Company//EN")
            add("CALSCALE:GREGORIAN")
            add("BEGIN:VEVENT")
            add("UID:$uid@good-company")
            add("DTSTAMP:$dtStamp")
            if (time == null) {
                add("DTSTART;VALUE=DATE:${date8(date)}")
                add("DTEND;VALUE=DATE:${date8(date.plus(1, DateTimeUnit.DAY))}")
            } else {
                val start = LocalDateTime(date, LocalTime(time.hour, time.minute))
                // Wall-clock arithmetic: UTC has no gaps, so 23:30 + 1 h is 00:30 the next day.
                val end = (start.toInstant(TimeZone.UTC) + minutes.minutes).toLocalDateTime(TimeZone.UTC)
                add("DTSTART:${dateTime(start)}")
                add("DTEND:${dateTime(end)}")
            }
            add("SUMMARY:${escape(title)}")
            location?.takeIf { it.isNotBlank() }?.let { add("LOCATION:${escape(it)}") }
            notes?.takeIf { it.isNotBlank() }?.let { add("DESCRIPTION:${escape(it)}") }
            add("END:VEVENT")
            add("END:VCALENDAR")
        }
        return lines.joinToString("\r\n", postfix = "\r\n")
    }

    fun fileName(title: String): String =
        title.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").split('-').filter { it.isNotEmpty() }
            .joinToString("-").take(MAX_NAME).ifEmpty { "plan" } + ".ics"

    private const val MAX_NAME = 40

    /** How long a timed event lasts when nothing else is said. */
    const val DEFAULT_MINUTES = 60

    private fun dateTime(t: LocalDateTime) = "${date8(t.date)}T${two(t.hour)}${two(t.minute)}00"

    private fun date8(d: LocalDate) = "${d.year.toString().padStart(4, '0')}${two(d.month.ordinal + 1)}${two(d.day)}"

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun escape(text: String) = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
