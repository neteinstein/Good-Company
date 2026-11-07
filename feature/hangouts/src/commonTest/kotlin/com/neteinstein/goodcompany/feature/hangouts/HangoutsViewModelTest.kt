package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakeLocationProvider
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HangoutsViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val friends = FakeFriendRepository(
        listOf(friend("ana") { copy(isCloseFriend = true, meetEveryDays = 7) }, friend("rui") { copy(meetEveryDays = 60) }),
    )
    private val hangouts = FakeHangoutRepository(
        listOf(Hangout("1", listOf(ana, rui), at(2026, 2, 1)), Hangout("2", listOf(rui), at(2026, 3, 1))),
    )
    private val clock = FixedClock()
    private val location = FakeLocationProvider()

    private fun vm() = HangoutsViewModel(
        ObserveFriendsUseCase(friends, hangouts, FakeInteractionRepository(), clock),
        ObserveHangoutHistoryUseCase(friends, hangouts),
        ObserveMeetupStatusUseCase(friends, hangouts, clock),
        ObserveHangoutHistoryUseCase(friends, hangouts),
        CheckInUseCase(hangouts, clock, SequentialIds()),
        DeleteHangoutUseCase(hangouts),
        RestoreHangoutUseCase(hangouts),
        GetCurrentLocationUseCase(location),
        clock,
    )

    @Test
    fun splitsOverdueAndUpcomingAndFiltersHistory() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(listOf("ana"), state.value.overdue.map { it.friend.id.value })
        assertEquals(listOf("rui"), state.value.upcoming.map { it.friend.id.value })
        assertEquals(2, state.value.history.size)
        assertNull(state.value.lastTogether)

        vm.onToggleFilter(ana)
        vm.onToggleFilter(rui)
        assertEquals("1", state.value.lastTogether?.hangout?.id)
        vm.onClearFilter()
        assertEquals(2, state.value.history.size)
    }

    @Test
    fun checkInFlowWithLocation() = runViewModelTest {
        location.point = GeoPoint(38.7, -9.1)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onStartCheckIn(ana)
        vm.onToggleDraftFriend(rui)
        vm.onDraftPlaceChange("Café")
        vm.onLocationPermissionResult(true)
        assertEquals(GeoPoint(38.7, -9.1), state.value.draft?.location)
        vm.onSaveCheckIn()
        assertNull(state.value.draft)
        assertEquals("Logged time with Ana, Rui ✨", state.value.message)
        val newest = hangouts.hangouts.value.last()
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
        assertEquals(2, hangouts.hangouts.value.size)
        vm.onLocationPermissionResult(false)
        assertTrue(state.value.message!!.contains("denied"))
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
        assertEquals("Memory deleted", state.value.message)
        assertTrue(hangouts.hangouts.value.none { it.id == "1" })
        vm.onUndoDelete()
        assertTrue(hangouts.hangouts.value.any { it.id == "1" })
    }
}
