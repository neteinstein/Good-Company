package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle.CircleEditorMessage
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle.CircleEditorViewModel
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CircleEditorViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(
        listOf(
            friend("ana") { copy(isCloseFriend = true, closeness = Closeness.INNER, meetEveryDays = 7) },
            friend("rui"),
        ),
    )

    private fun vm(id: String?) = CircleEditorViewModel(
        id,
        g.observeFriends,
        g.observeGroups,
        g.saveGroup,
        g.deleteGroup,
        g.restoreGroup,
        g.nextCircleColor,
    )

    @Test
    fun aNewCircleStartsWithTheReminderOff_andCanHaveOneMember() = runViewModelTest {
        g.saveGroup(null, "Surf", "🏄", CircleColor.PURPLE, listOf(rui), null)
        val vm = vm(null)
        val state = collecting(vm.state)
        assertTrue(state.value.isNew)
        assertFalse(state.value.remind)
        assertEquals(CircleColor.BLUE, state.value.color) // the next colour in turn
        assertFalse(state.value.canSave)

        vm.onName("Mentor")
        assertFalse(state.value.canSave) // nobody in it yet
        vm.onMembers(listOf(ana, ana))
        assertEquals(listOf("ana"), state.value.members.map { it.id.value })
        assertEquals(listOf("Ana", "Rui"), state.value.everyone.map { it.name })
        vm.onEmoji(" 🧭 ")
        vm.onColor(CircleColor.SLATE)
        assertTrue(state.value.canSave)
        vm.onSave()
        assertTrue(state.value.closed)

        val saved = g.groupRepo.groups.value.single { it.name == "Mentor" }
        assertEquals(listOf(ana), saved.memberIds)
        assertEquals("🧭", saved.emoji)
        assertEquals(CircleColor.SLATE, saved.color)
        assertNull(saved.meetEveryDays) // off by default
        // A circle never changes a member's own rhythm or closeness.
        val friend = g.friendRepo.get(ana)!!
        assertEquals(7, friend.meetEveryDays)
        assertEquals(Closeness.INNER, friend.closeness)
    }

    @Test
    fun editingKeepsTheCircleAndItsReminder() = runViewModelTest {
        val choir = g.saveGroup(null, "Choir", "🎵", CircleColor.MAGENTA, listOf(ana, rui), 30)
        val vm = vm(choir.id)
        val state = collecting(vm.state)
        assertFalse(state.value.isNew)
        assertEquals("Choir", state.value.name)
        assertEquals(CircleColor.MAGENTA, state.value.color)
        assertTrue(state.value.remind)
        assertEquals(30, state.value.days)
        assertEquals(listOf(Closeness.INNER, null), state.value.members.map { it.closeness })

        vm.onRemoveMember(rui)
        vm.onDays(14)
        vm.onSave()
        val saved = g.groupRepo.groups.value.single()
        assertEquals(choir.id, saved.id)
        assertEquals(listOf(ana), saved.memberIds)
        assertEquals(14, saved.meetEveryDays)

        val off = vm(choir.id)
        val offState = collecting(off.state)
        off.onRemind(false)
        off.onSave()
        assertTrue(offState.value.closed)
        assertNull(g.groupRepo.groups.value.single().meetEveryDays)
    }

    @Test
    fun deleteOffersUndo_thenClosesWhenTheSnackbarGoes() = runViewModelTest {
        val choir = g.saveGroup(null, "Choir", "🎵", CircleColor.PURPLE, listOf(ana), null)
        g.setHomeCircleFilter(choir.id)
        val vm = vm(choir.id)
        val state = collecting(vm.state)
        vm.onDelete()
        assertEquals(CircleEditorMessage.Deleted("Choir"), state.value.message)
        assertTrue(g.groupRepo.groups.value.isEmpty())
        assertNull(g.settingsRepo.settings.first().homeCircleFilter) // Home goes back to All
        assertFalse(state.value.canSave)

        vm.onUndoDelete()
        assertEquals(listOf(choir), g.groupRepo.groups.value)
        assertNull(state.value.message)
        assertFalse(state.value.closed)

        vm.onDelete()
        vm.onMessageShown() // no Undo
        assertTrue(state.value.closed)
        assertTrue(g.groupRepo.groups.value.isEmpty())
    }

    @Test
    fun aMissingCircleOpensAsANewOne() = runViewModelTest {
        val vm = vm("gone")
        val state = collecting(vm.state)
        assertTrue(state.value.isNew)
        vm.onDelete() // nothing to delete
        assertNull(state.value.message)
    }
}
