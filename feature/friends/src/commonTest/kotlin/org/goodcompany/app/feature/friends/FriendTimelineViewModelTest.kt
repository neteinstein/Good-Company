package org.goodcompany.app.feature.friends

import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Gift
import org.goodcompany.app.core.domain.model.Interaction
import org.goodcompany.app.core.domain.model.InteractionType
import org.goodcompany.app.core.domain.model.JournalEntry
import org.goodcompany.app.core.domain.model.LifeUpdate
import org.goodcompany.app.core.domain.usecase.TimelineEntry
import org.goodcompany.app.core.domain.usecase.TimelineFilter
import org.goodcompany.app.core.testing.Fixtures
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.feature.friends.ui.screens.timeline.FriendTimelineMessage
import org.goodcompany.app.feature.friends.ui.screens.timeline.FriendTimelineViewModel
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

    private fun vm(g: TestGraph = this.g) = FriendTimelineViewModel(
        ana,
        g.observeTimeline,
        g.observeFriends,
        g.deleteTimelineEntry,
        g.restoreTimelineEntry,
        g.importChat,
        g.clock,
        g.editTimelineEntry,
        g.updateFriend,
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

    @Test
    fun newsAndGiftsEditInPlace_blankTextChangesNothing() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("ana") {
                    copy(
                        lifeUpdates = listOf(LifeUpdate("u1", "New job", Fixtures.at(2026, 3, 1))),
                        gifts = listOf(Gift("g1", "Book", note = "Signed", givenOn = LocalDate(2026, 2, 1))),
                    )
                },
            ),
        )
        val vm = vm(g)
        val state = collecting(vm.state)
        val entries = state.value.months.flatMap { it.entries }
        val news = entries.filterIsInstance<TimelineEntry.WhatsNew>().single()
        val gift = entries.filterIsInstance<TimelineEntry.GiftGiven>().single()

        vm.onEditNews(news, "Started at the museum")
        vm.onEditNews(news, " ")
        vm.onEditGift(gift.gift, " Poster ", "Framed")
        vm.onEditGift(gift.gift, "", "x")

        val ana = g.friendRepo.get(this@FriendTimelineViewModelTest.ana)!!
        assertEquals("Started at the museum", ana.lifeUpdates.single().text)
        assertEquals(Fixtures.at(2026, 3, 1), ana.lifeUpdates.single().addedAt)
        val edited = ana.gifts.single()
        assertEquals("Poster", edited.title)
        assertEquals("Framed", edited.note)
        assertEquals(LocalDate(2026, 2, 1), edited.givenOn)
        // The timeline follows.
        val titles = state.value.months.flatMap { it.entries }.filterIsInstance<TimelineEntry.GiftGiven>().map { it.gift.title }
        assertEquals(listOf("Poster"), titles)
    }
}
