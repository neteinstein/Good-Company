package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.friendIds
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.PickedContactsResult
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendFilter
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendSort
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsMessage
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.Greeting
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendsViewModelTest {
    private val g = TestGraph(listOf(friend("bob"), friend("ana") { copy(likes = listOf("jazz")) }))

    private fun vm(g: TestGraph = this.g) = FriendsViewModel(
        g.observeFriends,
        g.observeAgenda,
        g.observeMeetups,
        g.observeSettings,
        g.observeStoryBubbles,
        g.observeGroups,
        g.importPickedContacts,
        g.syncContacts,
        g.importContactsFile,
        g.addFriend,
        g.setRealFriend,
        g.checkIn,
        g.logCall,
        g.snooze,
        g.loadSampleData,
        g.setHomeCircleFilter,
        g.clock,
    )

    @Test
    fun aCircleFiltersEveryHomeSection_andIsRemembered() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("ana") { copy(isCloseFriend = true, closeness = Closeness.INNER, meetEveryDays = 7) },
                friend("bob") { copy(isCloseFriend = true, meetEveryDays = 7) },
                friend("zoe"),
            ),
        )
        val ana = FriendId("ana")
        val bob = FriendId("bob")
        g.planRepo.upsert(Plan("p1", listOf(ana), "Lunch", g.clock.today(), createdAt = g.clock.now()))
        g.planRepo.upsert(Plan("p2", listOf(bob), "Run", g.clock.today(), createdAt = g.clock.now()))
        val choir = g.saveGroup(null, "Choir", "🎵", CircleColor.PURPLE, listOf(ana, FriendId("zoe")), 30)
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(listOf("Choir"), state.value.circles.map { it.group.name })
        assertNull(state.value.circle)
        assertEquals(listOf("Ana", "Bob"), state.value.friends.map { it.friend.name }) // real friends only
        assertEquals(setOf("ana", "bob"), state.value.catchUp.map { it.friend.id.value }.toSet())

        vm.onCircleFilter(choir.id)
        assertEquals(choir.id, state.value.circle?.group?.id)
        // The A–Z list shows every member, whatever their closeness; every other section only members too.
        assertEquals(listOf("Ana", "Zoe"), state.value.friends.map { it.friend.name })
        assertEquals(listOf("ana"), state.value.catchUp.map { it.friend.id.value })
        assertEquals(listOf("ana"), state.value.closeFriends.map { it.id.value })
        assertEquals(listOf("ana"), state.value.stories.map { it.friend.id.value })
        assertTrue(state.value.upcoming.isNotEmpty())
        assertTrue(state.value.upcoming.all { item -> ana in item.friendIds })
        assertEquals(3, state.value.totalCount) // "Everyone" still counts everyone

        // Remembered: a new Home (next launch) opens on the same circle.
        assertEquals(choir.id, g.settingsRepo.settings.first().homeCircleFilter)
        val again = collecting(vm(g).state)
        assertEquals(choir.id, again.value.circle?.group?.id)

        // Deleting the circle puts Home back on All.
        g.deleteGroup(choir.id)
        assertNull(state.value.circle)
        assertNull(g.settingsRepo.settings.first().homeCircleFilter)
        assertEquals(listOf("Ana", "Bob"), state.value.friends.map { it.friend.name })

        // A filter that points nowhere (e.g. restored from another device) reads as All.
        g.setHomeCircleFilter("gone")
        assertNull(state.value.circle)
        vm.onCircleFilter(null)
        assertNull(g.settingsRepo.settings.first().homeCircleFilter)
    }

    @Test
    fun showsWhoYouAreSeeingSoonAsStories() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.stories.isEmpty())
        g.planRepo.upsert(Plan("p1", listOf(FriendId("ana")), "Lunch", g.clock.today(), createdAt = g.clock.now()))
        assertEquals(listOf("ana"), state.value.stories.map { it.friend.id.value })
    }

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
        assertEquals(Closeness.CLOSE, g.friendRepo.get(FriendId("bob"))!!.closeness)
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
        g.friendRepo.contacts = listOf(ContactRecord("1", "Carla", emptyList(), null))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactsPermissionResult(true)
        assertEquals(FriendsMessage.Synced(1, 0), state.value.message)
        assertTrue(state.value.friends.any { it.friend.name == "Carla" })
        vm.onMessageShown()
        assertNull(state.value.message)
        vm.onContactsPermissionResult(false)
        assertEquals(FriendsMessage.ContactsDenied, state.value.message)
    }

    @Test
    fun vcardImportAddsPeopleAndReports() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactsFilePicked(null)
        vm.onContactsFilePicked("BEGIN:VCARD\nFN:Zoe Lima\nTEL:+351 912 345 678\nBDAY:1991-07-02\nEND:VCARD\nBEGIN:VCARD\nFN:Ana\nEND:VCARD")
        assertEquals(FriendsMessage.FileImported(ContactsFileImportResult(added = 1, updated = 0, skipped = 1)), state.value.message)
        assertTrue(state.value.friends.any { it.friend.name == "Zoe Lima" })
    }

    @Test
    fun addFriendManually() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddFriend("  ")
        vm.onAddFriend("Zoe")
        assertEquals(3, state.value.totalCount)
        assertEquals(FriendsMessage.Added("Zoe"), state.value.message)
    }

    @Test
    fun homeShowsComingUpCatchUpAndGreeting() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onExploreSample()
        val s = state.value
        assertEquals(FriendsMessage.SampleAdded, s.message)
        assertTrue(s.upcoming.isNotEmpty())
        assertTrue(s.catchUp.isNotEmpty())
        assertTrue(s.closeFriends.isNotEmpty())
        assertEquals(s.friends.size, s.sections.sumOf { it.second.size })
        assertFalse(s.week.isEmpty)
        assertEquals(Greeting.AFTERNOON, s.greeting) // FixedClock is 12:00 UTC

        val overdue = s.catchUp.first().friend.id
        vm.onMetToday(overdue)
        assertTrue(state.value.catchUp.none { it.friend.id == overdue })
        assertIs<FriendsMessage.LoggedWith>(state.value.message)
    }

    @Test
    fun calledAndSnoozeClearTheCatchUpNudge() = runViewModelTest {
        listOf("ana", "bob").forEach { g.updateFriend(FriendId(it)) { f -> f.copy(isCloseFriend = true, meetEveryDays = 7) } }
        val vm = vm()
        val state = collecting(vm.state)
        val (first, second) = state.value.catchUp.map { it.friend.id }
        vm.onCalled(first)
        assertIs<FriendsMessage.CallLogged>(state.value.message)
        assertEquals(InteractionType.CALL, g.interactionRepo.interactions.first().last().type)
        vm.onSnooze(second, SnoozeOption.WEEKEND)
        assertEquals(SnoozeOption.WEEKEND.until(g.clock.today()), (state.value.message as FriendsMessage.Snoozed).until)
        assertTrue(state.value.catchUp.none { it.friend.id == first || it.friend.id == second })
    }

    @Test
    fun sortByWarmthPutsColdestFirstInOneSection() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onExploreSample()
        vm.onFilterChange(FriendFilter.ALL)
        vm.onSortChange(FriendSort.WARMTH)
        val s = state.value
        assertEquals(1, s.sections.size)
        assertNull(s.sections.single().first)
        val values = s.friends.map { it.warmth.value }
        assertEquals(values.sorted(), values)
    }

    @Test
    fun searchLooksAcrossEveryoneRegardlessOfFilter() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onToggleClose(FriendId("bob"))
        vm.onFilterChange(FriendFilter.CLOSE)
        vm.onQueryChange("jazz")
        assertEquals(listOf("Ana"), state.value.friends.map { it.friend.name })
    }

    @Test
    fun pickedContactsAreAddedAsRealFriends() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactsPicked(emptyList())
        assertNull(state.value.message)
        vm.onContactsPicked(listOf(ContactRecord("9", "Zoe Lima", listOf("+351 1"), null)))
        assertEquals(FriendsMessage.ContactsPicked(PickedContactsResult(added = 1, updated = 0)), state.value.message)
        assertTrue(g.friendRepo.get(FriendId("contact-9"))!!.isCloseFriend)
    }

    @Test
    fun hidingWarmthHidesDriftingApart() = runViewModelTest {
        g.friendRepo.upsert(g.friendRepo.get(FriendId("ana"))!!.copy(isCloseFriend = true))
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.showWarmth)
        g.settingsRepo.update { it.copy(showWarmth = false) }
        assertFalse(state.value.showWarmth)
        assertTrue(state.value.drifting.isEmpty())
    }
}
