package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.PickedContactsResult
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.AddFriendOption
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsMessage
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.PeopleSection
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.PeopleSort
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.addFriendOptions
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendsViewModelTest {
    private val g = TestGraph(listOf(friend("bob"), friend("ana") { copy(likes = listOf("jazz")) }))

    private fun vm(g: TestGraph = this.g) = FriendsViewModel(
        g.observeFriends,
        g.observeSettings,
        g.observeGroups,
        g.observePeopleDashboard,
        g.importPickedContacts,
        g.syncContacts,
        g.importContactsFile,
        g.addFriend,
        g.loadSampleData,
        g.setHomeCircleFilter,
        g.observeKinship,
        g.parseKinshipQuery,
        g.clock,
    )

    @Test
    fun friendsKidsStayOutOfTheList_unlessSearchedForOrFamily() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("ana") { copy(kids = listOf(Kid("leo", "Leo"))) },
                friend("rui") { copy(relationToMe = LinkType.SIBLING, kids = listOf(Kid("mia", "Mia"))) },
            ),
        )
        val vm = vm(g)
        val state = collecting(vm.state)
        // Rui is family, so his daughter shows; Ana's son doesn't.
        assertEquals(listOf("Ana", "Mia", "Rui"), state.value.friends.map { it.friend.name })
        assertEquals(listOf("Rui"), state.value.friends.single { it.friend.name == "Mia" }.parents.map { it.name })
        vm.onQueryChange("le")
        assertEquals(listOf("Leo"), state.value.friends.map { it.friend.name })
    }

    @Test
    fun aCircleFiltersTheList_andIsRemembered() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("ana") { copy(closeness = Closeness.INNER, meetEveryDays = 7) },
                friend("bob") { copy(closeness = Closeness.CLOSE, meetEveryDays = 7) },
                friend("zoe"),
            ),
        )
        val ana = FriendId("ana")
        val choir = g.saveGroup(null, "Choir", "🎵", CircleColor.PURPLE, listOf(ana, FriendId("zoe")), 30)
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(listOf("Choir"), state.value.circles.map { it.group.name })
        assertNull(state.value.circle)
        assertEquals(listOf("Ana", "Bob", "Zoe"), state.value.friends.map { it.friend.name })

        vm.onCircleFilter(choir.id)
        assertEquals(choir.id, state.value.circle?.group?.id)
        // Only members, whatever their closeness, still grouped by closeness; "All · N" keeps counting everyone.
        assertEquals(listOf("Ana", "Zoe"), state.value.friends.map { it.friend.name })
        assertEquals(
            listOf(PeopleSection.Level(Closeness.INNER) to 1, PeopleSection.Level(Closeness.ACQUAINTANCE) to 1),
            state.value.sections.map { (section, people) -> section to people.size },
        )
        assertEquals(3, state.value.listedCount)
        assertEquals(3, state.value.totalCount)

        // Remembered: a new People tab (next launch) opens on the same circle.
        assertEquals(choir.id, g.settingsRepo.settings.first().homeCircleFilter)
        val again = collecting(vm(g).state)
        assertEquals(choir.id, again.value.circle?.group?.id)

        // Deleting the circle puts the list back on All.
        g.deleteGroup(choir.id)
        assertNull(state.value.circle)
        assertNull(g.settingsRepo.settings.first().homeCircleFilter)
        assertEquals(listOf("Ana", "Bob", "Zoe"), state.value.friends.map { it.friend.name })

        // A filter that points nowhere (e.g. restored from another device) reads as All.
        g.setHomeCircleFilter("gone")
        assertNull(state.value.circle)
        vm.onCircleFilter(null)
        assertNull(g.settingsRepo.settings.first().homeCircleFilter)
    }

    @Test
    fun everyoneInOneList_groupedByCloseness_acquaintancesLast() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("zoe"),
                friend("bia") { copy(closeness = Closeness.GOOD) },
                friend("rui") { copy(closeness = Closeness.CLOSE) },
                friend("ana") { copy(closeness = Closeness.CLOSE) },
                friend("eva") { copy(closeness = Closeness.INNER) },
                friend("max"),
            ),
        )
        val vm = vm(g)
        val state = collecting(vm.state)
        val s = state.value
        assertEquals(
            listOf(
                PeopleSection.Level(Closeness.INNER) to listOf("Eva"),
                PeopleSection.Level(Closeness.CLOSE) to listOf("Ana", "Rui"),
                PeopleSection.Level(Closeness.GOOD) to listOf("Bia"),
                PeopleSection.Level(Closeness.ACQUAINTANCE) to listOf("Max", "Zoe"),
            ),
            s.sections.map { (section, people) -> section to people.map { it.friend.name } },
        )
        assertEquals(listOf("Eva", "Ana", "Rui", "Bia", "Max", "Zoe"), s.friends.map { it.friend.name })
        assertEquals(PeopleSort.CLOSENESS, s.sort)
    }

    @Test
    fun eachHeadingCountsItsPeople_withNoLevelFilter_andSearchKeepsTheGrouping() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("ana") { copy(closeness = Closeness.INNER) },
                friend("bob") { copy(closeness = Closeness.CLOSE) },
                friend("cat") { copy(closeness = Closeness.CLOSE) },
                friend("dan"),
                friend("kid") { copy(showInMyList = false) }, // F9: not listed, not counted
            ),
        )
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(4, state.value.listedCount)
        // Levels nobody is in (Friends) have no heading; the others show how many they hold.
        assertEquals(
            listOf(
                PeopleSection.Level(Closeness.INNER) to 1,
                PeopleSection.Level(Closeness.CLOSE) to 2,
                PeopleSection.Level(Closeness.ACQUAINTANCE) to 1,
            ),
            state.value.sections.map { (section, people) -> section to people.size },
        )

        // A search looks across every level and keeps the grouping.
        vm.onQueryChange("a")
        assertEquals(
            listOf(
                PeopleSection.Level(Closeness.INNER) to listOf("Ana"),
                PeopleSection.Level(Closeness.CLOSE) to listOf("Cat"),
                PeopleSection.Level(Closeness.ACQUAINTANCE) to listOf("Dan"),
            ),
            state.value.sections.map { (section, people) -> section to people.map { it.friend.name } },
        )
        vm.onQueryChange("")

        // Dan gets a closeness: his heading moves, the counts follow.
        g.setCloseness(FriendId("dan"), Closeness.GOOD)
        assertEquals(
            listOf(Closeness.INNER to 1, Closeness.CLOSE to 2, Closeness.GOOD to 1),
            state.value.sections.map { (section, people) -> (section as PeopleSection.Level).closeness to people.size },
        )
    }

    @Test
    fun aToZFromTheMenu_groupsByFirstLetter() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("bea") { copy(closeness = Closeness.INNER) },
                friend("ana"),
                friend("bob") { copy(closeness = Closeness.GOOD) },
            ),
        )
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSortChange(PeopleSort.NAME)
        assertEquals(
            listOf(PeopleSection.Letter('A') to listOf("Ana"), PeopleSection.Letter('B') to listOf("Bea", "Bob")),
            state.value.sections.map { (section, people) -> section to people.map { it.friend.name } },
        )
        vm.onSortChange(PeopleSort.CLOSENESS)
        assertEquals(
            listOf(PeopleSection.Level(Closeness.INNER), PeopleSection.Level(Closeness.GOOD), PeopleSection.Level(Closeness.ACQUAINTANCE)),
            state.value.sections.map { it.first },
        )
    }

    @Test
    fun yourPeopleCardSummarisesTheDashboard() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onExploreSample()
        assertEquals(FriendsMessage.SampleAdded, state.value.message)
        val card = state.value.yourPeople!!
        val dashboard = g.observePeopleDashboard().first()
        assertEquals(dashboard.people, card.people)
        assertEquals(dashboard.meetupsLast90Days, card.meetupsLast90Days)
        assertEquals(dashboard.cooling.map { it.friend }, card.cooling)
        assertTrue(card.people > 0)
    }

    @Test
    fun hidingWarmthIsPassedToTheList() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.showWarmth)
        g.settingsRepo.update { it.copy(showWarmth = false) }
        assertFalse(state.value.showWarmth)
        assertTrue(state.value.yourPeople!!.cooling.isEmpty())
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
        // Everyone added starts as an Acquaintance (issue #86).
        assertEquals(Closeness.ACQUAINTANCE, state.value.friends.single { it.friend.name == "Zoe" }.friend.closeness)
    }

    @Test
    fun pickedContactsAreAddedAsAcquaintances() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactsPicked(emptyList())
        assertNull(state.value.message)
        vm.onContactsPicked(listOf(ContactRecord("9", "Zoe Lima", listOf("+351 1"), null)))
        assertEquals(FriendsMessage.ContactsPicked(PickedContactsResult(added = 1, updated = 0)), state.value.message)
        assertEquals(Closeness.ACQUAINTANCE, g.friendRepo.get(FriendId("contact-9"))!!.closeness)
    }

    @Test
    fun addFriendOptions_followTheBuild() {
        // iOS and the Android sideload build: one "Pick from contacts" entry, the list with checkboxes (2026-11-14).
        assertEquals(
            listOf(AddFriendOption.CHOOSE_FROM_LIST, AddFriendOption.TYPE_IT_IN),
            addFriendOptions(hasContactPicker = true, hasContactList = true),
        )
        assertEquals(listOf(AddFriendOption.CHOOSE_FROM_LIST, AddFriendOption.TYPE_IT_IN), addFriendOptions(false, true))
        // Android Play build: picker only (D2, until the READ_CONTACTS policy check).
        assertEquals(listOf(AddFriendOption.PICK_FROM_CONTACTS, AddFriendOption.TYPE_IT_IN), addFriendOptions(true, false))
        // Web.
        assertEquals(listOf(AddFriendOption.TYPE_IT_IN), addFriendOptions(false, false))
    }

    @Test
    fun aRefusedContactList_isRemembered_andSavedPeopleAreReported() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactListPermissionResult(false)
        assertTrue(state.value.contactListDenied)
        // A refusal falls back to the system picker, once.
        assertTrue(state.value.pickerFallback)
        vm.onPickerFallbackShown()
        assertFalse(state.value.pickerFallback)
        assertTrue(state.value.contactListDenied)
        vm.onContactListPermissionResult(true)
        assertFalse(state.value.contactListDenied)
        assertFalse(state.value.pickerFallback)
        vm.onContactsChosen(0)
        assertNull(state.value.message)
        vm.onContactsChosen(2)
        assertEquals(FriendsMessage.ContactsPicked(PickedContactsResult(2, 0)), state.value.message)
    }

    @Test
    fun myNieces_findsConfirmedKinshipOnly_andCountsWhatsSuggested() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("rui") { copy(relationToMe = LinkType.SIBLING) },
                friend("leonor") { copy(relationToMe = LinkType.NIECE_NEPHEW, showInMyList = false) },
                friend("mia"),
                friend("bia") { copy(relationToMe = LinkType.COUSIN) },
            ),
        )
        // Mia is Rui's daughter too, but nobody has confirmed she's my niece yet.
        g.linkRepo.upsert(PersonLink("1", FriendId("rui"), FriendId("mia"), LinkType.CHILD))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(1, state.value.suggestedFamily)
        vm.onQueryChange("My nieces and nephews")
        assertEquals(setOf(LinkType.NIECE_NEPHEW), state.value.kinship)
        assertEquals(listOf("Leonor"), state.value.friends.map { it.friend.name })
        vm.onQueryChange("my nieces, my cousins")
        assertEquals(listOf("Bia", "Leonor"), state.value.friends.map { it.friend.name }.sorted())
        vm.onQueryChange("leo")
        assertNull(state.value.kinship)
        assertEquals(listOf("Leonor"), state.value.friends.map { it.friend.name })
    }
}
