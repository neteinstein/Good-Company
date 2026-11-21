package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.HomeWidget
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenQuestions
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.PeopleDashboard
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.Shortcuts
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
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

/** Issue #115: with keep-in-touch tracking off, a person has no warmth, rhythm or nudges, and the rest keeps working. */
class KeepInTouchTrackingTest {
    private val today = LocalDate(2026, 3, 10)
    private val zone = TimeZone.UTC
    private fun id(s: String) = FriendId(s)

    // Both overdue by a lot: Ana is tracked, Kid isn't (seen every day, but nothing logged for a while).
    private val ana = friend("ana") { copy(closeness = Closeness.CLOSE) }
    private val kid = friend("kid") { copy(closeness = Closeness.CLOSE, tracksKeepInTouch = false, birthday = Birthday(3, 20)) }
    private val people = listOf(ana, kid)
    private val hangouts = listOf(
        Hangout("a1", listOf(id("ana")), at(2026, 2, 1)),
        Hangout("a2", listOf(id("ana")), at(2026, 2, 8)),
        Hangout("k1", listOf(id("kid")), at(2026, 2, 1)),
        Hangout("k2", listOf(id("kid")), at(2026, 2, 8)),
    )

    @Test
    fun onByDefault() {
        assertTrue(friend("x").tracksKeepInTouch)
    }

    @Test
    fun offMeansNoRhythmEvenWithACustomOne() {
        val rules = CadenceRules()
        assertNull(rules.cadenceFor(kid.copy(meetEveryDays = 7)))
        assertNull(rules.sourceFor(kid.copy(meetEveryDays = 7)))
        assertEquals(30, rules.cadenceFor(ana))
    }

    @Test
    fun offMeansNoWarmth() {
        assertNull(Warmth.scoreOrNull(kid, hangouts, emptyList(), 7, today, zone))
        assertNotNull(Warmth.scoreOrNull(ana, hangouts, emptyList(), 7, today, zone))
    }

    @Test
    fun relationshipsLeaveThemOut_evenWithAnApproximateLastSeen() {
        val withApprox = kid.copy(approxLastSeen = LocalDate(2026, 1, 1))
        val statuses = Relationships.meetupStatuses(listOf(ana, withApprox), hangouts, today, zone)
        assertEquals(listOf(id("ana")), statuses.map { it.friend.id })
    }

    @Test
    fun noCatchUpNotifications_butTheBirthdayStays() {
        val planned = NotificationPlanner.plan(people, emptyList(), hangouts, at(2026, 3, 10, 8), zone, reminderHour = 9)
        assertTrue(planned.any { it.id.startsWith("meet:ana") })
        assertTrue(planned.none { it.id.startsWith("meet:kid") })
        assertTrue(planned.any { it.id.startsWith("bday:kid") })
    }

    // Issue #130: tracking off only stops the keep-in-touch side; their reminders and plans still ring.
    private val kidReminder = Reminder("r1", "Bring the book", listOf(id("kid")), dueAt = at(2026, 3, 12, 18), createdAt = at(2026, 3, 1))
    private val kidPlan = Plan("p1", listOf(id("kid")), "Zoo", LocalDate(2026, 3, 14), createdAt = at(2026, 3, 1))

    @Test
    fun theirRemindersAndPlansAreStillNotified() {
        val planned = NotificationPlanner.plan(
            people,
            listOf(kidReminder),
            hangouts,
            at(2026, 3, 10, 8),
            zone,
            reminderHour = 9,
            plans = listOf(kidPlan),
        )
        assertTrue(planned.any { it.id.startsWith("reminder:r1") })
        assertTrue(planned.any { it.id.startsWith("plan:") && it.id.endsWith(":day") })
    }

    @Test
    fun theirBirthdayRemindersAndPlansStillShowInTheAgenda() = runTest {
        val g = TestGraph(friends = people, hangouts = hangouts)
        g.reminderRepo.upsert(kidReminder)
        g.planRepo.upsert(kidPlan)
        val agenda = g.observeAgenda().first()
        assertTrue(agenda.any { it is AgendaItem.FriendBirthday && it.friend.id == id("kid") })
        assertTrue(agenda.any { it is AgendaItem.ReminderDue && it.reminder.id == "r1" })
        assertTrue(agenda.any { it is AgendaItem.PlanUpcoming && it.plan.id == "p1" })
        assertTrue(agenda.none { it is AgendaItem.MeetupOverdue && it.friend.id == id("kid") })
    }

    @Test
    fun neverAskedWhenLastSeen() {
        val asked = LastSeenQuestions.pending(
            listOf(
                friend("new") {
                    copy(closeness = Closeness.CLOSE)
                },
                friend("kid2") { copy(closeness = Closeness.CLOSE, tracksKeepInTouch = false) },
            ),
            emptyList(),
            emptyList(),
            CadenceRules(),
        )
        assertEquals(listOf(id("new")), asked.map { it.id })
    }

    @Test
    fun dashboardCountsThem_butNeverInWarmthOrCooling() {
        // Both cooled the same way; only Ana can be cooling.
        val d = PeopleDashboard.build(people, emptyList(), hangouts, emptyList(), AppSettings(), today, zone)
        assertEquals(2, d.people)
        assertEquals(listOf(id("ana")), d.cooling.map { it.friend.id })
        val alone = PeopleDashboard.build(listOf(kid), emptyList(), hangouts, emptyList(), AppSettings(), today, zone)
        assertNull(alone.averageWarmth)
        assertTrue(alone.trend.isEmpty())
        assertTrue(alone.cooling.isEmpty())
        assertNull(alone.byCloseness.single().warmth)
    }

    @Test
    fun widgetsWhosNextNeverShowsThem() {
        val statuses = Relationships.meetupStatuses(listOf(kid), hangouts, today, zone)
        assertNull(HomeWidget.summarize(emptyList(), statuses, today, isPrivate = false).catchUp)
    }

    @Test
    fun appShortcutsLeaveThemOut() {
        val snapshot = Shortcuts.snapshot(people, hangouts, emptyList(), hideNames = false)
        assertEquals(listOf(id("ana")), snapshot.people.map { it.id })
    }

    @Test
    fun theListAndThePageHaveNoWarmthOrOverdueForThem_andTheTimelineStays() = runTest {
        val g = TestGraph(friends = people, hangouts = hangouts)
        val list = g.observeFriends().first().associateBy { it.friend.id }
        assertNull(list.getValue(id("kid")).warmth)
        assertEquals(false, list.getValue(id("kid")).meetupOverdue)
        assertNull(list.getValue(id("kid")).cadenceDays)
        assertNotNull(list.getValue(id("ana")).warmth)
        assertTrue(list.getValue(id("ana")).meetupOverdue)

        val page = assertNotNull(g.observeFriendDetail(id("kid")).first())
        assertNull(page.warmth)
        assertNull(page.cadenceDays)
        assertEquals(2, page.hangouts.size)
        assertTrue(page.timeline.isNotEmpty())
    }

    @Test
    fun theNetworkMapHasNoWarmthForThem() = runTest {
        val g = TestGraph(friends = people, hangouts = hangouts)
        val nodes = g.observeNetworkMap().first().graph.nodes.associateBy { it.friend.id }
        assertNull(nodes.getValue(id("kid")).warmth)
        assertNotNull(nodes.getValue(id("ana")).warmth)
    }
}
