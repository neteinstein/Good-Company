package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.Place
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
            friend("ana") { copy(isCloseFriend = true, meetEveryDays = 7) },
            friend("rui") { copy(meetEveryDays = 60) },
            friend("zoe"),
        ),
        hangouts = listOf(Hangout("1", listOf(ana, rui), at(2026, 2, 1)), Hangout("2", listOf(rui), at(2026, 3, 1))),
    )

    private fun vm() = HangoutsViewModel(
        g.observeFriends,
        g.observeHistory,
        g.observeMeetups,
        g.observeHistory,
        g.observeGroups,
        g.observeOccasionMatches,
        g.observePlaceBook,
        g.savePlan,
        g.deleteGroup,
        g.restoreGroup,
        g.snooze,
        g.checkIn,
        g.deleteHangout,
        g.restoreHangout,
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
    fun splitsOverdueAndUpcomingAndFiltersHistory() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(listOf("ana"), state.value.overdue.map { it.friend.id.value })
        assertEquals(listOf("rui"), state.value.upcoming.map { it.friend.id.value })
        assertEquals(2, state.value.history.size)
        assertNull(state.value.lastTogether)
        assertEquals(2026, state.value.reviewYear)

        vm.onToggleFilter(ana)
        vm.onToggleFilter(rui)
        assertEquals("1", state.value.lastTogether?.hangout?.id)
        vm.onClearFilter()
        assertEquals(2, state.value.history.size)
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
        assertTrue(state.value.overdue.isEmpty())
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
    fun statsCelebrationAndUndoDelete() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(1, state.value.stats.meetups) // only the Mar 1 hangout is this month
        vm.onStartCheckIn(ana)
        vm.onSaveCheckIn()
        assertEquals(1, state.value.celebrate)
        assertEquals(2, state.value.stats.meetups)

        vm.onDeleteHangout("1")
        assertEquals(HangoutsMessage.MemoryDeleted, state.value.message)
        assertTrue(g.hangoutRepo.hangouts.value.none { it.id == "1" })
        vm.onUndoDelete()
        assertTrue(g.hangoutRepo.hangouts.value.any { it.id == "1" })
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

        vm.onCheckInGroup(group.id)
        assertEquals(group.id, state.value.draft!!.circleId)
        assertTrue(state.value.draft!!.selected.isEmpty())
        assertFalse(state.value.draft!!.canSave)
        vm.onToggleDraftFriend(ana)
        vm.onToggleDraftFriend(rui)
        vm.onSaveCheckIn()
        assertFalse(state.value.groups.single().isOverdue(g.clock.today()))
        vm.onCheckInGroup("gone")
        assertNull(state.value.draft)

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
    fun whosFreeForSuggestsTaggedFriendsAndPlans() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.occasions.isEmpty())
        assertNull(state.value.selectedOccasion)
        g.updateFriend(ana) { it.copy(occasions = listOf(Occasion.WEEKDAY_LUNCH, Occasion.COFFEE)) }
        assertEquals(Occasion.WEEKDAY_LUNCH, state.value.selectedOccasion)
        vm.onSelectOccasion(Occasion.COFFEE)
        assertEquals(Occasion.COFFEE, state.value.selectedOccasion)
        assertEquals(listOf(ana), state.value.occasions.getValue(Occasion.COFFEE).map { it.friend.id })

        vm.onPlanWith(ana, Occasion.COFFEE)
        assertEquals(PlanDraft(ana, Occasion.COFFEE), state.value.planDraft)
        vm.onSavePlan("Coffee", emptyList(), LocalDate(2026, 3, 12), null)
        assertNull(state.value.planDraft)
        assertEquals(HangoutsMessage.PlanSaved, state.value.message)
        assertEquals(listOf(ana), g.planRepo.plans.value.single().friendIds)
        vm.onPlanWith(ana, Occasion.COFFEE)
        vm.onDismissPlan()
        assertNull(state.value.planDraft)
    }
}
