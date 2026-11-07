package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.serialization.Serializable

/** A birthday; the year is optional because address books often omit it. */
@Serializable
data class Birthday(val month: Int, val day: Int, val year: Int? = null) {
    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..31) { "day out of range: $day" }
    }

    /** Next occurrence on or after [today]. Feb 29 falls back to Feb 28 in non-leap years. */
    fun nextOccurrence(today: LocalDate): LocalDate {
        val thisYear = onYear(today.year)
        return if (thisYear >= today) thisYear else onYear(today.year + 1)
    }

    /** Age reached on [on] — null when the birth year is unknown. */
    fun ageOn(on: LocalDate): Int? = year?.let { y ->
        val hadBirthday = onYear(on.year) <= on
        on.year - y - if (hadBirthday) 0 else 1
    }

    private fun onYear(y: Int): LocalDate {
        val maxDay = daysInMonth(y, month)
        return LocalDate(y, Month(month), minOf(day, maxDay))
    }

    companion object {
        fun isLeap(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeap(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}
