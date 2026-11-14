package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.tags.TagsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.tags.TagsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagsViewModelTest {
    private val g = TestGraph(
        friends = listOf(
            friend("ana", "Ana") { copy(likes = listOf("Sushi", "jazz"), kids = listOf(Kid("leo", "Leo", likes = listOf("sushi")))) },
            friend("pedro", "Pedro") { copy(dislikes = listOf("sushi"), food = listOf("Ramen")) },
            friend("rui", "Rui") { copy(interests = listOf("hiking")) },
        ),
    )

    private fun vm() = TagsViewModel(g.observeTags, g.observeTagDuplicates, g.renameTag, g.deleteTag, g.restoreTag, g.keepTagSpellings)

    private suspend fun all() = g.friendRepo.friends.value

    @Test
    fun listsTagsAToZ_offersCaseOnlyDuplicates_andSearches() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(listOf("hiking", "jazz", "Ramen", "Sushi", "sushi"), state.value.tags.map { it.name })
        assertEquals(listOf(listOf("sushi", "Sushi")), state.value.duplicates.map { g -> g.map { it.name } })
        vm.onQueryChange("SÚ")
        assertEquals(listOf("Sushi", "sushi"), state.value.shown.map { it.name })
    }

    @Test
    fun mergeFixesEveryPageAtOnce() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onMerge(state.value.duplicates.single())
        assertEquals(TagsMessage.Merged("sushi"), state.value.message)
        assertTrue(state.value.duplicates.isEmpty())
        val byId = all().associateBy { it.id.value }
        assertEquals(listOf("sushi", "jazz"), byId.getValue("ana").likes)
        // Leo, Ana's kid, is a person of his own (F9).
        assertEquals(listOf("sushi"), byId.getValue("leo").likes)
        assertEquals(listOf("sushi"), byId.getValue("pedro").dislikes)
    }

    @Test
    fun keepBothHidesTheOffer() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onKeepBoth(state.value.duplicates.single())
        assertTrue(state.value.duplicates.isEmpty())
        assertEquals(2, state.value.tags.count { it.key == "sushi" })
    }

    @Test
    fun renameAndRenameIntoAnotherTagMerges() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onRename(state.value.tags.first { it.name == "Ramen" }, "  Ramen noodles ")
        assertEquals(TagsMessage.Renamed("Ramen noodles"), state.value.message)
        assertEquals(listOf("Ramen noodles"), all().first { it.id.value == "pedro" }.food)

        // Typing another tag's spelling (ignoring case) merges into that one.
        vm.onRename(state.value.tags.first { it.name == "jazz" }, "HIKING")
        assertEquals(TagsMessage.Merged("hiking"), state.value.message)
        assertEquals(listOf("Sushi", "hiking"), all().first { it.id.value == "ana" }.likes)

        // Unchanged or blank names do nothing.
        vm.onMessageShown()
        vm.onRename(state.value.tags.first { it.name == "hiking" }, "hiking")
        vm.onRename(state.value.tags.first { it.name == "hiking" }, " ")
        assertNull(state.value.message)
    }

    @Test
    fun deleteRemovesFromEveryone_withUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDelete(state.value.tags.first { it.name == "sushi" })
        assertEquals(TagsMessage.Deleted("sushi"), state.value.message)
        assertTrue(all().none { "sushi" in it.dislikes || "sushi" in it.likes })
        assertEquals(listOf("Sushi", "jazz"), all().first { it.id.value == "ana" }.likes)

        vm.onUndoDelete()
        vm.onMessageShown()
        assertEquals(listOf("sushi"), all().first { it.id.value == "pedro" }.dislikes)
        assertEquals(listOf("sushi"), all().first { it.id.value == "leo" }.likes)
        assertNull(state.value.undo)
    }
}
