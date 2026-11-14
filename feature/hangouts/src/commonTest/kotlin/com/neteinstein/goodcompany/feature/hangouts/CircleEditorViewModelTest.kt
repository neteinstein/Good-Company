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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CircleEditorViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(
        listOf(
            friend("ana") { copy(closeness = Closeness.INNER, meetEveryDays = 7) },
            friend("rui"),
        ),
    )

    private fun vm(id: String?, withFriend: FriendId? = null) = CircleEditorViewModel(
        id,
        withFriend,
        g.observeFriends,
        g.observeGroups,
        g.saveGroup,
        g.deleteGroup,
        g.restoreGroup,
        g.nextCircleColor,
        g.circleCovers,
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
    fun newCircleWithSomeone_startsWithThemIn() = runViewModelTest {
        val vm = vm(null, withFriend = rui)
        val state = collecting(vm.state)
        assertTrue(state.value.isNew)
        assertEquals(listOf(rui), state.value.members.map { it.id })
        vm.onName("Surf")
        assertTrue(state.value.canSave) // only the name was missing
        vm.onSave()
        assertEquals(listOf(rui), g.groupRepo.groups.value.single { it.name == "Surf" }.memberIds)
    }

    @Test
    fun anExistingCircleIgnoresWithFriend() = runViewModelTest {
        val choir = g.saveGroup(null, "Choir", "🎵", CircleColor.MAGENTA, listOf(ana), null)
        val state = collecting(vm(choir.id, withFriend = rui).state)
        assertEquals(listOf(ana), state.value.members.map { it.id })
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
        assertEquals(listOf(Closeness.INNER, Closeness.ACQUAINTANCE), state.value.members.map { it.closeness })

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

    // --- Cover photo (issue #88) ---

    @Test
    fun aNewCirclesCover_isSavedWithTheCircle() = runViewModelTest {
        val vm = vm(null)
        val state = collecting(vm.state)
        vm.onCoverPicked(byteArrayOf(1, 2, 3))
        assertNotNull(state.value.cover)
        assertTrue(g.coverRepo.covers.first().isEmpty()) // nothing stored before Save
        vm.onName("Surf")
        vm.onMembers(listOf(ana))
        vm.onSave()
        val id = g.groupRepo.groups.value.single().id
        assertNotNull(g.coverRepo.covers.first()[id])
    }

    @Test
    fun aNewCircleCanDropThePickedCover() = runViewModelTest {
        val vm = vm(null)
        val state = collecting(vm.state)
        vm.onCoverPicked(byteArrayOf(1))
        vm.onRemoveCover()
        assertNull(state.value.cover)
        vm.onName("Surf")
        vm.onMembers(listOf(ana))
        vm.onSave()
        assertTrue(g.coverRepo.covers.first().isEmpty())
    }

    @Test
    fun anExistingCirclesCover_isSavedAtOnce_andRemovalHasUndo() = runViewModelTest {
        val saved = g.saveGroup(null, "Surf", "🏄", CircleColor.TEAL, listOf(ana), null)
        val vm = vm(saved.id)
        val state = collecting(vm.state)
        vm.onCoverPicked(byteArrayOf(4, 5))
        val cover = g.coverRepo.covers.first()[saved.id]
        assertNotNull(cover)
        assertEquals(cover, state.value.cover)

        vm.onRemoveCover()
        assertEquals(CircleEditorMessage.CoverRemoved, state.value.message)
        assertNull(state.value.cover)
        vm.onUndoRemoveCover()
        assertEquals(cover, g.coverRepo.covers.first()[saved.id])
        assertEquals(cover, state.value.cover)
    }

    @Test
    fun anUnreadablePicture_leavesTheCoverAsItWas() = runViewModelTest {
        val vm = vm(null)
        val state = collecting(vm.state)
        vm.onCoverPicked(ByteArray(0))
        assertEquals(CircleEditorMessage.CoverUnreadable, state.value.message)
        assertNull(state.value.cover)
    }
}
