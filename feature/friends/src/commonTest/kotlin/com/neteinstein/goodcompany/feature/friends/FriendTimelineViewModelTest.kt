package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.TimelineFilter
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.timeline.FriendTimelineMessage
import com.neteinstein.goodcompany.feature.friends.ui.screens.timeline.FriendTimelineViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class FriendTimelineViewModelTest {
    private val ana = FriendId("ana")
    private val g = TestGraph(listOf(friend("ana"), friend("rui")))

    private fun vm() = FriendTimelineViewModel(
        ana,
        g.observeTimeline,
        g.observeFriends,
        g.deleteTimelineEntry,
        g.restoreTimelineEntry,
        g.importChat,
        g.clock,
    )

    private suspend fun seed() {
        val now = g.clock.now()
        g.interactionRepo.addAll(listOf(Interaction(ana, InteractionType.CALL, now - 1.days, id = "c1")))
        g.interactionRepo.upsertJournal(JournalEntry("j1", ana, now - 40.days, "Moving to Porto"))
    }

    @Test
    fun groupsByMonthAndFilters() = runViewModelTest {
        seed()
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.isLoading)
        assertTrue(state.value.hasEntries)
        assertEquals("ana", state.value.friend?.id?.value)
        assertEquals(2, state.value.months.size)

        vm.onFilter(TimelineFilter.NOTES)
        val notes = state.value.months.flatMap { it.entries }
        assertEquals(listOf("journal-j1"), notes.map { it.key })

        vm.onFilter(TimelineFilter.GIFTS)
        assertTrue(state.value.months.isEmpty())
        assertTrue(state.value.hasEntries)
    }

    @Test
    fun deleteOffersUndoAndUndoRestores() = runViewModelTest {
        seed()
        val vm = vm()
        val state = collecting(vm.state)
        val call = state.value.months.flatMap { it.entries }.first { it is TimelineEntry.Contact }

        vm.onDelete(call)
        assertEquals(FriendTimelineMessage.Removed, state.value.message)
        assertTrue(g.interactionRepo.interactions.value.isEmpty())

        vm.onUndoDelete()
        assertEquals(listOf("c1"), g.interactionRepo.interactions.value.map { it.id })
        assertNull(state.value.undo)

        vm.onMessageShown()
        assertNull(state.value.message)
    }

    @Test
    fun chatImportMovedToTheTimeline() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onChatPicked(null)
        assertNull(state.value.message)
        vm.onChatPicked("not a chat")
        assertEquals(FriendTimelineMessage.ChatInvalid, state.value.message)
        vm.onChatPicked("01/03/2026, 10:00 - Ana: hi")
        assertEquals(FriendTimelineMessage.ChatImported(1, "Ana"), state.value.message)
        val day = state.value.months.flatMap { it.entries }.single()
        assertIs<TimelineEntry.Contact>(day)
        assertEquals(InteractionType.WHATSAPP, day.interaction.type)
    }
}
