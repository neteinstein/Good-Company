package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.ShareChoice
import com.neteinstein.goodcompany.core.domain.usecase.ShareReason
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.PickedTarget
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareMessage
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareToFriendViewModel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareToFriendViewModelTest {
    private fun graph(apiKey: String? = null) = TestGraph(
        listOf(
            friend("ana", "Ana Silva"),
            friend("rui", "Rui Costa") { copy(isCloseFriend = true, phoneNumbers = listOf("+351 912 345 678")) },
            friend("bea", "Beatriz Rocha"),
        ),
        settings = AppSettings(aiApiKey = apiKey),
    )

    private fun vm(g: TestGraph) = ShareToFriendViewModel(
        g.shareInbox,
        g.observeShareTargets,
        g.observeSettings,
        g.saveShared,
        g.suggestUpdateFromShared,
        g.addFriend,
        g.clock,
    )

    @Test
    fun ranksByNameNumberAndRecency_thenSavesAChatAsAMessage() = runViewModelTest {
        val g = graph()
        g.checkIn(listOf(FriendId("bea")), null, null, "", at(2026, 3, 9))
        g.shareInbox.offer(SharedContent(text = "Ana: call me on 912 345 678"))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(listOf("ana", "rui", "bea"), state.value.friends.map { it.friend.id.value })
        assertEquals(listOf(ShareReason.MENTIONED, ShareReason.NUMBER, ShareReason.RECENT), state.value.friends.map { it.reason })

        vm.onQueryChange("ru")
        assertEquals(listOf("rui"), state.value.friends.map { it.friend.id.value })
        vm.onQueryChange("")

        vm.onSelect(state.value.friends[1].friend)
        assertIs<PickedTarget.Person>(state.value.target)
        // Chat-like text: counts as a message, today, caption prefilled from the text.
        assertEquals(ShareChoice.INTERACTION, state.value.choice)
        assertEquals(LogKind.MESSAGE, state.value.kind)
        assertEquals(g.clock.today(), state.value.date)
        assertEquals("Ana: call me on 912 345 678", state.value.caption)
        vm.onChangeFriend()
        assertNull(state.value.target)
        vm.onSelect(state.value.friends[1].friend)
        vm.onCaptionChange("Wants a call")
        vm.onSave()
        assertEquals(FriendId("rui"), state.value.savedTo)
        assertNull(g.shareInbox.pending.value)
        val logged = g.interactionRepo.interactions.value.single()
        assertEquals(InteractionSource.SHARE, logged.source)
        assertEquals("Wants a call", g.interactionRepo.attachments.value.single().caption)
    }

    @Test
    fun aPhotoIsGuessedAsMetOnItsDate_andCanBeKeptAsANote() = runViewModelTest {
        val g = graph()
        val ref = PhotoRef(PhotoPlatform.ANDROID, "content://picker/9")
        g.shareInbox.offer(SharedContent(image = SharedImage(byteArrayOf(1, 2), "image/jpeg"), photoRef = ref, takenOn = LocalDate(2026, 3, 7)))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSelect(state.value.friends.first { it.friend.id.value == "ana" }.friend)
        assertEquals(ShareChoice.INTERACTION, state.value.choice)
        assertEquals(LogKind.MEETUP, state.value.kind)
        assertEquals(LocalDate(2026, 3, 7), state.value.date)
        vm.onDate(LocalDate(2030, 1, 1))
        assertEquals(g.clock.today(), state.value.date, "never in the future")
        vm.onKind(LogKind.CALL)
        assertEquals(LogKind.CALL, state.value.kind)
        vm.onChoice(ShareChoice.NOTE)
        vm.onSave()
        val saved = g.interactionRepo.attachments.value.single()
        assertEquals(AttachmentKind.PHOTO, saved.kind)
        assertEquals(ref, saved.photoRef)
        assertFalse(saved.countsAsInteraction)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty() && g.interactionRepo.interactions.value.isEmpty())
    }

    @Test
    fun aCircleGetsTheShareAndTheScreenCloses_andGiftIdeasAreOnlyForAPerson() = runViewModelTest {
        val g = graph()
        g.groupRepo.upsert(FriendGroup("crew", "Crew", memberIds = listOf(FriendId("ana"), FriendId("bea"))))
        g.shareInbox.offer(SharedContent(text = "https://shop.example/x", url = "https://shop.example/x", title = "Board game"))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(listOf("crew"), state.value.circles.map { it.id })
        vm.onSelectCircle(state.value.circles.single())
        val target = assertIs<PickedTarget.Circle>(state.value.target)
        assertEquals(listOf("ana", "bea"), target.members.map { it.id.value })
        assertFalse(state.value.canBeGift)
        vm.onChoice(ShareChoice.GIFT_IDEA)
        assertEquals(ShareChoice.NOTE, state.value.choice, "a link starts as just an idea; a circle can't take a gift idea")
        vm.onSave()
        assertTrue(state.value.closed)
        assertEquals("crew", g.interactionRepo.attachments.value.single().circleId)
    }

    @Test
    fun aShopLinkBecomesAGiftIdeaWithAPrice() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "https://shop.example/x", url = "https://shop.example/x", title = "Board game"))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSelect(state.value.friends.first { it.friend.id.value == "bea" }.friend)
        assertEquals("Board game", state.value.caption)
        vm.onChoice(ShareChoice.GIFT_IDEA)
        vm.onPriceChange("€40")
        vm.onSave()
        val gift = g.friendRepo.friends.value.first { it.id.value == "bea" }.gifts.single()
        assertEquals("€40", gift.price)
        assertEquals("https://shop.example/x", gift.url)
        assertTrue(g.interactionRepo.attachments.value.isEmpty())
    }

    @Test
    fun aPhotoAddedFromAFriendsPageSkipsTheWhoStep() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(image = SharedImage(byteArrayOf(1), "image/jpeg")), FriendId("bea"))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(FriendId("bea"), (state.value.target as PickedTarget.Person).friend.id)
        vm.onChangeFriend()
        assertNull(state.value.target)
        assertNull(g.shareInbox.preselected.value)
        assertTrue(g.shareInbox.pending.value != null, "going back keeps the photo")
    }

    @Test
    fun nobodyMatchesTheSearch_soTheTypedNameCanBeAdded() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "hi"))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onQueryChange("Zé Nobody")
        assertTrue(state.value.canAddTyped)
        vm.onAddTyped()
        assertEquals("Zé Nobody", (state.value.target as PickedTarget.Person).friend.name)
        assertEquals(4, g.friendRepo.friends.value.size)
    }

    @Test
    fun aiFillsTheCaptionOrSaysItFoundNothing() = runViewModelTest {
        val g = graph(apiKey = "k")
        g.shareInbox.offer(SharedContent(image = SharedImage(byteArrayOf(1), "image/jpeg")))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertTrue(state.value.hasAiKey)
        vm.onSelect(state.value.friends.first { it.friend.id.value == "rui" }.friend)
        assertEquals("", state.value.caption)
        vm.onSuggest()
        assertEquals("Started a new job", state.value.caption)

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
        assertTrue(g.interactionRepo.attachments.value.isEmpty())
        vm.onCancel()
        assertTrue(state.value.closed)
        assertNull(g.shareInbox.pending.value)
    }
}
