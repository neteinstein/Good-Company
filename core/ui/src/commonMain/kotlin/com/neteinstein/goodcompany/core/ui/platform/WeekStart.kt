package com.neteinstein.goodcompany.core.ui.platform

import kotlinx.datetime.DayOfWeek

/** The first day of the week in the device's locale (issue #138: the month calendar starts its rows on it). */
expect fun firstDayOfWeek(): DayOfWeek

/** A platform weekday number where 1 is Sunday and 7 is Saturday (Android `Calendar`, iOS `NSCalendar`). */
internal fun dayOfWeekFromSundayFirst(number: Int): DayOfWeek =
    if (number in 1..7) DayOfWeek(((number + 5) % 7) + 1) else DayOfWeek.MONDAY

/** An ISO weekday number, 1 Monday … 7 Sunday (the web's `Intl.Locale` week info); Monday when unknown. */
internal fun dayOfWeekFromIso(number: Int): DayOfWeek = if (number in 1..7) DayOfWeek(number) else DayOfWeek.MONDAY
