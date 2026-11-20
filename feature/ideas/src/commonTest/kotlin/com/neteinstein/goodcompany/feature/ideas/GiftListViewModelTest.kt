package com.neteinstein.goodcompany.feature.ideas

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.PeopleFilter
import com.neteinstein.goodcompany.core.domain.usecase.PeopleFiltering
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.ideas.ui.screens.gifts.GiftListMessage
import com.neteinstein.goodcompany.feature.ideas.ui.screens.gifts.GiftListViewModel
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

    private fun list(g: TestGraph, id: String) =
        GiftListViewModel(id, g.observeGiftList, g.observeGroups, g.observeSettings, g.giftLists, g.parseKinshipQuery, g.suggestGifts)

    @Test
    fun aChristmasListForTheCousins_tracksToBuyBoughtGiven_withABudget_andFillsTheirGiftHistory() = runViewModelTest {
        val g = graph()
        val id = g.giftLists.create("Christmas 2026", GiftOccasion.CHRISTMAS, LocalDate(2026, 12, 25))!!

        val vm = list(g, id)
        val state = collecting(vm.state)
        vm.onStartAdding()
        // The picker's kinship search ("my cousins") and its "Tick all".
        val cousins = vm.kinshipOf("my cousins")
        assertEquals(setOf(LinkType.COUSIN), cousins)
        val shown = PeopleFiltering.apply(state.value.addable, PeopleFilter.Everyone, state.value.circles, "my cousins", cousins).people
        assertEquals(listOf("Bruno Costa", "Tomás Costa"), shown.map { it.name })
        vm.onAddBudget("€40")
        vm.onConfirmAdding(shown.map { it.id })
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

        // The Plans tab's card counts it.
        val summary = g.observeGiftLists().first().current.single()
        assertEquals(1, summary.gifts)
        assertTrue(summary.allGiven)
    }

    @Test
    fun aWholeCircle_isACircleInThePicker_withOnlyThoseNotOnTheList() = runViewModelTest {
        val g = graph()
        g.groupRepo.upsert(FriendGroup("c", "Cousins", "🌳", listOf(FriendId("tomas"), FriendId("bruno"))))
        val id = g.giftLists.create("Christmas", GiftOccasion.CHRISTMAS, null)!!
        g.giftLists.addPeople(id, listOf(FriendId("tomas")))
        val vm = list(g, id)
        val state = collecting(vm.state)
        vm.onStartAdding()
        assertTrue(state.value.circles.any { it.id == "c" })
        val inCircle = PeopleFiltering.apply(state.value.addable, PeopleFilter(circleId = "c"), state.value.circles).people
        assertEquals(listOf(FriendId("bruno")), inCircle.map { it.id })
        vm.onConfirmAdding(inCircle.map { it.id })
        assertEquals(listOf("tomas", "bruno"), g.giftListRepo.get(id)!!.entries.map { it.friendId.value })
        vm.onStartAdding()
        vm.onConfirmAdding(emptyList()) // nobody picked: stays open
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
    fun aMissingList_showsNothing() = runViewModelTest {
        val g = graph()
        val missing = list(g, "nope")
        val missingState = collecting(missing.state)
        assertNull(missingState.value.detail)
        assertFalse(missingState.value.isLoading)
    }
}
