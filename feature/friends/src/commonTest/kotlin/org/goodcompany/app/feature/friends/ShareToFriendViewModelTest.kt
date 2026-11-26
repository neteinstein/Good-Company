package org.goodcompany.app.feature.friends

import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.AddressKind
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.AttachmentKind
import org.goodcompany.app.core.domain.model.Closeness
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.GeoPoint
import org.goodcompany.app.core.domain.model.InteractionSource
import org.goodcompany.app.core.domain.model.PhotoPlatform
import org.goodcompany.app.core.domain.model.PhotoRef
import org.goodcompany.app.core.domain.model.SharedContent
import org.goodcompany.app.core.domain.model.SharedImage
import org.goodcompany.app.core.domain.usecase.LogKind
import org.goodcompany.app.core.domain.usecase.PeopleFilter
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.domain.usecase.ShareChoice
import org.goodcompany.app.core.domain.usecase.SharePlace
import org.goodcompany.app.core.domain.usecase.ShareReason
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.feature.friends.ui.screens.share.PickedTarget
import org.goodcompany.app.feature.friends.ui.screens.share.ShareMessage
import org.goodcompany.app.feature.friends.ui.screens.share.ShareToFriendViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareToFriendViewModelTest {
    private fun graph(apiKey: String? = null) = TestGraph(
        listOf(
            friend("ana", "Ana Silva"),
            friend("rui", "Rui Costa") { copy(closeness = Closeness.CLOSE, phoneNumbers = listOf("+351 912 345 678")) },
            friend("bea", "Beatriz Rocha"),
        ),
        settings = AppSettings(aiApiKey = apiKey, aiEnabled = apiKey != null),
    )

    private fun vm(g: TestGraph) = ShareToFriendViewModel(
        g.shareInbox,
        g.observeShareTargets,
        g.observeSettings,
        g.saveShared,
        g.suggestUpdateFromShared,
        g.addFriend,
        g.savePlan,
        g.clock,
    )

    @Test
    fun suggestionsStayOnTop_theRestIsGroupedByCloseness_andTheFilterRowNarrowsBoth() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "Lunch with Ana"))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(listOf("ana"), state.value.suggested.map { it.friend.id.value })
        // Not repeated below: Rui (Close) then Bea (Acquaintance), with the row's counts.
        assertEquals(
            listOf(Closeness.CLOSE to listOf("rui"), Closeness.ACQUAINTANCE to listOf("bea")),
            state.value.others.byCloseness { it.closeness }.map { (c, p) -> c to p.map { it.id.value } },
        )
        assertEquals(3, state.value.others.everyoneCount)

        vm.onFilter(PeopleFilter(closeness = Closeness.CLOSE))
        assertTrue(state.value.suggested.isEmpty())
        assertEquals(listOf("rui"), state.value.others.people.map { it.id.value })
        vm.onFilter(PeopleFilter.Everyone)
        assertEquals(3, state.value.friends.size)
    }

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
    fun aMapsLinkShowsItsPlace_offersTheLocationChoices_andSavesAHomeAddress() = runViewModelTest {
        // Issue #170.
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "Cervejaria Ramiro\nAv. Almirante Reis 1, Lisboa\nhttps://maps.app.goo.gl/abc"))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSelect(state.value.friends.first { it.friend.id.value == "ana" }.friend)
        val place = assertNotNull(state.value.place)
        assertEquals("Cervejaria Ramiro", place.name)
        assertEquals("Av. Almirante Reis 1, Lisboa", place.address)
        assertEquals(ShareChoice.NOTE, state.value.choice)
        assertTrue(state.value.offers(ShareChoice.PLAN_HERE) && state.value.offers(ShareChoice.MET_HERE))
        assertTrue(state.value.canBeAddress)
        assertFalse(state.value.canBeNamedPlace, "a short link has no coordinates, and is never followed")
        assertFalse(state.value.offers(ShareChoice.GIFT_IDEA))
        vm.onChoice(ShareChoice.NAMED_PLACE)
        assertEquals(ShareChoice.NOTE, state.value.choice)

        vm.onChoice(ShareChoice.HOME_ADDRESS)
        vm.onPlaceAddress("")
        vm.onPlaceName("")
        assertFalse(state.value.canSave, "nothing to keep as the address")
        vm.onPlaceAddress(" Av. Almirante Reis 1, 1150-007 Lisboa ")
        vm.onSave()
        assertEquals(FriendId("ana"), state.value.savedTo)
        assertEquals("Av. Almirante Reis 1, 1150-007 Lisboa", g.friendRepo.get(FriendId("ana"))?.address(AddressKind.HOME)?.text)
        assertTrue(g.interactionRepo.attachments.value.isEmpty())
    }

    @Test
    fun aBareShortLink_isStillALocation_withEmptyFields() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "https://maps.app.goo.gl/abc", url = "https://maps.app.goo.gl/abc"))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSelect(state.value.friends.first().friend)
        assertEquals(SharePlace(link = "https://maps.app.goo.gl/abc"), state.value.place)
        assertTrue(state.value.offers(ShareChoice.MET_HERE))
        assertFalse(state.value.canBeNamedPlace)
    }

    @Test
    fun planHere_opensThePlanEditor_thenSavesThePlanWithTheLinkAndPoint_forACircleToo() = runViewModelTest {
        val g = graph()
        g.groupRepo.upsert(FriendGroup("crew", "Crew", memberIds = listOf(FriendId("ana"), FriendId("bea"))))
        val link = "https://www.google.com/maps/place/Ramiro/@38.72,-9.13,17z"
        g.shareInbox.offer(SharedContent(url = link))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSelectCircle(state.value.circles.single())
        assertFalse(state.value.canBeAddress, "home and work are for a person")
        assertTrue(state.value.canBeNamedPlace)
        vm.onChoice(ShareChoice.PLAN_HERE)
        vm.onSave()
        assertTrue(state.value.planEditorOpen)
        assertTrue(g.planRepo.plans.value.isEmpty(), "nothing saved until the editor saves")
        vm.onDismissPlanEditor()
        assertFalse(state.value.planEditorOpen)

        val place = assertNotNull(state.value.place)
        vm.onSave()
        vm.onSavePlan(
            PlanInput(
                "Lunch",
                emptyList(),
                LocalDate(2026, 3, 13),
                place = place.label,
                circleId = "crew",
                mapLink = place.link,
                location = place.point,
            ),
        )
        val plan = g.planRepo.plans.value.single()
        assertEquals("Ramiro", plan.place)
        assertEquals(link, plan.mapLink)
        assertEquals(GeoPoint(38.72, -9.13), plan.location)
        assertTrue(state.value.closed)
        assertNull(g.shareInbox.pending.value)
    }

    @Test
    fun weMetHere_logsAMeetupThere_onTheChosenDay() = runViewModelTest {
        val g = graph()
        g.shareInbox.offer(SharedContent(text = "geo:38.72,-9.13?q=38.72,-9.13(Casa%20da%20Ana)"))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSelect(state.value.friends.first { it.friend.id.value == "ana" }.friend)
        vm.onChoice(ShareChoice.MET_HERE)
        vm.onDate(LocalDate(2026, 3, 8))
        vm.onSave()
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals("Casa da Ana", hangout.place?.name)
        assertEquals(38.72, hangout.place?.latitude)
        assertEquals(hangout.id, g.interactionRepo.attachments.value.single().logId)
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
