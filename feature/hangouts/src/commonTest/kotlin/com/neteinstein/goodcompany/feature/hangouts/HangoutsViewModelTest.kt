package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.GroupDraft
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsMessage
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsViewModel
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
        g.saveGroup,
        g.deleteGroup,
        g.snooze,
        g.checkIn,
        g.deleteHangout,
        g.restoreHangout,
        g.currentLocation,
        g.clock,
    )

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
    fun groupsCanBeCreatedCheckedInSnoozedAndDeleted() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onNewGroup()
        vm.onGroupDraftChange(GroupDraft(name = "Surf", emoji = "🏄", members = setOf(ana), meetEveryDays = 14))
        assertFalse(state.value.groupDraft!!.canSave)
        vm.onSaveGroup()
        assertTrue(state.value.groups.isEmpty())
        vm.onGroupDraftChange(state.value.groupDraft!!.copy(members = setOf(ana, rui)))
        vm.onSaveGroup()
        assertNull(state.value.groupDraft)
        assertEquals(HangoutsMessage.GroupSaved("Surf"), state.value.message)
        val group = state.value.groups.single()
        assertTrue(group.isOverdue(g.clock.today())) // last all together Feb 1, every 14 days

        vm.onEditGroup(group.group.id)
        assertEquals("Surf", state.value.groupDraft!!.name)
        vm.onDismissGroup()

        vm.onStartCheckIn()
        vm.onPickDraftGroup(group.group.id)
        assertEquals(setOf(ana, rui), state.value.draft!!.selected)
        vm.onPickDraftGroup(group.group.id)
        assertTrue(state.value.draft!!.selected.isEmpty())
        vm.onDismissCheckIn()

        vm.onCheckInGroup(group.group.id)
        assertEquals(setOf(ana, rui), state.value.draft!!.selected)
        vm.onSaveCheckIn()
        assertFalse(state.value.groups.single().isOverdue(g.clock.today()))

        vm.onSnoozeGroup(group.group.id, SnoozeOption.ONE_WEEK)
        assertIs<HangoutsMessage.GroupSnoozed>(state.value.message)
        vm.onDeleteGroup(group.group.id)
        assertEquals(HangoutsMessage.GroupDeleted("Surf"), state.value.message)
        assertTrue(state.value.groups.isEmpty())
    }
}
