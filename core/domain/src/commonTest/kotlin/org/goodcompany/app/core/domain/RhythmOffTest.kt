package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.Closeness
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.usecase.CadenceRules
import org.goodcompany.app.core.domain.usecase.HomeWidget
import org.goodcompany.app.core.domain.usecase.LastSeenQuestions
import org.goodcompany.app.core.domain.usecase.NotificationPlanner
import org.goodcompany.app.core.domain.usecase.Relationships
import org.goodcompany.app.core.domain.usecase.Warmth
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #139: a custom rhythm of Off means no rhythm, even when their closeness has one. */
class RhythmOffTest {
    private val today = LocalDate(2026, 3, 10)
    private val zone = TimeZone.UTC
    private fun id(s: String) = FriendId(s)

    // Both Close (monthly) and last seen in early February, so both would be overdue; Leo's custom rhythm is Off.
    private val ana = friend("ana") { copy(closeness = Closeness.CLOSE) }
    private val leo = friend("leo") { copy(closeness = Closeness.CLOSE, meetEveryDays = Friend.RHYTHM_OFF) }
    private val people = listOf(ana, leo)
    private val hangouts = listOf(
        Hangout("a", listOf(id("ana")), at(2026, 2, 1)),
        Hangout("l", listOf(id("leo")), at(2026, 2, 1)),
    )

    @Test
    fun cadenceRules_offFromClosenessAndManual() {
        val rules = CadenceRules()
        // Off → none, even though Close is monthly.
        assertNull(rules.cadenceFor(leo))
        assertNull(rules.sourceFor(leo))
        assertTrue(leo.rhythmOff)
        assertNull(leo.customRhythm)
        // From closeness (null) → the level's rhythm.
        assertEquals(30, rules.cadenceFor(ana))
        assertEquals(CadenceRules.Source.CLOSENESS, rules.sourceFor(ana))
        assertEquals(30, rules.levelDays(leo))
        // Manual → the manual value.
        val weekly = ana.copy(meetEveryDays = 7)
        assertEquals(7, rules.cadenceFor(weekly))
        assertEquals(CadenceRules.Source.CUSTOM, rules.sourceFor(weekly))
        // Off on an Acquaintance, whose level has none, is still none.
        assertNull(rules.cadenceFor(leo.copy(closeness = Closeness.ACQUAINTANCE)))
    }

    @Test
    fun warmthIsUnchanged_itStillMeasuresAgainstTheirCloseness() {
        val rules = CadenceRules()
        assertEquals(30, rules.warmthDaysFor(leo))
        assertEquals(
            Warmth.scoreOrNull(ana, hangouts, emptyList(), 30, today, zone)?.value,
            Warmth.scoreOrNull(
                leo,
                listOf(hangouts[0].copy(friendIds = listOf(id("leo")))),
                emptyList(),
                rules.warmthDaysFor(leo),
                today,
                zone,
            )?.value,
        )
        assertNull(rules.warmthDaysFor(leo.copy(tracksKeepInTouch = false)))
    }

    @Test
    fun noCatchUpEntry_overdue_orWhosNext() {
        val statuses = Relationships.meetupStatuses(people, hangouts, today, zone)
        assertEquals(listOf(id("ana")), statuses.map { it.friend.id })
        val onlyLeo = Relationships.meetupStatuses(listOf(leo), hangouts, today, zone)
        assertNull(HomeWidget.summarize(emptyList(), onlyLeo, today, isPrivate = false).catchUp)
    }

    @Test
    fun noCatchUpNudge() {
        val planned = NotificationPlanner.plan(people, emptyList(), hangouts, at(2026, 3, 10, 8), zone, reminderHour = 9)
        assertTrue(planned.any { it.id.startsWith("meet:ana") })
        assertTrue(planned.none { it.id.startsWith("meet:leo") })
    }

    @Test
    fun neverAskedWhenTheyWereLastSeen() {
        val asked = LastSeenQuestions.pending(
            listOf(friend("new") { copy(closeness = Closeness.CLOSE) }, friend("off") { copy(closeness = Closeness.CLOSE, meetEveryDays = 0) }),
            emptyList(),
            emptyList(),
            CadenceRules(),
        )
        assertEquals(listOf(id("new")), asked.map { it.id })
    }

    @Test
    fun theListAgendaAndPageHaveNoRhythmForThem_butWarmthStays() = runTest {
        val g = TestGraph(friends = people, hangouts = hangouts)
        val list = g.observeFriends().first().associateBy { it.friend.id }
        assertNull(list.getValue(id("leo")).cadenceDays)
        assertFalse(list.getValue(id("leo")).meetupOverdue)
        assertNotNull(list.getValue(id("leo")).warmth)
        assertTrue(list.getValue(id("ana")).meetupOverdue)

        val agenda = g.observeAgenda().first()
        assertTrue(agenda.any { it is AgendaItem.MeetupOverdue && it.friend.id == id("ana") })
        assertTrue(agenda.none { it is AgendaItem.MeetupOverdue && it.friend.id == id("leo") })

        val page = assertNotNull(g.observeFriendDetail(id("leo")).first())
        assertNull(page.cadenceDays)
        assertNull(page.cadenceSource)
        assertNotNull(page.warmth)
    }
}
