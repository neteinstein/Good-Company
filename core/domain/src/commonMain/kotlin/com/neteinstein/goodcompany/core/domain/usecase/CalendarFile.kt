package com.neteinstein.goodcompany.core.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A minimal iCalendar (RFC 5545) file with one all-day event, for "Add to calendar" on the web. */
object CalendarFile {
    fun allDayEvent(uid: String, title: String, date: LocalDate, location: String?, notes: String?, stamp: Instant): String {
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
            add("DTSTART;VALUE=DATE:${date8(date)}")
            add("DTEND;VALUE=DATE:${date8(date.plus(1, DateTimeUnit.DAY))}")
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

    private fun date8(d: LocalDate) = "${d.year.toString().padStart(4, '0')}${two(d.month.ordinal + 1)}${two(d.day)}"

    private fun two(n: Int) = n.toString().padStart(2, '0')

    private fun escape(text: String) = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
}
