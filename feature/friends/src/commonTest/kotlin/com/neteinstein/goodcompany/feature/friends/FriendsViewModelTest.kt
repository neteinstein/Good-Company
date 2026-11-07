package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendFilter
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendsViewModelTest {
    private val friends = FakeFriendRepository(listOf(friend("bob"), friend("ana") { copy(likes = listOf("jazz")) }))
    private val interactions = FakeInteractionRepository()
    private val clock = FixedClock()

    private fun vm() = FriendsViewModel(
        ObserveFriendsUseCase(friends, FakeHangoutRepository(), interactions, clock),
        SyncContactsUseCase(friends, interactions),
        AddFriendUseCase(friends, SequentialIds()),
        UpdateFriendUseCase(friends),
        clock,
    )

    @Test
    fun showsEveryoneUntilSomeoneIsMarkedClose() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.isLoading)
        assertEquals(FriendFilter.ALL, state.value.filter)
        assertEquals(listOf("Ana", "Bob"), state.value.friends.map { it.friend.name })

        vm.onToggleClose(FriendId("bob"))
        assertEquals(FriendFilter.CLOSE, state.value.filter)
        assertEquals(listOf("Bob"), state.value.friends.map { it.friend.name })
    }

    @Test
    fun searchMatchesNamesAndTastes() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onQueryChange("JAZ")
        assertEquals(listOf("Ana"), state.value.friends.map { it.friend.name })
    }

    @Test
    fun syncReportsResultAndDeniedPermissionExplains() = runViewModelTest {
        friends.contacts = listOf(ContactRecord("1", "Carla", emptyList(), null))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactsPermissionResult(true)
        assertEquals("Synced 1 contacts", state.value.message)
        assertTrue(state.value.friends.any { it.friend.name == "Carla" })
        vm.onMessageShown()
        assertNull(state.value.message)
        vm.onContactsPermissionResult(false)
        assertTrue(state.value.message!!.contains("add friends by hand"))
    }

    @Test
    fun addFriendManually() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddFriend("  ")
        vm.onAddFriend("Zoe")
        assertEquals(3, state.value.totalCount)
        assertEquals("Added Zoe", state.value.message)
    }
}
