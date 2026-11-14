package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts.GiftListMessage
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts.GiftListViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts.GiftListsMessage
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts.GiftListsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiftListViewModelTest {
    private fun graph(ai: Boolean = false) = TestGraph(
        listOf(
            friend("tomas", "Tomás Costa") { copy(relationToMe = LinkType.COUSIN) },
            friend("bruno", "Bruno Costa") {
                copy(relationToMe = LinkType.COUSIN, gifts = listOf(Gift("lego", "LEGO", url = "https://shop", price = "€35")))
            },
            friend("ana", "Ana"),
        ),
        settings = AppSettings(aiApiKey = "key".takeIf { ai }, aiEnabled = ai),
    )

    private fun lists(g: TestGraph) = GiftListsViewModel(g.observeGiftLists, g.giftLists, g.clock)

    private fun list(g: TestGraph, id: String) =
        GiftListViewModel(id, g.observeGiftList, g.observeGroups, g.observeSettings, g.giftLists, g.parseKinshipQuery, g.suggestGifts)

    @Test
    fun aChristmasListForTheCousins_tracksToBuyBoughtGiven_withABudget_andFillsTheirGiftHistory() = runViewModelTest {
        val g = graph()
        val overview = lists(g)
        val overviewState = collecting(overview.state)
        overview.onNewList()
        overview.onCreate("Christmas 2026", GiftOccasion.CHRISTMAS, LocalDate(2026, 12, 25))
        val id = overviewState.value.open!!
        assertNull(overviewState.value.creating)
        overview.onOpened()

        val vm = list(g, id)
        val state = collecting(vm.state)
        vm.onStartAdding()
        vm.onAddQuery("my cousins")
        assertEquals(setOf(LinkType.COUSIN), state.value.adding!!.kinship)
        assertEquals(listOf("Bruno Costa", "Tomás Costa"), state.value.matches.map { it.name })
        state.value.matches.forEach { vm.onTogglePerson(it.id) }
        vm.onAddBudget("€40")
        vm.onConfirmAdding()
        assertNull(state.value.adding)
        val people = state.value.detail!!.people
        assertEquals(listOf(40.0, 40.0), people.map { it.entry.budget })

        val bruno = FriendId("bruno")
        vm.onAddIdea(bruno, "lego")
        vm.onAdvance(bruno, "lego")
        assertEquals(35.0, state.value.detail!!.totals.spent)
        assertEquals(80.0, state.value.detail!!.totals.planned)
        vm.onAdvance(bruno, "lego")
        assertEquals(GiftStatus.GIVEN, state.value.detail!!.people.first { it.friend.id == bruno }.gifts.single().status)
        val given = g.friendRepo.get(bruno)!!.gifts.single()
        assertEquals(g.clock.today(), given.givenOn)
        assertEquals(GiftOccasion.CHRISTMAS, given.occasion)

        vm.onSetBudget(FriendId("tomas"), "25")
        assertEquals(25.0, g.giftListRepo.get(id)!!.entryFor(FriendId("tomas"))!!.budget)
        vm.onAddGift(FriendId("tomas"), "Climbing shoes", "€80")
        assertEquals(listOf("Climbing shoes"), state.value.detail!!.people.first { it.friend.id.value == "tomas" }.gifts.map { it.gift.title })
        val shoes = g.friendRepo.get(FriendId("tomas"))!!.gifts.single().id
        vm.onRemoveGift(FriendId("tomas"), shoes)
        assertTrue(state.value.detail!!.people.first { it.friend.id.value == "tomas" }.gifts.isEmpty())

        // The overview counts it.
        val summary = overviewState.value.current.single()
        assertEquals(1, summary.gifts)
        assertTrue(summary.allGiven)
    }

    @Test
    fun aWholeCircle_ticksItsMembersNotOnTheList_andAgainUnticksThem() = runViewModelTest {
        val g = graph()
        g.groupRepo.upsert(FriendGroup("c", "Cousins", "🌳", listOf(FriendId("tomas"), FriendId("bruno"))))
        val id = g.giftLists.create("Christmas", GiftOccasion.CHRISTMAS, null)!!
        g.giftLists.addPeople(id, listOf(FriendId("tomas")))
        val vm = list(g, id)
        val state = collecting(vm.state)
        vm.onStartAdding()
        vm.onToggleCircle("c")
        assertEquals(setOf(FriendId("bruno")), state.value.adding!!.picked)
        vm.onToggleCircle("c")
        assertTrue(state.value.adding!!.picked.isEmpty())
        vm.onToggleCircle("c")
        vm.onConfirmAdding()
        assertEquals(listOf("tomas", "bruno"), g.giftListRepo.get(id)!!.entries.map { it.friendId.value })
        vm.onStartAdding()
        vm.onConfirmAdding() // nobody picked: stays open
        assertTrue(state.value.adding != null)
        vm.onDismissAdding()
        assertNull(state.value.adding)
    }

    @Test
    fun takingSomeoneOff_canBeUndone() = runViewModelTest {
        val g = graph()
        val id = g.giftLists.create("Christmas", null, null)!!
        g.giftLists.addPeople(id, listOf(FriendId("ana")), 20.0)
        val vm = list(g, id)
        val state = collecting(vm.state)
        vm.onRemovePerson(FriendId("ana"))
        assertEquals(GiftListMessage.Removed("Ana"), state.value.message)
        assertTrue(state.value.detail!!.people.isEmpty())
        vm.onUndoRemove()
        vm.onMessageShown()
        assertEquals(listOf(20.0), state.value.detail!!.people.map { it.entry.budget })
        assertNull(state.value.message)
    }

    @Test
    fun aiIdeas_onlyWithAiOn_andKeptOnesGoOnTheList() = runViewModelTest {
        val off = graph(ai = false)
        val offId = off.giftLists.create("Christmas", null, null)!!
        off.giftLists.addPeople(offId, listOf(FriendId("ana")))
        val offVm = list(off, offId)
        val offState = collecting(offVm.state)
        assertFalse(offState.value.hasAiKey)
        offVm.onSuggest(FriendId("ana"))
        assertTrue(offState.value.suggestions.isEmpty())

        val g = graph(ai = true)
        val id = g.giftLists.create("Christmas", null, null)!!
        g.giftLists.addPeople(id, listOf(FriendId("ana")))
        val vm = list(g, id)
        val state = collecting(vm.state)
        assertTrue(state.value.hasAiKey)
        vm.onSuggest(FriendId("ana"))
        assertEquals(listOf("Vinyl", "Concert tickets"), state.value.suggestions[FriendId("ana")])
        vm.onKeepSuggestion(FriendId("ana"), "Vinyl")
        assertEquals(listOf("Concert tickets"), state.value.suggestions[FriendId("ana")])
        assertEquals(listOf("Vinyl"), state.value.detail!!.people.single().gifts.map { it.gift.title })
        vm.onDismissSuggestions(FriendId("ana"))
        assertTrue(state.value.suggestions.isEmpty())

        g.assistant.gifts = Result.success(emptyList())
        vm.onSuggest(FriendId("ana"))
        assertEquals(GiftListMessage.NoNewIdeas, state.value.message)
        g.assistant.gifts = Result.failure(IllegalStateException("down"))
        vm.onSuggest(FriendId("ana"))
        assertIs<GiftListMessage.AiError>(state.value.message)
    }

    @Test
    fun editAndDuplicate_andAMissingList() = runViewModelTest {
        val g = graph()
        val id = g.giftLists.create("Christmas 2025", GiftOccasion.CHRISTMAS, LocalDate(2025, 12, 25))!!
        g.giftLists.addPeople(id, listOf(FriendId("ana")), 30.0)
        val vm = list(g, id)
        val state = collecting(vm.state)
        vm.onEdit()
        assertTrue(state.value.editing)
        vm.onSaveEdit("Natal 2025", GiftOccasion.CHRISTMAS, LocalDate(2025, 12, 24))
        assertFalse(state.value.editing)
        assertEquals("Natal 2025", state.value.detail!!.list.title)
        vm.onDuplicate()
        val copy = g.giftListRepo.get(state.value.open!!)!!
        assertEquals("Natal 2026", copy.title)
        assertEquals(listOf(30.0), copy.entries.map { it.budget })
        vm.onOpened()
        assertNull(state.value.open)

        val missing = list(g, "nope")
        val missingState = collecting(missing.state)
        assertNull(missingState.value.detail)
        assertFalse(missingState.value.isLoading)
    }

    @Test
    fun theOverview_duplicatesAndDeletesWithUndo() = runViewModelTest {
        val g = graph()
        val id = g.giftLists.create("Christmas 2025", GiftOccasion.CHRISTMAS, LocalDate(2025, 12, 25))!!
        val vm = lists(g)
        val state = collecting(vm.state)
        assertEquals(listOf(id), state.value.past.map { it.list.id })
        vm.onDuplicate(id)
        val copy = state.value.open!!
        assertEquals(listOf(copy), state.value.current.map { it.list.id })
        vm.onDelete(copy)
        assertEquals(GiftListsMessage.Deleted("Christmas 2026"), state.value.message)
        assertTrue(state.value.current.isEmpty())
        vm.onUndoDelete()
        vm.onMessageShown()
        assertEquals(listOf(copy), state.value.current.map { it.list.id })
        vm.onNewList()
        vm.onDismissNew()
        assertNull(state.value.creating)
        assertEquals(2, g.giftListRepo.lists.first().size)
    }
}
