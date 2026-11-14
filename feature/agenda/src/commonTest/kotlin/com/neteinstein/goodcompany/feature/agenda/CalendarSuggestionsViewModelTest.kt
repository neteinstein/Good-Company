package com.neteinstein.goodcompany.feature.agenda

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogRequest
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.agenda.ui.screens.calendar.CalendarSuggestionsMessage
import com.neteinstein.goodcompany.feature.agenda.ui.screens.calendar.CalendarSuggestionsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class CalendarSuggestionsViewModelTest {
    private val now = at(2026, 3, 10, 12)
    private val lunch = CalendarEvent("lunch", "personal", "Lunch with Rui", at(2026, 3, 6, 13), at(2026, 3, 6, 14))
    private val cinema = CalendarEvent("cinema", "personal", "Ana · cinema", now - 30.days, now - 30.days + 2.hours, seriesId = "s1")
    private val g = TestGraph(
        listOf(friend("rui", "Rui Costa"), friend("ana", "Ana Sousa")),
        AppSettings(calendarSuggestions = true, calendarIds = listOf("personal", "family"), calendarOnSince = now - 7.days),
        clock = FixedClock(now),
    ).apply {
        calendarReader.calendars = listOf(DeviceCalendar("personal", "Personal"), DeviceCalendar("family", "Family"))
        calendarReader.events = listOf(lunch, cinema)
    }

    private fun vm() = CalendarSuggestionsViewModel(g.observeCalendarSuggestions, g.decideCalendarSuggestion, g.undoCalendarDecision, g.clock)

    @Test
    fun splitsRecentFromLookingBack() = runViewModelTest {
        val state = collecting(vm().state).value
        assertEquals(listOf("lunch"), state.recent.map { it.event.id })
        assertEquals(listOf("cinema"), state.lookingBack.map { it.event.id })
    }

    @Test
    fun yesLogsItOnceAndItIsGone() = runViewModelTest {
        val state = collecting(vm().state)
        val s = state.value.recent.single()
        // "Log it" opens the log sheet; saving there writes the meetup on the event's day.
        g.logInteraction(
            LogRequest(
                s.friends.map { it.id },
                LogKind.MEETUP,
                date = s.date,
                activity = s.activity,
            ),
        )
        assertTrue(state.value.recent.isEmpty())
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
    }

    @Test
    fun noAndNeverAreRememberedWithUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onNo(state.value.recent.single())
        val message = assertIs<CalendarSuggestionsMessage.Decided>(state.value.message)
        assertEquals(CalendarDecisionKind.NO, message.decision.kind)
        assertTrue(state.value.recent.isEmpty())
        vm.onUndo()
        assertNull(state.value.message)
        assertEquals(1, state.value.recent.size)

        vm.onNever(state.value.lookingBack.single())
        assertEquals(CalendarDecisionKind.NEVER, assertIs<CalendarSuggestionsMessage.Decided>(state.value.message).decision.kind)
        assertTrue(state.value.lookingBack.isEmpty())
        vm.onMessageShown()
        assertNull(state.value.message)
        assertEquals(listOf("series:s1"), g.calendarDecisionRepo.decisions.value.map { it.key })
    }
}
