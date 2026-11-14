package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.LoggedMeetupViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.WeMetViewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeMetViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(listOf(friend("ana"), friend("rui")))

    private fun vm(ids: List<FriendId> = listOf(ana), circleId: String? = null, plan: PlanOccurrence? = null) = WeMetViewModel(
        ids,
        circleId,
        plan,
        g.observeFriends,
        g.observeActivities,
        g.observeGroups,
        g.observePlaceBook,
        g.observePlans,
        g.logInteraction,
        g.confirmPlan,
        g.currentLocation,
        g.loggedMeetups,
        g.clock,
    )

    @Test
    fun weMetThenSaveLogsTodayInTwoTaps() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val sheet = state.value.sheet
        assertEquals(g.clock.today(), sheet.date)
        assertEquals(listOf(ana), sheet.people.map { it.id })
        assertNull(sheet.activity)
        assertFalse(state.value.noteOpen)
        assertTrue(sheet.canSave)

        vm.onSave()
        assertTrue(state.value.done)
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals(listOf(ana), hangout.friendIds)
        assertEquals(g.clock.now(), hangout.at)
        assertEquals("", hangout.note)
        assertEquals(listOf(ana), g.loggedMeetups.pending.value?.people?.map { it.id })
    }

    @Test
    fun fridaysLunchLoggedAWeekLater_withANote() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val friday = LocalDate(2026, 3, 6)
        vm.onDate(friday)
        vm.onActivity(HangoutActivity.LUNCH)
        vm.onOpenNote()
        assertTrue(state.value.noteOpen)
        vm.onNote("Her new job at the studio")
        vm.onSave()
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals(at(2026, 3, 6), hangout.at)
        assertEquals(HangoutActivity.LUNCH, hangout.activity)
        assertEquals("Her new job at the studio", hangout.note)
    }

    @Test
    fun yesterdayWorks_andFutureDaysFallBackToToday() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val today = g.clock.today()
        vm.onDate(today.plus(DatePeriod(days = -1)))
        assertEquals(today.plus(DatePeriod(days = -1)), state.value.sheet.date)
        vm.onDate(today.plus(DatePeriod(days = 4)))
        assertEquals(today, state.value.sheet.date)
    }

    @Test
    fun theActivityIsOptionalAndTappingItAgainClearsIt() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(HangoutActivity.entries.size, state.value.sheet.activities.size)
        vm.onActivity(HangoutActivity.COFFEE)
        assertEquals(HangoutActivity.COFFEE, state.value.sheet.activity)
        vm.onActivity(null)
        assertNull(state.value.sheet.activity)
    }

    @Test
    fun aCircleListsItsMembersUnticked() = runViewModelTest {
        val circle = g.saveGroup(null, "Choir", "🎵", CircleColor.PURPLE, listOf(ana, rui), 14)
        val vm = vm(ids = emptyList(), circleId = circle.id)
        val state = collecting(vm.state)
        assertEquals("Choir", state.value.sheet.circle?.name)
        assertEquals(listOf(ana, rui), state.value.sheet.circleMembers.map { it.id })
        assertTrue(state.value.sheet.people.isEmpty())
        assertFalse(state.value.sheet.canSave)

        vm.onTogglePerson(rui) // Ana didn't come
        vm.onSave()
        assertEquals(listOf(rui), g.hangoutRepo.hangouts.value.single().friendIds)
    }

    @Test
    fun addPeopleAddsSomeoneElse() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onPeople(listOf(ana, rui, ana))
        assertEquals(listOf(ana, rui), state.value.sheet.people.map { it.id })
    }

    @Test
    fun moreDetailsCarriesEverythingChosen() = runViewModelTest {
        val vm = vm()
        collecting(vm.state)
        val friday = LocalDate(2026, 3, 6)
        vm.onDate(friday)
        vm.onActivity(HangoutActivity.DINNER)
        vm.onNote("Pasta")
        val details = vm.details()
        assertEquals(listOf(ana), details.friendIds)
        assertEquals(friday, details.date)
        assertEquals(HangoutActivity.DINNER, details.activity)
        assertEquals("Pasta", details.note)
        assertNull(details.circleId)
        assertNull(details.plan)
    }

    @Test
    fun aPlansDidYouMeetFillsThePanel_andSavingClosesThePlan() = runViewModelTest {
        val plan = g.savePlan(null, "Dinner at Rui’s", listOf(ana, rui), LocalDate(2026, 3, 7), "Porto")
        val vm = vm(ids = emptyList(), plan = PlanOccurrence(plan.id, plan.date))
        val state = collecting(vm.state)
        assertEquals(listOf(ana, rui), state.value.sheet.people.map { it.id })
        assertEquals(LocalDate(2026, 3, 7), state.value.sheet.date)
        assertEquals("Dinner at Rui’s", state.value.sheet.note)
        assertTrue(state.value.noteOpen)
        vm.onSave()
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals("Porto", hangout.place?.name)
        assertEquals(PlanStatus.HAPPENED, g.planRepo.plans.value.single().status)
    }

    @Test
    fun undoOnTheSnackbarRemovesTheHangout_andReopensThePlan() = runViewModelTest {
        val plan = g.savePlan(null, "Lunch", listOf(ana), LocalDate(2026, 3, 6))
        val panel = vm(plan = PlanOccurrence(plan.id, plan.date))
        collecting(panel.state)
        panel.onSave()
        val snackbar = LoggedMeetupViewModel(g.loggedMeetups, g.undoLog)
        val meetup = snackbar.pending.value!!
        assertEquals(listOf(ana), meetup.people.map { it.id })

        snackbar.onUndo(meetup)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)
        assertNull(snackbar.pending.value)
    }

    @Test
    fun aShownSnackbarKeepsTheHangout() = runViewModelTest {
        val panel = vm()
        collecting(panel.state)
        panel.onSave()
        val snackbar = LoggedMeetupViewModel(g.loggedMeetups, g.undoLog)
        snackbar.onShown(snackbar.pending.value!!)
        assertNull(snackbar.pending.value)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
    }

    @Test
    fun aPlaceIsCollapsedUntilAsked_andIsSavedAndCarriedToMoreDetails() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.placeOpen)
        vm.onOpenPlace()
        assertTrue(state.value.placeOpen)
        vm.onPlaceName("Time Out")
        assertEquals("Time Out", vm.details().placeName)
        vm.onUseLocation()
        vm.onClearLocation()
        vm.onChangePlace()
        assertEquals("", state.value.sheet.place.name)
        vm.onPlaceName("Café Lisboa")
        vm.onSave()
        assertEquals("Café Lisboa", g.hangoutRepo.hangouts.value.single().place?.name)
    }
}
