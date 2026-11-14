package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import com.neteinstein.goodcompany.core.domain.model.friendIds
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.NudgePolicy
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #43 (F20): plans with a circle, recurring plans, "Did you meet?" per occurrence. */
class RecurringPlansTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val crew = FriendGroup("crew", "Climbing crowd", "🧗", listOf(ana, rui))

    private fun plan(
        date: LocalDate,
        repeat: Repeat? = null,
        skipped: List<LocalDate> = emptyList(),
        circleId: String? = null,
        friendIds: List<FriendId> = emptyList(),
    ) = Plan("p", friendIds, "Climbing", date, createdAt = at(2026, 3, 1), circleId = circleId, repeat = repeat, skippedDates = skipped)

    // --- Occurrences ----------------------------------------------------------------------------------------------

    @Test
    fun everyFifteenDays_fromTheStart_untilTheEnd() {
        val start = LocalDate(2026, 3, 1)
        val p = plan(start, Repeat(15, RepeatUnit.DAY, start, until = LocalDate(2026, 4, 15)))
        assertEquals(
            listOf(LocalDate(2026, 3, 1), LocalDate(2026, 3, 16), LocalDate(2026, 3, 31), LocalDate(2026, 4, 15)),
            p.occurrences(start, LocalDate(2026, 12, 31)),
        )
        assertNull(p.nextOccurrenceAfter(LocalDate(2026, 4, 15)), "the series ends on its end date")
    }

    @Test
    fun monthly_keepsTheMonthEnd() {
        val start = LocalDate(2026, 1, 31)
        val p = plan(start, Repeat(1, RepeatUnit.MONTH, start))
        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2026, 3, 31), LocalDate(2026, 4, 30)),
            p.occurrences(start, LocalDate(2026, 4, 30)),
        )
        // Counted from the start, so a short month doesn't drag the rest to the 28th.
        assertEquals(LocalDate(2026, 3, 31), p.repeat!!.after(LocalDate(2026, 2, 28)))
    }

    @Test
    fun weekly_andTheWindowCutsBothEnds() {
        val start = LocalDate(2026, 3, 2)
        val p = plan(start, Repeat(2, RepeatUnit.WEEK, start))
        assertEquals(listOf(LocalDate(2026, 3, 16), LocalDate(2026, 3, 30)), p.occurrences(LocalDate(2026, 3, 10), LocalDate(2026, 4, 12)))
    }

    @Test
    fun skippingALaterOne_keepsTheSeries() {
        val start = LocalDate(2026, 3, 1)
        val p = plan(start, Repeat(15, RepeatUnit.DAY, start))
        val skipped = p.closeOccurrence(LocalDate(2026, 3, 16), happened = false)
        assertEquals(PlanStatus.PLANNED, skipped.status)
        assertEquals(start, skipped.date)
        assertEquals(listOf(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)), skipped.occurrences(start, LocalDate(2026, 4, 1)))
        // Answering the open one jumps over the skipped date.
        val next = skipped.closeOccurrence(start, happened = true)
        assertEquals(LocalDate(2026, 3, 31), next.date)
        assertTrue(next.skippedDates.isEmpty())
        // A date that isn't an occurrence changes nothing.
        assertEquals(next, next.closeOccurrence(LocalDate(2026, 4, 2), happened = false))
        assertEquals(next, next.closeOccurrence(LocalDate(2026, 3, 1), happened = false))
    }

    @Test
    fun theLastOccurrence_closesTheSeries_andAOneOffClosesAsBefore() {
        val start = LocalDate(2026, 3, 1)
        val p = plan(start, Repeat(15, RepeatUnit.DAY, start, until = LocalDate(2026, 3, 10)))
        assertEquals(PlanStatus.HAPPENED, p.closeOccurrence(start, happened = true).status)
        val once = plan(start)
        assertEquals(PlanStatus.SKIPPED, once.closeOccurrence(start, happened = false).status)
        assertEquals(once, once.closeOccurrence(LocalDate(2026, 3, 2), happened = false))
        assertTrue(once.copy(status = PlanStatus.SKIPPED).occurrences(start, start).isEmpty())
    }

    @Test
    fun occurrenceIds_roundTrip() {
        assertEquals("p@2026-03-16", PlanOccurrence("p", LocalDate(2026, 3, 16)).encode())
        assertEquals(PlanOccurrence("p", LocalDate(2026, 3, 16)), PlanOccurrence.decode("p@2026-03-16"))
        assertEquals(PlanOccurrence("p", null), PlanOccurrence.decode("p"))
        assertEquals(PlanOccurrence("odd@id", null), PlanOccurrence.decode("odd@id"))
        assertFailsWith<IllegalArgumentException> { Repeat(0, RepeatUnit.DAY, LocalDate(2026, 3, 1)) }
    }

    // --- Saving, answering ----------------------------------------------------------------------------------------

    @Test
    fun aPlanWithACircle_needsNoPeople_andCountsFromItsDate() = runTest {
        val g = TestGraph(listOf(friend("ana"), friend("rui")))
        g.groupRepo.upsert(crew)
        val saved = g.savePlan.save(
            null,
            PlanInput(
                "Climbing",
                emptyList(),
                LocalDate(2026, 3, 14),
                circleId = "crew",
                activity = HangoutActivity.SPORTS,
                repeat = Repeat(15, RepeatUnit.DAY, LocalDate(2000, 1, 1), until = LocalDate(2026, 3, 1)),
            ),
        )
        assertEquals(Repeat(15, RepeatUnit.DAY, LocalDate(2026, 3, 14)), saved.repeat, "starts on the date; an end before it is dropped")
        assertEquals("crew", saved.circleId)
        assertEquals(listOf(saved), g.observePlans.forCircle("crew").first())
        assertEquals(listOf(saved), g.observePlans.forFriend(rui).first(), "a member sees the circle's plan")
        assertTrue(g.observePlans.forFriend(FriendId("zoe")).first().isEmpty())
        assertEquals(listOf(saved), g.observeFriendDetail(ana).first()!!.plans)
        assertFailsWith<IllegalArgumentException> { g.savePlan.save(null, PlanInput("x", emptyList(), LocalDate(2026, 3, 20))) }
    }

    @Test
    fun didYouMeet_perOccurrence_logsThatDay_andMovesTheSeriesOn() = runTest {
        val g = TestGraph(listOf(friend("ana"), friend("rui")))
        val start = LocalDate(2026, 2, 20)
        val p = g.savePlan.save(
            null,
            PlanInput(
                "Climbing",
                listOf(ana),
                start,
                place = "Fábrica",
                activity = HangoutActivity.SPORTS,
                repeat = Repeat(7, RepeatUnit.DAY, start),
            ),
        )
        // Answer the second occurrence first (out of order): it is taken out, the first one stays open.
        g.confirmPlan(p.id, happened = true, day = LocalDate(2026, 2, 27))
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(at(2026, 2, 27), h.at)
        assertEquals(HangoutActivity.SPORTS, h.activity)
        assertEquals(start, g.planRepo.plans.value.single().date)
        // "We didn't go" to the first: the series moves past the answered one.
        g.confirmPlan(p.id, happened = false)
        assertEquals(LocalDate(2026, 3, 6), g.planRepo.plans.value.single().date)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
        // Not an occurrence: ignored.
        g.confirmPlan(p.id, happened = true, day = LocalDate(2026, 3, 7))
        assertEquals(LocalDate(2026, 3, 6), g.planRepo.plans.value.single().date)
        // The Log sheet already logged it: close without a second check-in.
        g.confirmPlan(p.id, happened = true, day = LocalDate(2026, 3, 6), logCheckIn = false)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
        assertEquals(LocalDate(2026, 3, 13), g.planRepo.plans.value.single().date)
    }

    @Test
    fun notificationActions_carryTheOccurrence() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        val start = LocalDate(2026, 3, 1)
        val p = g.savePlan.save(null, PlanInput("Run", listOf(ana), start, repeat = Repeat(1, RepeatUnit.WEEK, start)))
        g.handleNotificationAction(NotificationAction(NotificationAction.Kind.PLAN_SKIPPED, PlanOccurrence(p.id, LocalDate(2026, 3, 8)).encode()))
        assertEquals(listOf(LocalDate(2026, 3, 8)), g.planRepo.plans.value.single().skippedDates)
        g.handleNotificationAction(NotificationAction(NotificationAction.Kind.PLAN_HAPPENED, PlanOccurrence(p.id, start).encode()))
        assertEquals(LocalDate(2026, 3, 15), g.planRepo.plans.value.single().date)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
    }

    // --- Agenda and notifications ---------------------------------------------------------------------------------

    @Test
    fun agenda_listsEachOccurrence_withTheCirclesMembers() {
        val today = LocalDate(2026, 3, 10)
        val start = LocalDate(2026, 3, 1)
        val p = plan(start, Repeat(15, RepeatUnit.DAY, start), skipped = listOf(LocalDate(2026, 3, 31)), circleId = "crew")
        val items = AgendaBuilder.build(
            listOf(friend("ana"), friend("rui")),
            emptyList(),
            emptyList(),
            today,
            TimeZone.UTC,
            horizonDays = 40,
            groups = listOf(crew),
            plans = listOf(p),
        ).filter { it is AgendaItem.PlanUpcoming || it is AgendaItem.PlanToConfirm }
        val ask = assertIs<AgendaItem.PlanToConfirm>(items.first())
        assertEquals(start, ask.occurrence)
        assertEquals(today, ask.date)
        assertEquals(crew, ask.circle)
        val upcoming = items.drop(1).map { assertIs<AgendaItem.PlanUpcoming>(it) }
        assertEquals(listOf(LocalDate(2026, 3, 16), LocalDate(2026, 4, 15)), upcoming.map { it.occurrence })
        assertEquals(LocalDate(2026, 4, 15), upcoming.first().nextAfter, "skips the skipped one")
        assertEquals(setOf(ana, rui), upcoming.first().friendIds)
        assertEquals(upcoming.map { it.key }.distinct().size, upcoming.size)
    }

    @Test
    fun notifications_onEachDay_andAskTheDayAfter_neverThrottled() {
        val now = at(2026, 3, 10, 12)
        val start = LocalDate(2026, 3, 9)
        val p = plan(start, Repeat(7, RepeatUnit.DAY, start, until = LocalDate(2026, 3, 23)), circleId = "crew")
        val planned = NotificationPlanner.plan(
            listOf(friend("ana"), friend("rui")),
            emptyList(),
            emptyList(),
            now,
            TimeZone.UTC,
            9,
            horizonDays = 30,
            groups = listOf(crew),
            plans = listOf(p),
            // An app that hasn't been opened for months: catch-ups stop, plans don't (gotcha 16).
            policy = NudgePolicy(weeklyDigest = false, lastOpenedAt = at(2025, 1, 1)),
        )
        assertEquals(
            listOf(
                "plan:p@2026-03-09:ask",
                "plan:p@2026-03-16:day",
                "plan:p@2026-03-16:ask",
                "plan:p@2026-03-23:day",
                "plan:p@2026-03-23:ask",
            ),
            planned.map { it.id },
        )
        val ask = assertIs<NotificationContent.PlanFollowUp>(planned.first().content)
        assertEquals(listOf("Climbing crowd"), ask.withNames)
        assertTrue(ask.isCircle)
        assertEquals("p@2026-03-09", ask.target)
        assertEquals(LocalDate(2026, 3, 16), assertIs<NotificationContent.PlanToday>(planned[1].content).occurrence)
    }

    @Test
    fun aOneOffPlan_keepsItsNotificationIds() {
        val planned = NotificationPlanner.plan(
            listOf(friend("ana")),
            emptyList(),
            emptyList(),
            at(2026, 3, 10, 12),
            TimeZone.UTC,
            9,
            plans = listOf(plan(LocalDate(2026, 3, 12), friendIds = listOf(ana))),
        )
        assertEquals(listOf("plan:p:day", "plan:p:ask"), planned.map { it.id })
        assertEquals("p", assertIs<NotificationContent.PlanFollowUp>(planned[1].content).target)
    }
}
