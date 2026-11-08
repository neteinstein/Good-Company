package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareMessage
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareToFriendViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareToFriendViewModelTest {
    private fun graph(apiKey: String? = null) = TestGraph(
        listOf(friend("ana", "Ana Silva"), friend("rui", "Rui Costa") { copy(isCloseFriend = true) }),
        settings = AppSettings(aiApiKey = apiKey),
    )

    private fun vm(g: TestGraph) = ShareToFriendViewModel(
        g.shareInbox,
        g.observeFriends,
        g.observeSettings,
        g.addLifeUpdate,
        g.suggestUpdateFromShared,
    )

    @Test
    fun pickAFriendEditTheDraftAndSave() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "Ana got the job!\nmore text", url = "https://instagram.com/p/1"))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(listOf("ana", "rui"), state.value.friends.map { it.id.value })
        assertEquals(setOf(FriendId("ana")), state.value.mentioned)

        vm.onQueryChange("ru")
        assertEquals(listOf("rui"), state.value.friends.map { it.id.value })
        vm.onQueryChange("")

        val ana = state.value.friends.first()
        vm.onSelect(ana)
        assertEquals("Ana got the job!", state.value.draft)
        vm.onChangeFriend()
        assertNull(state.value.selected)
        vm.onSelect(ana)
        vm.onDraftChange("Got the job at Studio X")
        vm.onSave()
        assertEquals(FriendId("ana"), state.value.savedTo)
        assertNull(g.shareInbox.pending.value)
        assertEquals(listOf("Got the job at Studio X"), g.friendRepo.friends.value.first { it.id.value == "ana" }.lifeUpdates.map { it.text })
    }

    @Test
    fun aiFillsTheDraftOrSaysItFoundNothing() = runViewModelTest {
        val g = graph(apiKey = "k")
        g.shareInbox.offer(SharedContent(image = SharedImage(byteArrayOf(1), "image/jpeg")))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertTrue(state.value.hasAiKey)
        vm.onSelect(state.value.friends.first { it.id.value == "rui" })
        assertEquals("", state.value.draft)
        vm.onSuggest()
        assertEquals("Started a new job", state.value.draft)

        g.assistant.lifeUpdate = Result.success(null)
        vm.onSuggest()
        assertEquals(ShareMessage.NothingFound("Rui"), state.value.message)
        vm.onMessageShown()

        g.assistant.lifeUpdate = Result.failure(IllegalStateException("boom"))
        vm.onSuggest()
        assertIs<ShareMessage.AiError>(state.value.message)
    }

    @Test
    fun cancelClearsTheInboxAndCloses() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "hi"))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSave() // nobody picked: no-op
        vm.onCancel()
        assertTrue(state.value.closed)
        assertNull(g.shareInbox.pending.value)
    }
}
