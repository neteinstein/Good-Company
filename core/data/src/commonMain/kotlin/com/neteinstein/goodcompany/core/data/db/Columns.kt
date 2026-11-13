package com.neteinstein.goodcompany.core.data.db

import com.neteinstein.goodcompany.core.domain.model.Birthday
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

// How domain values are stored in columns (see the header of Friend.sq): flags as 0/1, instants as epoch
// milliseconds, dates as ISO text, enums by name. Booleans are INTEGER on purpose: the web driver can't read SQL
// booleans back (AGENTS.md gotcha 24).

internal fun Boolean.toColumn(): Long = if (this) 1L else 0L

internal fun Long.toFlag(): Boolean = this != 0L

internal fun Instant.toColumn(): Long = toEpochMilliseconds()

internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

internal fun LocalDate.toColumn(): String = toString()

internal fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

internal fun Int.toColumn(): Long = toLong()

/** A birthday from its three columns; null when month or day is missing (the schema keeps them together). */
internal fun birthdayOf(month: Long?, day: Long?, year: Long?): Birthday? =
    if (month != null && day != null) Birthday(month.toInt(), day.toInt(), year?.toInt()) else null
