package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsMessage
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.PlanDraft
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HangoutsViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(
        friends = listOf(
            friend("ana") { copy(closeness = Closeness.CLOSE, meetEveryDays = 7) },
            friend("rui") { copy(meetEveryDays = 60) },
            friend("zoe"),
        ),
        hangouts = listOf(Hangout("1", listOf(ana, rui), at(2026, 2, 1)), Hangout("2", listOf(rui), at(2026, 3, 1))),
    )

    private fun vm() = HangoutsViewModel(
        g.observeFriends,
        g.observeGroups,
        g.observePlaceBook,
        g.observePlans,
        g.savePlan,
        g.deleteGroup,
        g.restoreGroup,
        g.snooze,
        g.checkIn,
        g.currentLocation,
        g.clock,
    )

    @Test
    fun checkInAtANamedPlace_namesItself_suggestsWithoutTicking_andChangeAsksAgain() = runViewModelTest {
        val home = GeoPoint(38.7139, -9.1394)
        g.hangoutRepo.upsertPlace(NamedPlace("ines", "Inês’s house", home.latitude, home.longitude))
        g.hangoutRepo.add(Hangout("3", listOf(rui, FriendId("zoe")), at(2026, 3, 2), Place("Inês’s house", home.latitude, home.longitude, "ines")))
        g.location.point = GeoPoint(38.71395, -9.1394)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onStartCheckIn(ana)
        vm.onLocationPermissionResult(granted = true)
        val place = state.value.draftPlace
        assertEquals("Inês’s house", place.match?.place?.name)
        assertEquals(listOf("rui", "zoe"), place.suggested.map { it.id.value })
        assertEquals(setOf(ana), state.value.draft?.selected)
        vm.onToggleDraftFriend(rui)
        assertEquals(listOf("zoe"), state.value.draftPlace.suggested.map { it.id.value })

        vm.onChangeDraftPlace()
        assertEquals(null, state.value.draftPlace.match)
        assertEquals(false, state.value.draftPlace.firstTimeHere)
        // Removing the fix and taking it again forgets "Change": the place names itself again.
        vm.onClearDraftLocation()
        vm.onLocationPermissionResult(granted = true)
        assertEquals("Inês’s house", state.value.draftPlace.match?.place?.name)
        vm.onSaveCheckIn()
        val saved = g.hangoutRepo.hangouts.value.last()
        assertEquals("Inês’s house", saved.place?.name)
        assertEquals("ines", saved.place?.placeId)
        assertEquals(setOf(ana, rui), saved.friendIds.toSet())
    }

    @Test
    fun theTabHasOnlyTheCircles_noHistoryOrFilter() = runViewModelTest {
        // Issue #88: "Last time with…" and the memories left the tab; meetups show on each circle's page.
        val vm = vm()
        val state = collecting(vm.state)
        g.saveGroup(null, "Surf", "🏄", CircleColor.TEAL, listOf(ana, rui), null)
        assertEquals(listOf("Surf"), state.value.groups.map { it.group.name })
        assertFalse(state.value.isLoading)
    }

    @Test
    fun checkInFlowWithLocation() = runViewModelTest {
        g.location.point = GeoPoint(38.7, -9.1)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onStartCheckIn(ana)
        vm.onToggleDraftFriend(rui)
        vm.onDraftPlaceChange("Café")
        vm.onLocationPermissionResult(true)
        assertEquals(GeoPoint(38.7, -9.1), state.value.draft?.location)
        vm.onSaveCheckIn()
        assertNull(state.value.draft)
        assertEquals(HangoutsMessage.LoggedWith(listOf("Ana", "Rui")), state.value.message)
        val newest = g.hangoutRepo.hangouts.value.last()
        assertEquals(setOf(ana, rui), newest.friendIds.toSet())
        assertEquals("Café", newest.place?.name)
    }

    @Test
    fun cannotSaveWithoutFriendsAndDeniedLocationExplains() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onStartCheckIn()
        vm.onSaveCheckIn()
        assertEquals(2, g.hangoutRepo.hangouts.value.size)
        vm.onLocationPermissionResult(false)
        assertEquals(HangoutsMessage.LocationDenied, state.value.message)
        g.location.point = null
        vm.onLocationPermissionResult(true)
        assertEquals(HangoutsMessage.LocationUnavailable, state.value.message)
        vm.onDismissCheckIn()
        assertNull(state.value.draft)
    }

    @Test
    fun circlesCheckInWithNobodyTicked_snoozeAndDeleteWithUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val group = g.saveGroup(null, "Surf", "🏄", CircleColor.TEAL, listOf(ana, rui), 14)
        val status = state.value.groups.single()
        assertTrue(status.isOverdue(g.clock.today())) // last together Feb 1, every 14 days

        // F20: picking a circle lists its members with nobody ticked; picking it again goes back to everyone.
        vm.onStartCheckIn(ana)
        vm.onPickDraftGroup(group.id)
        assertEquals(group.id, state.value.draft!!.circleId)
        assertEquals(setOf(ana), state.value.draft!!.selected) // whoever was ticked stays ticked
        vm.onPickDraftGroup(group.id)
        assertNull(state.value.draft!!.circleId)
        vm.onDismissCheckIn()

        // The circle card's "We met" opens the "We met" panel (issue #47), not the check-in sheet: nothing is drafted here.
        assertNull(state.value.draft)
        vm.onStartCheckIn(ana)
        vm.onPickDraftGroup(group.id)
        vm.onToggleDraftFriend(rui)
        vm.onSaveCheckIn()
        assertFalse(state.value.groups.single().isOverdue(g.clock.today()))

        vm.onSnoozeGroup(group.id, SnoozeOption.ONE_WEEK)
        assertIs<HangoutsMessage.GroupSnoozed>(state.value.message)
        vm.onDeleteGroup(group.id)
        assertEquals(HangoutsMessage.GroupDeleted("Surf"), state.value.message)
        assertTrue(state.value.groups.isEmpty())
        vm.onUndoDelete()
        assertEquals(listOf("Surf"), state.value.groups.map { it.group.name })
        assertEquals(CircleColor.TEAL, state.value.groups.single().group.color)
        vm.onMessageShown()
        assertNull(state.value.recentlyDeletedGroup)
    }

    @Test
    fun circlesAreOrderedByLastMeetup_neverMetLast_andPlanWithACircle() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val never = g.saveGroup(null, "Book club", "📚", CircleColor.AMBER, listOf(FriendId("zoe")), null)
        g.saveGroup(null, "Lunch", "🥪", CircleColor.BLUE, listOf(ana, rui), null) // last together Feb 1
        val recent = g.saveGroup(null, "Rui", "🎸", CircleColor.TEAL, listOf(rui), null) // last together Mar 1
        assertEquals(listOf("Rui", "Lunch", "Book club"), state.value.groups.map { it.group.name })

        vm.onPlanWithCircle(recent.id)
        assertEquals(PlanDraft(recent.id), state.value.planDraft)
        vm.onSavePlan(PlanInput("Jam", emptyList(), LocalDate(2026, 3, 12), circleId = recent.id))
        assertNull(state.value.planDraft)
        assertEquals(HangoutsMessage.PlanSaved, state.value.message)
        assertEquals(recent.id, g.planRepo.plans.value.single().circleId)
        assertEquals("Jam", state.value.circlePlans.getValue(recent.id).title)
        vm.onPlanWithCircle(never.id)
        vm.onDismissPlan()
        assertNull(state.value.planDraft)
    }
}
