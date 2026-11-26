package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.NotificationAction
import org.goodcompany.app.core.domain.model.NotificationContent
import org.goodcompany.app.core.domain.model.Plan
import org.goodcompany.app.core.domain.model.PlanAnswer
import org.goodcompany.app.core.domain.model.PlanOccurrence
import org.goodcompany.app.core.domain.model.PlanStatus
import org.goodcompany.app.core.domain.model.Repeat
import org.goodcompany.app.core.domain.model.RepeatUnit
import org.goodcompany.app.core.domain.usecase.NotificationPlanner
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #100: cancel (kept), delete (with Undo), "Didn't happen" and "I didn't go". */
class PlanStatesTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val people = listOf(friend("ana"), friend("rui"))
    private val start = LocalDate(2026, 3, 1)
    private val weekly = Repeat(1, RepeatUnit.WEEK, start)

    private fun graph() = TestGraph(people)

    @Test
    fun isGroup_isMoreThanOnePersonOrACircle() {
        val one = Plan("p", listOf(ana), "Coffee", start, createdAt = at(2026, 3, 1))
        assertFalse(one.isGroup)
        assertTrue(one.copy(friendIds = listOf(ana, rui)).isGroup)
        assertTrue(one.copy(friendIds = emptyList(), circleId = "crew").isGroup)
    }

    @Test
    fun closeOccurrence_neverReopens() {
        val one = Plan("p", listOf(ana), "Coffee", start, createdAt = at(2026, 3, 1))
        assertFailsWith<IllegalArgumentException> { one.closeOccurrence(start, PlanStatus.PLANNED) }
        assertEquals(PlanStatus.NOT_ATTENDED, one.closeOccurrence(start, PlanStatus.NOT_ATTENDED).status)
    }

    @Test
    fun cancel_keepsTheOneOffPlan_asCancelled_andUndoReopensIt() = runTest {
        val g = graph()
        val p = g.savePlan(null, "Dinner", listOf(ana), LocalDate(2026, 3, 20))
        val before = g.cancelPlan(p.id)
        assertEquals(p, before)
        assertEquals(PlanStatus.CANCELLED, g.planRepo.plans.value.single().status)
        assertTrue(g.observePlans().first().isEmpty(), "no longer open")
        assertEquals(listOf(p.id), g.observePlans.cancelled().first().map { it.id })
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        // Twice does nothing.
        assertNull(g.cancelPlan(p.id))
        g.restorePlan(before!!)
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)
    }

    @Test
    fun cancel_oneOccurrence_ofASeries_takesItOut_andTheSeriesGoesOn() = runTest {
        val g = graph()
        val p = g.savePlan.save(null, PlanInput("Run", listOf(ana), start, repeat = weekly))
        g.cancelPlan(p.id, LocalDate(2026, 3, 8))
        val after = g.planRepo.plans.value.single()
        assertEquals(PlanStatus.PLANNED, after.status)
        assertEquals(listOf(LocalDate(2026, 3, 8)), after.skippedDates)
        // The open one moves the series on.
        g.cancelPlan(p.id)
        assertEquals(LocalDate(2026, 3, 15), g.planRepo.plans.value.single().date)
        // The whole series is kept as cancelled.
        g.cancelPlan(p.id, wholeSeries = true)
        assertEquals(PlanStatus.CANCELLED, g.planRepo.plans.value.single().status)
    }

    @Test
    fun delete_removesThePlan_orOneOccurrence_andUndoPutsItBack() = runTest {
        val g = graph()
        val one = g.savePlan(null, "Dinner", listOf(ana), LocalDate(2026, 3, 20))
        val removed = g.deletePlan(one.id)
        assertEquals(one, removed)
        assertTrue(g.planRepo.plans.value.isEmpty())
        g.restorePlan(removed!!)
        assertEquals(listOf(one), g.planRepo.plans.value)
        assertNull(g.deletePlan("missing"))

        val series = g.savePlan.save(null, PlanInput("Run", listOf(ana), start, repeat = weekly))
        g.deletePlan(series.id, LocalDate(2026, 3, 8), wholeSeries = false)
        assertEquals(listOf(LocalDate(2026, 3, 8)), g.planRepo.plans.value.single { it.id == series.id }.skippedDates)
        // Not an occurrence: nothing changes.
        assertNull(g.deletePlan(series.id, LocalDate(2026, 3, 9), wholeSeries = false))
        g.deletePlan(series.id, wholeSeries = true)
        assertTrue(g.planRepo.plans.value.none { it.id == series.id })
    }

    @Test
    fun notAttended_closesAGroupPlan_withNoCheckIn() = runTest {
        val g = graph()
        val p = g.savePlan(null, "Dinner", listOf(ana, rui), LocalDate(2026, 3, 9))
        val before = g.closePlan(p.id, PlanAnswer.NOT_ATTENDED)
        assertEquals(p, before)
        assertEquals(PlanStatus.NOT_ATTENDED, g.planRepo.plans.value.single().status)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty(), "no check-in for me: no warmth, no rhythm")
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
    }

    @Test
    fun notAttended_isOnlyForGroupAndCirclePlans() = runTest {
        val g = graph()
        val p = g.savePlan(null, "Coffee", listOf(ana), LocalDate(2026, 3, 9))
        assertNull(g.closePlan(p.id, PlanAnswer.NOT_ATTENDED))
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)
        g.circles.upsert(FriendGroup("crew", "Climbing crowd", "🧗", listOf(ana, rui)))
        val c = g.savePlan.save(null, PlanInput("Climb", emptyList(), start, circleId = "crew", repeat = weekly))
        g.closePlan(c.id, PlanAnswer.NOT_ATTENDED)
        // A series: that occurrence closes, the series goes on.
        val after = g.planRepo.plans.value.single { it.id == c.id }
        assertEquals(PlanStatus.PLANNED, after.status)
        assertEquals(LocalDate(2026, 3, 8), after.date)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
    }

    @Test
    fun theSheet_coversEveryAnswer() = runTest {
        val g = graph()
        val happened = g.savePlan(null, "Lunch", listOf(ana), LocalDate(2026, 3, 9))
        assertNull(g.closePlan(happened.id, PlanAnswer.HAPPENED))
        assertEquals(PlanStatus.HAPPENED, g.planRepo.plans.value.single { it.id == happened.id }.status)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)

        val no = g.savePlan(null, "Hike", listOf(ana), LocalDate(2026, 3, 9))
        g.closePlan(no.id, PlanAnswer.DIDNT_HAPPEN)
        assertEquals(PlanStatus.SKIPPED, g.planRepo.plans.value.single { it.id == no.id }.status)

        val cancel = g.savePlan(null, "Film", listOf(ana), LocalDate(2026, 3, 12))
        assertEquals(cancel, g.closePlan(cancel.id, PlanAnswer.CANCEL))
        assertEquals(PlanStatus.CANCELLED, g.planRepo.plans.value.single { it.id == cancel.id }.status)

        val delete = g.savePlan(null, "Gig", listOf(ana), LocalDate(2026, 3, 12))
        assertEquals(delete, g.closePlan(delete.id, PlanAnswer.DELETE))
        assertTrue(g.planRepo.plans.value.none { it.id == delete.id })
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
    }

    @Test
    fun notificationAction_iDidntGo_closesThatOccurrence() = runTest {
        val g = graph()
        val p = g.savePlan.save(null, PlanInput("Run", listOf(ana, rui), start, repeat = weekly))
        val action = NotificationAction(NotificationAction.Kind.PLAN_NOT_ATTENDED, PlanOccurrence(p.id, start).encode())
        assertTrue(action.isPlan)
        assertEquals(action, NotificationAction.decode(action.encode()))
        g.handleNotificationAction(action)
        assertEquals(LocalDate(2026, 3, 8), g.planRepo.plans.value.single().date)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
    }

    @Test
    fun followUp_offersIDidntGo_forGroupPlansOnly() {
        fun ask(plan: Plan) = NotificationPlanner.plan(
            people,
            emptyList(),
            emptyList(),
            at(2026, 3, 10, 12),
            TimeZone.UTC,
            9,
            plans = listOf(plan),
        ).map { it.content }.filterIsInstance<NotificationContent.PlanFollowUp>().single()
        val one = Plan("p", listOf(ana), "Coffee", LocalDate(2026, 3, 12), createdAt = at(2026, 3, 1))
        fun kinds(plan: Plan) = ask(plan).actions.map { it.kind }
        val yes = NotificationAction.Kind.PLAN_HAPPENED
        val no = NotificationAction.Kind.PLAN_SKIPPED
        val notMe = NotificationAction.Kind.PLAN_NOT_ATTENDED
        assertFalse(ask(one).isGroup)
        assertEquals(listOf(yes, no), kinds(one))
        assertEquals(listOf(yes, no, notMe), kinds(one.copy(friendIds = listOf(ana, rui))))
        // A circle plan asks who came in the app: no "Yes" here, but "I didn't go".
        val circle = listOf(FriendGroup("crew", "Climbing crowd", "🧗", listOf(ana, rui)))
        val circleAsk = NotificationPlanner.plan(
            people,
            emptyList(),
            emptyList(),
            at(2026, 3, 10, 12),
            TimeZone.UTC,
            9,
            groups = circle,
            plans = listOf(one.copy(friendIds = emptyList(), circleId = "crew")),
        ).map { it.content }.filterIsInstance<NotificationContent.PlanFollowUp>().single()
        assertEquals(listOf(no, notMe), circleAsk.actions.map { it.kind })
        assertTrue(circleAsk.actions.all { it.targetId == "p" })
    }

    @Test
    fun aClosedPlan_isNoLongerNotified_soItsNotificationsGo() {
        val open = Plan("p", listOf(ana), "Coffee", LocalDate(2026, 3, 12), createdAt = at(2026, 3, 1))
        listOf(PlanStatus.CANCELLED, PlanStatus.NOT_ATTENDED, PlanStatus.SKIPPED).forEach { status ->
            val planned = NotificationPlanner.plan(
                people,
                emptyList(),
                emptyList(),
                at(2026, 3, 10, 12),
                TimeZone.UTC,
                9,
                plans = listOf(open.copy(status = status)),
            )
            assertTrue(planned.none { it.slot == "plan:p" }, "$status")
        }
    }
}
