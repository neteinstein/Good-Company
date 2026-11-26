package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.usecase.BackupJson
import com.neteinstein.goodcompany.core.domain.usecase.CalendarMatcher
import com.neteinstein.goodcompany.core.domain.usecase.CalendarSuggester
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** "Who's Ana?" (decision 2026-11-26): a first name several people share is never guessed. */
class CalendarWhoTest {
    // Tuesday 10 March 2026, noon UTC.
    private val now = at(2026, 3, 10, 12)
    private val zone = TimeZone.UTC
    private val tomorrow = LocalDate(2026, 3, 11)
    private val ana = friend("ana", "Ana")
    private val niece = friend("aj", "Ana Jorge")
    private val rui = friend("rui", "Rui Costa")
    private val friends = listOf(ana, niece, rui)

    private fun event(id: String, title: String, start: Instant, series: String? = null) =
        CalendarEvent(id, "personal", title, start, start + 1.hours, seriesId = series, calendarName = "personal")

    private val lunch = event("lunch", "Almoço com a Ana", at(2026, 3, 11, 13))

    private fun plan(vararg ids: String, date: LocalDate = tomorrow, status: PlanStatus = PlanStatus.PLANNED) =
        Plan("p", ids.map(::FriendId), "Almoço", date, status = status, createdAt = now)

    private fun who(event: CalendarEvent, vararg ids: String) =
        CalendarDecision(CalendarDecision.whoKey(event), CalendarDecisionKind.WHO, now, ids.map(::FriendId))

    private fun soon(events: List<CalendarEvent>, plans: List<Plan> = emptyList(), decisions: List<CalendarDecision> = emptyList()) =
        CalendarSuggester.meetingSoon(events, friends, decisions, now, zone, plans).map { it.first.value }

    private fun questions(
        events: List<CalendarEvent>,
        plans: List<Plan> = emptyList(),
        decisions: List<CalendarDecision> = emptyList(),
        hangouts: List<Hangout> = emptyList(),
    ) = CalendarSuggester.questions(events, friends, hangouts, decisions, plans, now, zone)

    // --- Matching

    @Test
    fun aSharedFirstNameIsAmbiguous_notEveryone() {
        val m = assertNotNull(CalendarMatcher.match(lunch, friends))
        assertTrue(m.friends.isEmpty())
        assertEquals(listOf("ana", "aj"), m.ambiguous.map { it.id.value })
        assertEquals(listOf("Ana"), m.names)
    }

    @Test
    fun aFullNameOrAUniqueFirstNameIsStillSure() {
        val full = assertNotNull(CalendarMatcher.match(event("e", "Lunch with Ana Jorge", now), friends))
        // "Ana Jorge" explains the "Ana": the other Ana isn't asked about.
        assertEquals(listOf("aj"), full.friends.map { it.id.value })
        assertTrue(full.ambiguous.isEmpty())
        val unique = assertNotNull(CalendarMatcher.match(event("e", "Coffee with Rui and Ana", now), friends))
        assertEquals(listOf("rui"), unique.friends.map { it.id.value })
        assertEquals(listOf("ana", "aj"), unique.ambiguous.map { it.id.value })
    }

    @Test
    fun aOneWordNameMatchesOnlyAsAWord() {
        assertNull(CalendarMatcher.match(event("e", "Banana bread", now), listOf(ana)))
        assertEquals(listOf("ana"), CalendarMatcher.match(event("e", "Ana's birthday", now), listOf(ana))?.friends?.map { it.id.value })
    }

    // --- Before you meet

    @Test
    fun theScreenshotCase_aPlanWithOneAnaPicksHer() {
        assertEquals(listOf("ana"), soon(listOf(lunch), plans = listOf(plan("ana"))))
        assertTrue(questions(listOf(lunch), plans = listOf(plan("ana"))).isEmpty())
    }

    @Test
    fun aCancelledPlanOrOneOnAnotherDayDoesNotDecide() {
        assertTrue(soon(listOf(lunch), plans = listOf(plan("ana", status = PlanStatus.CANCELLED))).isEmpty())
        assertEquals(1, questions(listOf(lunch), plans = listOf(plan("ana", date = LocalDate(2026, 3, 12)))).size)
    }

    @Test
    fun unansweredNobodyIsGuessed_andItIsAsked() {
        assertTrue(soon(listOf(lunch)).isEmpty())
        val q = questions(listOf(lunch)).single()
        assertEquals(listOf("ana", "aj"), q.candidates.map { it.id.value })
        assertEquals(tomorrow, q.date)
    }

    @Test
    fun theAnswerDecides_andNoneOfThemMeansNobody() {
        assertEquals(listOf("aj"), soon(listOf(lunch), decisions = listOf(who(lunch, "aj"))))
        assertTrue(questions(listOf(lunch), decisions = listOf(who(lunch, "aj"))).isEmpty())
        assertTrue(soon(listOf(lunch), decisions = listOf(who(lunch))).isEmpty())
        assertTrue(questions(listOf(lunch), decisions = listOf(who(lunch))).isEmpty())
    }

    @Test
    fun aRepeatingEventIsAskedOnceAndAnsweredForTheSeries() {
        val week1 = event("w1", "Padel com a Ana", now - 6.days, series = "padel")
        val week2 = event("w2", "Padel com a Ana", now + 1.days, series = "padel")
        assertEquals(listOf("w2"), questions(listOf(week1, week2)).map { it.event.id })
        val answer = who(week1, "ana")
        assertTrue(questions(listOf(week1, week2), decisions = listOf(answer)).isEmpty())
        assertEquals(listOf("ana"), soon(listOf(week2), decisions = listOf(answer)))
        assertEquals(
            listOf("ana"),
            CalendarSuggester.suggestions(listOf(week1), friends, emptyList(), listOf(answer), now, zone, null).single().friends.map {
                it.id.value
            },
        )
    }

    @Test
    fun aMeetupLoggedThatDaySettlesAPastEvent() {
        val past = event("past", "Jantar com a Ana", at(2026, 3, 6, 20))
        assertEquals(1, questions(listOf(past)).size)
        val logged = Hangout("h", listOf(FriendId("aj")), at(2026, 3, 6, 22))
        assertTrue(questions(listOf(past), hangouts = listOf(logged)).isEmpty())
    }

    @Test
    fun comingQuestionsFirst_thenEndedNewestFirst_nothingBeyondTomorrow() {
        val old = event("old", "Jantar com a Ana", now - 20.days)
        val recent = event("recent", "Café com a Ana", now - 2.days)
        val later = event("later", "Cinema com a Ana", now + 5.days)
        assertEquals(listOf("lunch", "recent", "old"), questions(listOf(old, recent, lunch, later)).map { it.event.id })
    }

    // --- Use cases and backup

    @Test
    fun answeringIsStored_backedUp_andUndone() = runTest {
        val g = TestGraph(friends, AppSettings(calendarSuggestions = true, calendarIds = listOf("personal")), clock = FixedClock(now))
        g.calendarReader.events = listOf(lunch)
        val q = g.observeCalendarQuestions().first().single()
        assertTrue(g.observeStoryBubbles().first().isEmpty())

        // Only the people asked about are kept.
        val decision = g.answerCalendarQuestion(q, setOf(FriendId("aj"), FriendId("rui")))
        assertEquals(listOf(FriendId("aj")), decision.friendIds)
        assertTrue(g.observeCalendarQuestions().first().isEmpty())
        assertEquals(listOf("aj"), g.observeStoryBubbles().first().map { it.friend.id.value })

        val backup = BackupJson.decodeFromString(com.neteinstein.goodcompany.core.domain.model.Backup.serializer(), g.exportBackup())
        assertEquals(listOf(decision), backup.calendarDecisions)

        g.undoCalendarDecision(decision)
        assertEquals(1, g.observeCalendarQuestions().first().size)
    }

    @Test
    fun noQuestionsWhileSuggestionsAreOff() = runTest {
        val g = TestGraph(friends, AppSettings(calendarSuggestions = false, calendarIds = listOf("personal")), clock = FixedClock(now))
        g.calendarReader.events = listOf(lunch)
        assertTrue(g.observeCalendarQuestions().first().isEmpty())
    }
}
