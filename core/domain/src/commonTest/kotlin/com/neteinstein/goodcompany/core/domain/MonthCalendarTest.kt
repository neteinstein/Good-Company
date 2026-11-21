package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.AgendaView
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.MonthCalendar
import com.neteinstein.goodcompany.core.domain.usecase.withoutDeviceOnly
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Issue #138: the month grid of "Everything coming up" and its entries by day. */
class MonthCalendarTest {
    @Test
    fun aMonthStartingOnMonday_rowsRunMondayToSunday() {
        // June 2026: the 1st is a Monday, the 30th a Tuesday.
        val weeks = MonthCalendar.weeks(LocalDate(2026, 6, 17), DayOfWeek.MONDAY)
        assertEquals(5, weeks.size)
        assertTrue(weeks.all { it.size == 7 && it.first().dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate(2026, 6, 1), weeks.first().first())
        assertEquals(LocalDate(2026, 7, 5), weeks.last().last())
    }

    @Test
    fun theWeekStartFollowsTheLocale() {
        // March 2026: the 1st is a Sunday.
        val sunday = MonthCalendar.weeks(LocalDate(2026, 3, 10), DayOfWeek.SUNDAY)
        assertEquals(LocalDate(2026, 3, 1), sunday.first().first())
        assertEquals(5, sunday.size)
        val monday = MonthCalendar.weeks(LocalDate(2026, 3, 10), DayOfWeek.MONDAY)
        assertEquals(LocalDate(2026, 2, 23), monday.first().first())
        assertEquals(6, monday.size)
        assertEquals(
            listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY),
            MonthCalendar.weekdays(DayOfWeek.SATURDAY).take(3),
        )
    }

    @Test
    fun aMonthSpanningSixWeeks() {
        // August 2026 starts on a Saturday and has 31 days.
        val weeks = MonthCalendar.weeks(LocalDate(2026, 8, 1), DayOfWeek.MONDAY)
        assertEquals(6, weeks.size)
        assertEquals(LocalDate(2026, 7, 27), weeks.first().first())
        assertEquals(LocalDate(2026, 9, 6), weeks.last().last())
    }

    @Test
    fun aLeapFebruary_andAFourWeekOne() {
        val leap = MonthCalendar.weeks(LocalDate(2028, 2, 1), DayOfWeek.MONDAY).flatten()
        assertTrue(LocalDate(2028, 2, 29) in leap)
        assertEquals(LocalDate(2028, 2, 29), MonthCalendar.lastOfMonth(LocalDate(2028, 2, 3)))
        // February 2026 starts on a Sunday and has 28 days: exactly four rows from Sunday.
        assertEquals(4, MonthCalendar.weeks(LocalDate(2026, 2, 14), DayOfWeek.SUNDAY).size)
    }

    @Test
    fun monthsMoveAcrossTheYear() {
        assertEquals(LocalDate(2027, 1, 1), MonthCalendar.plusMonths(LocalDate(2026, 12, 31), 1))
        assertEquals(LocalDate(2025, 11, 1), MonthCalendar.plusMonths(LocalDate(2026, 1, 15), -2))
        assertEquals(LocalDate(2026, 3, 1), MonthCalendar.firstOfMonth(LocalDate(2026, 3, 31)))
    }

    @Test
    fun theAgendaReachesTheEndOfTheMonthShown() {
        val today = LocalDate(2026, 3, 10)
        assertEquals(AgendaBuilder.DEFAULT_HORIZON_DAYS, MonthCalendar.horizonFor(LocalDate(2026, 3, 1), today))
        // Six months on: up to a week past the end of September.
        assertEquals(today.daysUntil(LocalDate(2026, 10, 7)), MonthCalendar.horizonFor(LocalDate(2026, 9, 1), today))
    }

    @Test
    fun entriesByDay_overdueCatchUpsSitOnToday() = runTest {
        val g = TestGraph(
            friends = listOf(
                friend("ana") { copy(closeness = Closeness.CLOSE) },
                friend("rui") { copy(closeness = Closeness.CLOSE, birthday = Birthday(3, 20)) },
            ),
            hangouts = listOf(Hangout("h", listOf(FriendId("ana")), at(2026, 1, 1))),
        )
        val byDay = MonthCalendar.byDay(g.observeAgenda().first())
        val today = g.clock.today()
        assertTrue(byDay.getValue(today).any { it is AgendaItem.MeetupOverdue && it.friend.id == FriendId("ana") })
        assertTrue(byDay.getValue(LocalDate(2026, 3, 20)).single() is AgendaItem.FriendBirthday)
        // The wider window brings later months in.
        val later = friend("eva") { copy(closeness = Closeness.CLOSE, birthday = Birthday(9, 2)) }
        g.friendRepo.upsert(later)
        assertTrue(g.observeAgenda().first().none { it.date == LocalDate(2026, 9, 2) })
        val wide = g.observeAgenda(MonthCalendar.horizonFor(LocalDate(2026, 9, 1), today)).first()
        assertTrue(wide.any { it is AgendaItem.FriendBirthday && it.date == LocalDate(2026, 9, 2) })
    }

    @Test
    fun theViewIsCalendarByDefault_andNeverTravelsInABackup() = runTest {
        assertEquals(AgendaView.CALENDAR, AppSettings().agendaView)
        val g = TestGraph()
        g.setAgendaView(AgendaView.LIST)
        assertEquals(AgendaView.LIST, g.settingsRepo.settings.first().agendaView)
        assertEquals(AgendaView.CALENDAR, AppSettings(agendaView = AgendaView.LIST).withoutDeviceOnly().agendaView)
    }
}
