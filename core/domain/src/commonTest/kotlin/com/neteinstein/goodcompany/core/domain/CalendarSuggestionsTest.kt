package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.CalendarAttendee
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.usecase.ActivityGuesser
import com.neteinstein.goodcompany.core.domain.usecase.BackupJson
import com.neteinstein.goodcompany.core.domain.usecase.CalendarMatchKind
import com.neteinstein.goodcompany.core.domain.usecase.CalendarMatcher
import com.neteinstein.goodcompany.core.domain.usecase.CalendarSuggester
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.NudgePolicy
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CalendarSuggestionsTest {
    // Tuesday 10 March 2026, noon UTC (FixedClock's default). "Last Friday" is 6 March.
    private val now = at(2026, 3, 10, 12)
    private val zone = TimeZone.UTC
    private val rui = friend("rui", "Rui Costa") { copy(emails = listOf(FriendEmail("rui@example.com"))) }
    private val marta = friend("marta", "Marta Silva") { copy(emails = listOf(FriendEmail("Marta@Example.com"))) }
    private val ana = friend("ana", "Ana Sousa")
    private val friends = listOf(rui, marta, ana)

    private fun event(
        id: String,
        title: String,
        start: Instant,
        hours: Int = 1,
        calendar: String = "personal",
        attendees: List<CalendarAttendee> = emptyList(),
        series: String? = null,
    ) = CalendarEvent(id, calendar, title, start, start + hours.hours, attendees = attendees, seriesId = series, calendarName = calendar)

    private val lunch = event("lunch", "Lunch with Rui", at(2026, 3, 6, 13))

    // --- Matching

    @Test
    fun matchesByAttendeeEmailIgnoringCase() {
        val e = event("e", "Book club", now, attendees = listOf(CalendarAttendee("M", "marta@example.com"), CalendarAttendee("", "me@x.com")))
        val m = assertNotNull(CalendarMatcher.match(e, friends))
        assertEquals(listOf("marta"), m.friends.map { it.id.value })
        assertEquals(setOf(CalendarMatchKind.EMAIL), m.by)
    }

    @Test
    fun matchesByNameInTitle() {
        val m = assertNotNull(CalendarMatcher.match(lunch, friends))
        assertEquals(listOf("rui"), m.friends.map { it.id.value })
        assertEquals(setOf(CalendarMatchKind.NAME), m.by)
    }

    @Test
    fun matchesByBothAndCollectsEveryone() {
        val e = event("e", "Dinner with Ana", now, attendees = listOf(CalendarAttendee("Rui", "RUI@example.com")))
        val m = assertNotNull(CalendarMatcher.match(e, friends))
        assertEquals(setOf("rui", "ana"), m.friends.map { it.id.value }.toSet())
        assertEquals(setOf(CalendarMatchKind.EMAIL, CalendarMatchKind.NAME), m.by)
    }

    @Test
    fun noMatchWithoutNameOrEmail() {
        assertNull(CalendarMatcher.match(event("e", "Dentist", now, attendees = listOf(CalendarAttendee("Dr", "dr@clinic.pt"))), friends))
        // A first name inside another word doesn't count.
        assertNull(CalendarMatcher.match(event("e", "Banana bread", now), friends))
    }

    @Test
    fun guessesTheActivityFromTheTitle() {
        assertEquals(HangoutActivity.LUNCH, ActivityGuesser.guess("Lunch with Rui"))
        assertEquals(HangoutActivity.DINNER, ActivityGuesser.guess("Jantar com a Ana"))
        assertEquals(HangoutActivity.COFFEE, ActivityGuesser.guess("Café"))
        assertEquals(HangoutActivity.CULTURE, ActivityGuesser.guess("Ana · cinema"))
        assertNull(ActivityGuesser.guess("Catch-up"))
        assertNull(ActivityGuesser.guess("Cinematographer meetup"))
    }

    // --- Suggestions

    private fun suggest(
        events: List<CalendarEvent>,
        hangouts: List<Hangout> = emptyList(),
        decisions: List<CalendarDecision> = emptyList(),
        onSince: Instant? = null,
    ) = CalendarSuggester.suggestions(events, friends, hangouts, decisions, now, zone, onSince)

    @Test
    fun lastFridaysLunchIsSuggestedWithItsDayAndActivity() {
        val s = suggest(listOf(lunch)).single()
        assertEquals(LocalDate(2026, 3, 6), s.date)
        assertEquals(HangoutActivity.LUNCH, s.activity)
        assertEquals(listOf("rui"), s.friends.map { it.id.value })
        assertFalse(s.isRepeating)
    }

    @Test
    fun onlyEndedEventsWithinTheLookBackWindow() {
        val running = event("running", "Coffee with Rui", now - 1.hours, hours = 2) // started an hour ago, still on
        val future = event("future", "Lunch with Ana", now + 1.days)
        val inWindow = event("old", "Lunch with Ana", now - 59.days)
        val tooOld = event("older", "Lunch with Ana", now - 61.days)
        assertEquals(listOf("old"), suggest(listOf(running, future, inWindow, tooOld)).map { it.event.id })
    }

    @Test
    fun newestFirstAndLookingBackBeforeTurnedOn() {
        val before = event("before", "Ana · cinema", now - 20.days)
        val after = lunch
        val s = suggest(listOf(before, after), onSince = now - 7.days)
        assertEquals(listOf("lunch", "before"), s.map { it.event.id })
        assertEquals(listOf(false, true), s.map { it.lookingBack })
    }

    @Test
    fun noHidesOneOccurrenceAndNeverTheWholeSeries() {
        val week1 = event("bc1", "Book club with Marta", now - 13.days, series = "bookclub")
        val week2 = event("bc2", "Book club with Marta", now - 6.days, series = "bookclub")
        val no = CalendarDecision(CalendarDecision.occurrenceKey(week1), CalendarDecisionKind.NO, now)
        assertEquals(listOf("bc2"), suggest(listOf(week1, week2), decisions = listOf(no)).map { it.event.id })
        val never = CalendarDecision(CalendarDecision.seriesKey(week2)!!, CalendarDecisionKind.NEVER, now)
        assertTrue(suggest(listOf(week1, week2, lunch), decisions = listOf(never)).none { it.event.seriesId == "bookclub" })
        assertTrue(suggest(listOf(week2)).single().isRepeating)
    }

    @Test
    fun alreadyLoggedThatDayIsNotSuggestedAgain() {
        val logged = Hangout("h", listOf(FriendId("rui")), at(2026, 3, 6, 15))
        assertTrue(suggest(listOf(lunch), hangouts = listOf(logged)).isEmpty())
        val otherDay = Hangout("h", listOf(FriendId("rui")), at(2026, 3, 5, 15))
        assertEquals(1, suggest(listOf(lunch), hangouts = listOf(otherDay)).size)
    }

    @Test
    fun longEventsAreNotMeetups() {
        assertTrue(suggest(listOf(event("trip", "Trip with Ana", now - 10.days, hours = 24 * 5))).isEmpty())
    }

    @Test
    fun upcomingMatchedEventsFeedBeforeYouMeet() {
        val tomorrow = event("t", "Drinks with Ana", now + 1.days)
        val later = event("l", "Lunch with Rui", now + 3.days)
        assertEquals(
            listOf(FriendId("ana") to LocalDate(2026, 3, 11)),
            CalendarSuggester.meetingSoon(listOf(tomorrow, later, lunch), friends, emptyList(), now, zone),
        )
    }

    // --- Notifications

    private fun planned(events: List<CalendarEvent>, policy: NudgePolicy = NudgePolicy(weeklyDigest = false)) = NotificationPlanner.plan(
        friends,
        emptyList(),
        emptyList(),
        now,
        zone,
        9,
        policy = policy,
        calendar = CalendarSuggester.candidates(events, friends, emptyList(), emptyList(), zone),
    ).filter { it.id.startsWith("calendar:") }

    @Test
    fun atMostOneSummaryADayInTheEvening() {
        val today1 = event("a", "Lunch with Rui", at(2026, 3, 10, 13))
        val today2 = event("b", "Coffee with Ana", at(2026, 3, 10, 16))
        val nextDay = event("c", "Dinner with Marta", at(2026, 3, 12, 20, 0), hours = 2)
        val n = planned(listOf(today1, today2, nextDay, lunch))
        assertEquals(listOf("calendar:2026-03-10", "calendar:2026-03-12"), n.map { it.id })
        assertEquals(at(2026, 3, 10, 20), n[0].at)
        // After a late event: half an hour after it ends.
        assertEquals(at(2026, 3, 12, 22, 30), n[1].at)
        val two = n[0].content as NotificationContent.CalendarMeetups
        assertEquals(2, two.total)
        assertNull(two.eventTitle)
        val one = n[1].content as NotificationContent.CalendarMeetups
        assertEquals("Dinner with Marta", one.eventTitle)
        assertEquals(listOf("Marta Silva"), one.names)
    }

    @Test
    fun silentWhenTheAppIsIgnored() {
        val ignored = NudgePolicy(weeklyDigest = false, lastOpenedAt = now - (NudgePolicy.SILENT_AFTER_DAYS + 1).days)
        assertTrue(planned(listOf(event("a", "Lunch with Rui", at(2026, 3, 10, 13))), ignored).isEmpty())
    }

    // --- Use cases

    private val personal = DeviceCalendar("personal", "Personal", "iCloud")
    private val family = DeviceCalendar("family", "Family")
    private val work = DeviceCalendar("work", "Work", "ana@company.com")

    private fun graph(on: Boolean = true, chosen: List<String> = listOf("personal", "family")) = TestGraph(
        friends,
        AppSettings(calendarSuggestions = on, calendarIds = chosen, calendarOnSince = now - 1.days),
        clock = FixedClock(now),
    ).apply {
        calendarReader.calendars = listOf(personal, family, work)
        calendarReader.events = listOf(
            lunch,
            event("bc", "Book club", at(2026, 3, 4, 19), calendar = "family", attendees = listOf(CalendarAttendee("", "marta@example.com"))),
            event("w", "1:1 with Ana", at(2026, 3, 9, 10), calendar = "work"),
        )
    }

    @Test
    fun withTwoCalendarsChosenOnlyTheirEventsAreSuggested() = runTest {
        val g = graph()
        assertEquals(listOf("lunch", "bc"), g.observeCalendarSuggestions().first().map { it.event.id })
    }

    @Test
    fun nothingIsReadWhileOffOrWithNothingChosen() = runTest {
        val off = graph(on = false)
        assertTrue(off.observeCalendarSuggestions().first().isEmpty())
        val none = graph(chosen = emptyList())
        assertTrue(none.observeCalendarSuggestions().first().isEmpty())
        assertEquals(0, off.calendarReader.reads + none.calendarReader.reads)
        val denied = graph().apply { calendarReader.access = false }
        assertTrue(denied.observeCalendarSuggestions().first().isEmpty())
    }

    @Test
    fun noIsRememberedAndCanBeUndone() = runTest {
        val g = graph()
        val s = g.observeCalendarSuggestions().first().first()
        val decision = assertNotNull(g.decideCalendarSuggestion(s, CalendarDecisionKind.NO))
        assertEquals(listOf("bc"), g.observeCalendarSuggestions().first().map { it.event.id })
        // A one-off event has no series to say "Never" to.
        assertNull(g.decideCalendarSuggestion(s, CalendarDecisionKind.NEVER))
        g.undoCalendarDecision(decision)
        assertEquals(2, g.observeCalendarSuggestions().first().size)
    }

    @Test
    fun loggingTheMeetupRemovesTheSuggestion() = runTest {
        val g = graph()
        val s = g.observeCalendarSuggestions().first().first()
        g.checkIn(s.friends.map { it.id }, at = s.event.start)
        assertEquals(listOf("bc"), g.observeCalendarSuggestions().first().map { it.event.id })
    }

    @Test
    fun turningOnStartsTheLookBackAndCalendarsStartUnticked() = runTest {
        val g = TestGraph(friends, clock = FixedClock(now))
        g.setCalendarSuggestions(true)
        val s = g.settingsRepo.settings.value
        assertTrue(s.calendarSuggestions)
        assertEquals(now, s.calendarOnSince)
        assertTrue(s.calendarIds.isEmpty())
        g.setCalendarChosen("personal", true)
        g.setCalendarChosen("family", true)
        g.setCalendarChosen("personal", false)
        assertEquals(listOf("family"), g.settingsRepo.settings.value.calendarIds)
        g.setCalendarSuggestions(false)
        assertFalse(g.settingsRepo.settings.value.calendarSuggestions)
    }

    @Test
    fun settingsListCalendarsOnlyWithAccess() = runTest {
        val g = graph()
        val s = g.observeCalendarSettings().first()
        assertTrue(s.hasAccess)
        assertEquals(listOf("personal", "family", "work"), s.calendars.map { it.id })
        assertEquals(setOf("personal", "family"), s.chosen)
        g.calendarReader.access = false
        g.setCalendarChosen("work", true) // a settings change reads again
        val denied = g.observeCalendarSettings().first()
        assertFalse(denied.hasAccess)
        assertTrue(denied.calendars.isEmpty())
    }

    @Test
    fun beforeYouMeetIncludesCalendarEvents() = runTest {
        val g = graph()
        g.calendarReader.events = listOf(event("t", "Drinks with Ana", now + 1.days))
        assertEquals(listOf("ana"), g.observeStoryBubbles().first().map { it.friend.id.value })
    }

    @Test
    fun keepNotificationsScheduledAddsTheCalendarSummary() = runTest {
        val g = graph()
        g.calendarReader.events = listOf(event("a", "Lunch with Rui", at(2026, 3, 10, 13)))
        val job = launch { g.keepNotificationsScheduled() }
        advanceUntilIdle()
        assertTrue(g.scheduler.scheduled.last().any { it.id == "calendar:2026-03-10" })
        job.cancel()
    }

    @Test
    fun backupsCarryDecisionsButNothingAboutThisDevicesCalendars() = runTest {
        val g = graph()
        val s = g.observeCalendarSuggestions().first().first()
        g.decideCalendarSuggestion(s, CalendarDecisionKind.NO)
        val json = g.exportBackup()
        assertFalse("Lunch with Rui" in json)
        assertFalse("personal" in json)
        val backup = BackupJson.decodeFromString(com.neteinstein.goodcompany.core.domain.model.Backup.serializer(), json)
        assertEquals(1, backup.calendarDecisions.size)
        assertFalse(backup.settings.calendarSuggestions)

        // Restoring keeps this device's calendar choices.
        g.restoreBackup(json)
        val restored = g.settingsRepo.settings.value
        assertTrue(restored.calendarSuggestions)
        assertEquals(listOf("personal", "family"), restored.calendarIds)
        assertEquals(1, g.calendarDecisionRepo.decisions.value.size)
    }
}
