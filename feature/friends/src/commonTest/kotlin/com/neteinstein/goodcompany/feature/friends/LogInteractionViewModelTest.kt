package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.log.LogInteractionViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.log.LogPrefill
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogInteractionViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(listOf(friend("ana"), friend("rui")))

    private fun vm(
        ids: List<FriendId> = listOf(ana),
        type: String? = null,
        circleId: String? = null,
        plan: PlanOccurrence? = null,
        prefill: LogPrefill = LogPrefill(),
    ) = LogInteractionViewModel(
        ids,
        type,
        circleId,
        g.observeFriends,
        g.observeActivities,
        g.observeGroups,
        g.observePlaceBook,
        g.logInteraction,
        g.currentLocation,
        g.clock,
        plan,
        g.observePlans,
        g.confirmPlan,
        prefill,
    )

    @Test
    fun aCalendarSuggestionOpensOnItsDayWithTheGuessedActivity() = runViewModelTest {
        val friday = g.clock.today().minus(DatePeriod(days = 4))
        val vm = vm(listOf(rui), type = "MEETUP", prefill = LogPrefill(friday, HangoutActivity.LUNCH))
        val state = collecting(vm.state)
        assertEquals(friday, state.value.sheet.date)
        assertEquals(HangoutActivity.LUNCH, state.value.sheet.activity)
        vm.onSave()
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(listOf(rui), h.friendIds)
        assertEquals(HangoutActivity.LUNCH, h.activity)
        // A future day is never prefilled (F2).
        val future = vm(prefill = LogPrefill(g.clock.today().plus(DatePeriod(days = 3))))
        assertEquals(g.clock.today(), collecting(future.state).value.sheet.date)
    }

    @Test
    fun typeFromTheRoutePreselectsTheKind() = runViewModelTest {
        assertEquals(LogKind.MEETUP, collecting(vm().state).value.sheet.kind)
        assertEquals(LogKind.CALL, collecting(vm(type = "CALL").state).value.sheet.kind)
        assertEquals(LogKind.MESSAGE, collecting(vm(type = "WHATSAPP").state).value.sheet.kind)
        assertEquals(LogKind.MEETUP, collecting(vm(type = "nonsense").state).value.sheet.kind)
    }

    @Test
    fun savingAMeetupWritesAHangoutWithTheActivity() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(listOf("ana"), state.value.sheet.people.map { it.id.value })
        assertEquals(HangoutActivity.entries.size, state.value.sheet.activities.size)
        vm.onPeople(listOf(ana, rui, ana))
        vm.onActivity(HangoutActivity.COFFEE)
        vm.onPlaceName("Café")
        vm.onNote("  catch-up  ")
        vm.onSave()
        assertTrue(state.value.done)
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(setOf(ana, rui), h.friendIds.toSet())
        assertEquals(HangoutActivity.COFFEE, h.activity)
        assertEquals("catch-up", h.note)
    }

    @Test
    fun anUnknownIdFromAShortcutOpensWithNobodyAndIsNeverSaved() = runViewModelTest {
        val vm = vm(ids = listOf(FriendId("deleted")))
        val state = collecting(vm.state)
        assertTrue(state.value.sheet.people.isEmpty())
        assertFalse(state.value.sheet.canSave)
        vm.onTogglePerson(rui)
        vm.onSave()
        assertTrue(state.value.done)
        assertEquals(listOf(rui), g.hangoutRepo.hangouts.value.single().friendIds)
    }

    @Test
    fun savingACallInThePastLogsItThatDayAndDropsMeetupFields() = runViewModelTest {
        val vm = vm(type = "CALL")
        val state = collecting(vm.state)
        val today = g.clock.today()
        vm.onDate(today.plus(DatePeriod(days = 3)))
        assertEquals(today, state.value.sheet.date)
        vm.onDate(today.plus(DatePeriod(days = -1)))
        vm.onActivity(HangoutActivity.DINNER)
        vm.onSave()
        assertTrue(state.value.done)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        val call = g.interactionRepo.interactions.value.single()
        assertEquals(InteractionType.CALL, call.type)
        assertTrue(call.at < g.clock.now())
    }

    @Test
    fun aJournalEntryNeedsText() = runViewModelTest {
        val vm = vm(type = "JOURNAL")
        val state = collecting(vm.state)
        assertFalse(state.value.sheet.canSave)
        vm.onSave()
        assertFalse(state.value.done)
        vm.onNote("Talked about Porto")
        vm.onSave()
        assertTrue(state.value.done)
        assertEquals("Talked about Porto", g.interactionRepo.journal.value.single().text)
    }

    @Test
    fun locationCanBeUsedAndCleared() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onUseLocation()
        vm.onClearLocation()
        assertNull(state.value.sheet.place.location)
        vm.onKind(LogKind.EMAIL)
        assertEquals(LogKind.EMAIL, state.value.sheet.kind)
    }

    @Test
    fun atANamedPlace_itNamesItself_andSuggestsTheUsualPeopleWithoutTickingThem() = runViewModelTest {
        val home = GeoPoint(38.7139, -9.1394)
        g.location.point = home
        // First time: "What place is this?"
        val first = vm(ids = listOf(ana, rui))
        val firstState = collecting(first.state)
        first.onUseLocation()
        assertEquals(home, firstState.value.sheet.place.location)
        assertTrue(firstState.value.sheet.place.firstTimeHere)
        assertNull(firstState.value.sheet.place.match)
        first.onPlaceName("Inês’s house")
        first.onSave()
        assertTrue(firstState.value.done)

        // Second time, a few metres away: named by itself; Rui is suggested, not ticked.
        g.location.point = GeoPoint(38.71392, -9.1394)
        val second = vm(ids = listOf(ana))
        val state = collecting(second.state)
        second.onUseLocation()
        val place = state.value.sheet.place
        assertEquals("Inês’s house", place.match?.place?.name)
        assertEquals(listOf(rui), place.suggested.map { it.id })
        assertEquals(1, place.suggestedFrom)
        assertEquals(listOf(ana), state.value.sheet.people.map { it.id })
        second.onTogglePerson(rui) // tapping the suggestion adds them
        assertTrue(state.value.sheet.place.suggested.isEmpty())
        second.onSave()
        val saved = g.hangoutRepo.hangouts.value.last()
        assertEquals("Inês’s house", saved.place?.name)
        assertEquals(1, g.hangoutRepo.places.value.size)

        // "Change": somewhere else nearby gets its own name.
        val third = vm(ids = listOf(ana))
        val thirdState = collecting(third.state)
        third.onUseLocation()
        third.onChangePlace()
        assertNull(thirdState.value.sheet.place.match)
        assertTrue(thirdState.value.sheet.place.suggested.isEmpty())
        third.onPlaceName("Café downstairs")
        third.onSave()
        assertEquals("Café downstairs", g.hangoutRepo.hangouts.value.last().place?.name)
        assertEquals(2, g.hangoutRepo.places.value.size)
    }

    @Test
    fun aCircleListsItsMembersWithNobodyTicked() = runViewModelTest {
        val circle = g.saveGroup(null, "Choir", "🎵", CircleColor.PURPLE, listOf(ana, rui), null)
        val vm = vm(ids = emptyList(), type = "MEETUP", circleId = circle.id)
        val state = collecting(vm.state)
        assertEquals("Choir", state.value.sheet.circle?.name)
        assertEquals(listOf(ana, rui), state.value.sheet.circleMembers.map { it.id })
        assertTrue(state.value.sheet.people.isEmpty())
        assertFalse(state.value.sheet.canSave)
        assertEquals(listOf("Choir"), state.value.sheet.circles.map { it.name })

        vm.onTogglePerson(rui) // Ana didn't come
        vm.onSave()
        assertEquals(listOf(rui), g.hangoutRepo.hangouts.value.single().friendIds)

        vm.onCircle(null)
        assertNull(state.value.sheet.circle)
        vm.onTogglePerson(rui)
        assertTrue(state.value.sheet.people.isEmpty())
    }

    @Test
    fun didYouMeetOnACirclePlan_opensAGroupCheckIn_withNobodyTicked_andMovesTheSeriesOn() = runViewModelTest {
        val circle = g.saveGroup(null, "Climbing crowd", "🧗", CircleColor.TEAL, listOf(ana, rui), null)
        val start = LocalDate(2026, 3, 1)
        val plan = g.savePlan.save(
            null,
            PlanInput(
                "Climbing at Fábrica",
                emptyList(),
                start,
                place = "Fábrica",
                circleId = circle.id,
                activity = HangoutActivity.SPORTS,
                repeat = Repeat(15, RepeatUnit.DAY, start),
            ),
        )
        val vm = vm(ids = emptyList(), type = "MEETUP", plan = PlanOccurrence(plan.id, start))
        val state = collecting(vm.state)
        val sheet = state.value.sheet
        assertEquals("Climbing crowd", sheet.circle?.name)
        assertEquals(listOf(ana, rui), sheet.circleMembers.map { it.id })
        assertTrue(sheet.people.isEmpty(), "nobody is ticked to start with")
        assertEquals(HangoutActivity.SPORTS, sheet.activity)
        assertEquals(start, sheet.date)
        assertEquals("Fábrica", sheet.place.name)
        assertFalse(sheet.canSave)

        vm.onTogglePerson(rui)
        vm.onSave()
        assertTrue(state.value.done)
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(listOf(rui), h.friendIds)
        assertEquals(HangoutActivity.SPORTS, h.activity)
        assertEquals(LocalDate(2026, 3, 16), g.planRepo.plans.value.single().date, "the series moves on")
    }
}
