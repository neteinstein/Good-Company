package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.ContactListFilter
import com.neteinstein.goodcompany.core.domain.usecase.PickedContactsResult
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.choose.ChooseContactsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChooseContactsViewModelTest {
    private val photo = ContactPhoto.of(byteArrayOf(9, 9))!!
    private val contacts = listOf(
        ContactRecord("1", "Rui Costa", emptyList(), null, photo, starred = true),
        ContactRecord("2", "Ana Silva", listOf("+351 912 000 000"), Birthday(11, 11), photo),
        ContactRecord("3", "Bea Rocha", emptyList(), null, photo),
        ContactRecord("4", "Carla", emptyList(), null, photo),
    )

    private fun graph() = TestGraph(listOf(friend("carla") { copy(contactId = "4") })).also { it.friendRepo.contacts = contacts }

    private fun vm(g: TestGraph) = ChooseContactsViewModel(g.listContacts, g.saveChosenContacts)

    @Test
    fun listsFavouritesThenLetters_withNothingTicked() = runViewModelTest {
        val vm = vm(graph())
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertEquals(4, s.total)
        assertTrue(s.selected.isEmpty())
        assertFalse(s.canSave)
        assertEquals(listOf(null, "A", "B", "C"), s.sections.map { it.letter })
        assertEquals("Rui Costa", s.sections.first().items.single().record.name)
        assertTrue(s.sections.last().items.single().alreadyFriend)
    }

    @Test
    fun ticking_searching_andFilters() = runViewModelTest {
        val vm = vm(graph())
        vm.onToggle("2")
        vm.onToggle("3")
        vm.onToggle("3")
        vm.onToggle("4") // already a friend: can't change
        vm.onToggle("nope")
        assertEquals(setOf("2"), vm.state.value.selected)
        assertTrue(vm.state.value.canSave)

        vm.onQueryChange("912")
        assertEquals(listOf("2"), vm.state.value.sections.flatMap { it.items }.map { it.record.contactId })
        vm.onQueryChange("")
        vm.onFilterChange(ContactListFilter.WITH_BIRTHDAY)
        assertEquals(listOf("2"), vm.state.value.sections.flatMap { it.items }.map { it.record.contactId })
        vm.onFilterChange(ContactListFilter.ALL)
        assertEquals(listOf("A", "B", "C", "R"), vm.state.value.sections.map { it.letter })
        // Filters never untick anyone.
        assertEquals(setOf("2"), vm.state.value.selected)
    }

    @Test
    fun savingAddsOnlyTheTicked_withTheirPhotosOnly() = runViewModelTest {
        val g = graph()
        val vm = vm(g)
        vm.onSave() // nothing ticked: nothing happens
        assertNull(vm.state.value.saved)
        vm.onToggle("1")
        vm.onToggle("2")
        vm.onSave()
        assertEquals(PickedContactsResult(2, 0), vm.state.value.saved)
        assertEquals(setOf("1", "2", "4"), g.friendRepo.friends.value.mapNotNull { it.contactId }.toSet())
        assertEquals(listOf(setOf("1", "2")), g.friendRepo.photoRequests)
        assertEquals(listOf(false), g.friendRepo.contactReads)
        assertTrue(g.friendRepo.friends.value.filter { it.contactId in setOf("1", "2") }.all { it.photo == photo })
    }

    @Test
    fun reopeningStartsFresh_andThePickerIsTheFallback() = runViewModelTest {
        val g = graph()
        val vm = vm(g)
        vm.onOpened()
        vm.onToggle("2")
        vm.onOpened() // opened again later (onboarding keeps the ViewModel)
        assertTrue(vm.state.value.selected.isEmpty())
        assertEquals(2, g.friendRepo.contactReads.size)

        g.friendRepo.contacts = emptyList()
        vm.reload()
        assertTrue(vm.state.value.isEmpty)
        vm.onPicked(emptyList())
        assertNull(vm.state.value.saved)
        vm.onPicked(listOf(ContactRecord("9", "Eva", emptyList(), null)))
        assertEquals(PickedContactsResult(1, 0), vm.state.value.saved)
    }
}
