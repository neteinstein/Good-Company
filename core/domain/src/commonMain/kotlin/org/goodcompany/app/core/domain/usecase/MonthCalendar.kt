package org.goodcompany.app.core.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.AgendaView
import org.goodcompany.app.core.domain.repository.SettingsRepository

/**
 * Date maths for the month calendar of "Everything coming up" (issue #138). A month is named by its first day
 * ([firstOfMonth]); the grid is whole weeks, starting on the device's first day of the week.
 */
object MonthCalendar {
    /** The first day of [date]'s month. */
    fun firstOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    /** The first day of the month [months] after (or before, when negative) [date]'s month. */
    fun plusMonths(date: LocalDate, months: Int): LocalDate = firstOfMonth(date).plus(months, DateTimeUnit.MONTH)

    /** The last day of [date]'s month. */
    fun lastOfMonth(date: LocalDate): LocalDate = plusMonths(date, 1).minus(1, DateTimeUnit.DAY)

    /**
     * The weeks that show [month] (any day of it): 4 to 6 rows of 7 days, the first starting on [firstDayOfWeek]
     * on or before the 1st, the last ending on or after the month's last day. Days of the months around it fill the
     * first and last rows.
     */
    fun weeks(month: LocalDate, firstDayOfWeek: DayOfWeek): List<List<LocalDate>> {
        val first = firstOfMonth(month)
        val last = lastOfMonth(month)
        val lead = (first.dayOfWeek.ordinal - firstDayOfWeek.ordinal + 7) % 7
        var day = first.minus(lead, DateTimeUnit.DAY)
        val weeks = mutableListOf<List<LocalDate>>()
        while (day <= last) {
            weeks += (0 until 7).map { day.plus(it, DateTimeUnit.DAY) }
            day = day.plus(7, DateTimeUnit.DAY)
        }
        return weeks
    }

    /** The seven weekdays in grid order, starting on [firstDayOfWeek]. */
    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
        (0 until 7).map { DayOfWeek.entries[(firstDayOfWeek.ordinal + it) % 7] }

    /** Agenda items by the day they sit on (overdue ones are already pinned to today), in agenda order. */
    fun byDay(items: List<AgendaItem>): Map<LocalDate, List<AgendaItem>> = items.groupBy { it.date }

    /**
     * How many days ahead the agenda must reach so the grid of [month] is filled: at least the usual
     * [AgendaBuilder.DEFAULT_HORIZON_DAYS], and up to the end of the last row (seven days past the month covers it).
     */
    fun horizonFor(month: LocalDate, today: LocalDate): Int =
        maxOf(AgendaBuilder.DEFAULT_HORIZON_DAYS, today.daysUntil(lastOfMonth(month).plus(7, DateTimeUnit.DAY)))
}

/** Calendar or List on "Everything coming up" (issue #138), remembered on this device. */
class SetAgendaViewUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(view: AgendaView) = settings.update { it.copy(agendaView = view) }
}
