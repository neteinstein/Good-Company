package com.neteinstein.goodcompany.core.ui.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * Small, locale-light formatters. kotlinx-datetime has no localized formatting on every target,
 * so we keep English copy here in one place (and unit-test it).
 */

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun LocalDate.shortLabel(): String = "${MONTHS[month.ordinal]} $day"

fun LocalDate.longLabel(): String = "${MONTHS[month.ordinal]} $day, $year"

fun LocalDateTime.timeLabel(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** "Today", "Tomorrow", "In 5 days", "Mar 20" — for upcoming dates. */
fun relativeDay(date: LocalDate, today: LocalDate): String = when (val d = today.daysUntil(date)) {
    0 -> "Today"
    1 -> "Tomorrow"
    -1 -> "Yesterday"
    in 2..6 -> "In $d days"
    in -6..-2 -> "${-d} days ago"
    else -> date.shortLabel()
}

/** Lower-case phrase for use mid-sentence: "today", "tomorrow", "in 4 days", "on Mar 30", "2 days ago". */
fun relativeDayPhrase(date: LocalDate, today: LocalDate): String = when (val d = today.daysUntil(date)) {
    0 -> "today"
    1 -> "tomorrow"
    -1 -> "yesterday"
    in 2..6 -> "in $d days"
    in -6..-2 -> "${-d} days ago"
    else -> "on ${date.shortLabel()}"
}

/** "just now", "3h ago", "yesterday", "12 days ago", "3 months ago", "over a year ago". */
fun relativePast(instant: Instant, now: Instant, zone: TimeZone): String {
    val minutes = (now - instant).inWholeMinutes
    if (minutes < 1) return "just now"
    if (minutes < 60) return "${minutes}m ago"
    if (minutes < 24 * 60 && instant.toLocalDateTime(zone).date == now.toLocalDateTime(zone).date) return "${minutes / 60}h ago"
    val days = instant.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date)
    return when {
        days <= 1 -> "yesterday"
        days < 14 -> "$days days ago"
        days < 60 -> "${days / 7} weeks ago"
        days < 365 -> "${days / 30} months ago"
        else -> "over a year ago"
    }
}

/** Splits a comma/newline separated entry into clean tags. */
fun parseTags(raw: String): List<String> = raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
