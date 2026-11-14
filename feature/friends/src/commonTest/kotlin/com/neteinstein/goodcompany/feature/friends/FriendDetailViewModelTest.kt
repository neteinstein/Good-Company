package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.model.TasteKind
import com.neteinstein.goodcompany.core.domain.usecase.FamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.MissingApiKeyException
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.AboutEdit
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailMessage
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.LinkEdit
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.PlanEditing
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.RelativeEdit
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.SavedEdit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriendDetailViewModelTest {
    private fun graph(apiKey: String? = null, withKid: Boolean = false) = TestGraph(
        listOf(friend("ana") { copy(likes = listOf("jazz"), kids = if (withKid) listOf(Kid("k1", "Leo")) else emptyList()) }),
        settings = AppSettings(aiApiKey = apiKey, aiEnabled = apiKey != null),
    )

    private var g = graph()
    private val reminders get() = g.reminderRepo

    private fun vm(id: String = "ana") = FriendDetailViewModel(
        FriendId(id),
        g.observeFriendDetail,
        g.observeSettings,
        g.updateFriend,
        g.deleteFriend,
        g.setCloseness,
        g.snooze,
        g.logCall,
        g.generateTalkingPoints,
        g.dismissIdea,
        g.draftBirthdayMessage,
        g.suggestGifts,
        g.addReminder,
        g.setCatchUpPaused,
        g.savePlan,
        g.confirmPlan,
        g.cancelPlan,
        g.restorePlan,
        g.addFeed,
        g.setFriendPhoto,
        g.removeFriendPhoto,
        g.renameFriend,
        g.setHomeCircleFilter,
        g.setCircleMember,
        g.restoreGroup,
        g.observeAttachments,
        g.observeTags,
        g.deleteAttachment,
        g.restoreAttachment,
        g.shareInbox,
        g.personLinks,
        g.clock,
        g.ids,
    )

    @Test
    fun circleSheet_listsStoredCircles_andATickSavesWithUndo() = runViewModelTest {
        g = TestGraph(listOf(friend("ana") { copy(relationToMe = LinkType.SIBLING) }, friend("rui")))
        g.groupRepo.upsert(FriendGroup("surf", "Surf", memberIds = listOf(FriendId("rui"))))
        val vm = vm()
        val state = collecting(vm.state)
        // Ana is family: the built-in circle shows as a chip but is never offered in the sheet.
        assertEquals(listOf(FamilyCircle.ID), state.value.detail!!.groups.map { it.id })
        assertEquals(listOf("surf"), state.value.detail!!.allCircles.map { it.id })

        vm.onSetInCircle("surf", member = true)
        assertEquals(listOf("rui", "ana"), g.groupRepo.groups.value.single().memberIds.map { it.value })
        assertEquals(listOf(FamilyCircle.ID, "surf"), state.value.detail!!.groups.map { it.id })
        assertEquals(FriendDetailMessage.Saved, state.value.message)
        assertIs<SavedEdit.CircleBefore>(state.value.undoSave)

        vm.onUndoSave()
        assertEquals(listOf("rui"), g.groupRepo.groups.value.single().memberIds.map { it.value })
        assertNull(state.value.undoSave)
    }

    @Test
    fun circleChipX_removesWithUndo() = runViewModelTest {
        val surf = FriendGroup("surf", "Surf", emoji = "🏄", memberIds = listOf(FriendId("ana"), FriendId("x")), meetEveryDays = 14)
        g.groupRepo.upsert(surf)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onRemoveFromCircle("surf")
        assertEquals(listOf(FriendId("x")), g.groupRepo.groups.value.single().memberIds)
        assertEquals(FriendDetailMessage.RemovedFromCircle(surf), state.value.message)
        assertTrue(state.value.detail!!.groups.isEmpty())

        vm.onUndoSave()
        assertEquals(surf, g.groupRepo.groups.value.single()) // back as it was, order and reminder included
    }

    @Test
    fun circleTick_onTheFamilyCircleOrNoChange_saysNothing() = runViewModelTest {
        g.groupRepo.upsert(FriendGroup("surf", "Surf", memberIds = listOf(FriendId("ana"))))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSetInCircle("surf", member = true) // already in
        vm.onSetInCircle(FamilyCircle.ID, member = true)
        assertNull(state.value.message)
        assertNull(state.value.undoSave)
    }

    @Test
    fun aiOff_hidesTheAiSection_evenWithAKey() = runViewModelTest {
        g = TestGraph(listOf(friend("ana")), settings = AppSettings(aiApiKey = "sk-ant-key"))
        val state = collecting(vm().state)
        assertFalse(state.value.aiEnabled)
        assertFalse(state.value.hasAiKey) // no gift or message suggestions either
        g.settingsRepo.update { it.copy(aiEnabled = true) }
        assertTrue(state.value.aiEnabled)
        assertTrue(state.value.hasAiKey)
    }

    @Test
    fun editsFlowBackIntoState() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onClosenessChange(Closeness.CLOSE)
        vm.onMeetEveryChange(14)
        vm.onAddLikes(listOf("jazz", "sushi"))
        vm.onAddLikes(listOf("jazz"))
        vm.onRemoveLike("sushi")
        vm.onAddDislikes(listOf("crowds"))
        vm.onSaveBirthday(Birthday(5, 4, 1990), notify = true)
        val f = state.value.detail!!.friend
        assertTrue(f.isCloseFriend && f.notifyBirthday)
        assertEquals(Closeness.CLOSE, f.closeness)
        assertEquals(14, f.meetEveryDays)
        assertEquals(listOf("jazz"), f.likes)
        assertEquals(listOf("crowds"), f.dislikes)
        assertEquals(Birthday(5, 4, 1990), f.birthday)
    }

    @Test
    fun aPickedPicture_becomesTheirPhoto_andAnUnreadableOneSaysSo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onPhotoPicked(null)
        assertNull(state.value.message)
        assertNull(state.value.detail!!.friend.avatar)

        vm.onPhotoPicked(ByteArray(ContactPhoto.MAX_BYTES * 2)) // the test graph can't shrink this
        assertEquals(FriendDetailMessage.PhotoUnreadable, state.value.message)
        assertNull(state.value.detail!!.friend.avatar)

        vm.onMessageShown()
        vm.onPhotoPicked(ByteArray(0)) // what the pickers hand back for a picture they couldn't read
        assertEquals(FriendDetailMessage.PhotoUnreadable, state.value.message)

        vm.onMessageShown()
        vm.onPhotoPicked(byteArrayOf(1, 2, 3))
        assertEquals(ContactPhoto.of(byteArrayOf(1, 2, 3)), state.value.detail!!.friend.customPhoto)
        assertNull(state.value.message)
    }

    @Test
    fun aPhotoSetInTheApp_winsOverTheContactsOne_andRemovingItShowsTheContactsAgain() = runViewModelTest {
        val contactPhoto = ContactPhoto.of(byteArrayOf(9, 9))!!
        g.friendRepo.upsert(g.friendRepo.get(FriendId("ana"))!!.copy(contactId = "c1", photo = contactPhoto))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onPhotoPicked(byteArrayOf(1, 2, 3))
        val f = state.value.detail!!.friend
        assertEquals(ContactPhoto.of(byteArrayOf(1, 2, 3)), f.avatar)
        assertEquals(contactPhoto, f.photo)

        vm.onRemovePhoto()
        assertNull(state.value.detail!!.friend.customPhoto)
        assertEquals(contactPhoto, state.value.detail!!.friend.avatar)
    }

    @Test
    fun aNameGivenInTheApp_isShown_andTheContactsNameCanComeBack() = runViewModelTest {
        g.friendRepo.upsert(g.friendRepo.get(FriendId("ana"))!!.copy(contactId = "c1"))
        val contactName = g.friendRepo.get(FriendId("ana"))!!.name
        val vm = vm()
        val state = collecting(vm.state)
        vm.onRename("  Aninhas ")
        assertEquals("Aninhas", state.value.detail!!.friend.customName)
        assertEquals("Aninhas", state.value.detail!!.friend.displayName)
        assertEquals(contactName, state.value.detail!!.friend.name)

        vm.onRename(null)
        assertNull(state.value.detail!!.friend.customName)
        assertEquals(contactName, state.value.detail!!.friend.displayName)
    }

    @Test
    fun aBirthdayWithoutAYear_isKept_andTheReminderSwitchIsSavedWithIt() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveBirthday(Birthday(3, 14), notify = false)
        val f = state.value.detail!!.friend
        assertEquals(Birthday(3, 14, null), f.birthday)
        assertFalse(f.notifyBirthday)
        vm.onSaveBirthday(null, notify = true)
        assertNull(state.value.detail!!.friend.birthday)
    }

    @Test
    fun lifeUpdatesAndNewThingsAreTimestampedForTheStory() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        val now = g.clock.now()
        assertEquals(0, state.value.freshCount)

        vm.onAddLifeUpdate("  New job at a studio ")
        vm.onAddLifeUpdate(" ")
        vm.onAddLikes(listOf("jazz", "climbing"))
        vm.onAddKid(KidDraft("Mia"))
        vm.onAddEvent("Moving day", LocalDate(2026, 4, 1), 0)
        val f = state.value.detail!!.friend
        assertEquals(listOf("New job at a studio"), f.lifeUpdates.map { it.text })
        assertEquals(now, f.lifeUpdates.single().addedAt)
        // "jazz" was already there, so only the new like gets a date.
        assertEquals(mapOf("climbing" to now), f.likesAddedAt)
        assertEquals(now, state.value.detail!!.kids.single().link.addedAt)
        assertEquals(now, f.importantEvents.single().addedAt)
        assertEquals(4, state.value.freshCount)

        vm.onRemoveLike("climbing")
        vm.onRemoveLifeUpdate(f.lifeUpdates.single().id)
        val after = state.value.detail!!.friend
        assertTrue(after.lifeUpdates.isEmpty())
        assertTrue(after.likesAddedAt.isEmpty())
    }

    @Test
    fun followsABlogAndTurnsPostsIntoNews() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        g.feedFetcher.pages["https://ana.blog"] =
            "<rss><channel><title>Ana writes</title><item><title>Moved to Porto</title><guid>1</guid></item></channel></rss>"
        vm.onAddFeed("ana.blog")
        assertEquals(FriendDetailMessage.FeedAdded("Ana writes"), state.value.message)
        assertEquals(1, state.value.feedAdded)
        assertEquals(listOf("Moved to Porto"), state.value.detail!!.friend.feeds.single().items.map { it.title })

        vm.onFeedPostToNews("Moved to Porto")
        assertEquals(FriendDetailMessage.AddedToNews, state.value.message)
        assertEquals(listOf("Moved to Porto"), state.value.detail!!.friend.lifeUpdates.map { it.text })

        vm.onAddFeed("nothing.here")
        assertEquals(FriendDetailMessage.FeedNotFound, state.value.message)
        vm.onRemoveFeed("https://ana.blog")
        assertTrue(state.value.detail!!.friend.feeds.isEmpty())
    }

    @Test
    fun kidsArePeople_andEventsCanBeAdded() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddKid(KidDraft("  "))
        assertTrue(state.value.detail!!.kids.isEmpty())
        vm.onAddKid(KidDraft("Leo Costa", Birthday(4, 1, 2020), listOf("lego")))
        assertEquals(FriendDetailMessage.KidAdded("Leo"), state.value.message)
        val kid = state.value.detail!!.kids.single().person
        assertEquals(listOf("lego"), kid.likes)
        assertFalse(kid.showInMyList) // Ana is a friend, not family

        // On the kid's own page, "Show in my list" can be switched on.
        val kidVm = vm(kid.id.value)
        val kidState = collecting(kidVm.state)
        assertEquals(listOf("Ana"), kidState.value.detail!!.parents.map { it.name })
        kidVm.onSetShowInMyList(true)
        assertTrue(kidState.value.detail!!.friend.showInMyList)

        vm.onAddEvent("Surgery", LocalDate(2026, 3, 20), 1)
        vm.onAddEvent("  ", LocalDate(2026, 3, 21), 1)
        assertEquals(listOf("Surgery"), state.value.detail!!.friend.importantEvents.map { it.title })
    }

    @Test
    fun aiWithoutKeyReportsAMessage() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onGenerateIdeas()
        assertIs<MissingApiKeyException>((state.value.message as FriendDetailMessage.AiError).error)
    }

    @Test
    fun deleteAndMissingFriend() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDelete()
        assertTrue(state.value.deleted)
        assertTrue(state.value.notFound)
    }

    @Test
    fun aPlansNoClosesItWithoutLoggingAMeetup() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onOpenPlanEditor(null)
        vm.onSavePlan(PlanInput("Dinner", emptyList(), g.clock.today()))
        val plan = g.planRepo.plans.value.single()
        // A yes opens the "We met" panel (issue #47); the friend page only handles the no.
        vm.onConfirmPlan(plan.id, happened = false)
        assertEquals(FriendDetailMessage.PlanClosed, state.value.message)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
    }

    @Test
    fun reminderFromFriendPageDefaultsToThatFriend() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onOpenReminderEditor()
        assertTrue(state.value.showReminderEditor)
        vm.onSaveReminder("Buy present", emptyList(), LocalDate(2026, 3, 12), 18)
        assertEquals(listOf(FriendId("ana")), reminders.reminders.value.single().friendIds)
        assertTrue(!state.value.showReminderEditor)
    }

    @Test
    fun circleSnoozeAndCallShapeTheCatchUpRhythm() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onClosenessChange(Closeness.INNER)
        assertEquals(7, state.value.detail!!.cadenceDays)
        assertTrue(state.value.detail!!.friend.isCloseFriend)
        vm.onSnooze(SnoozeOption.ONE_WEEK)
        assertEquals(LocalDate(2026, 3, 17), state.value.detail!!.friend.snoozedUntil)
        assertIs<FriendDetailMessage.Snoozed>(state.value.message)
        vm.onClearSnooze()
        assertNull(state.value.detail!!.friend.snoozedUntil)
        vm.onCalled()
        assertEquals(FriendDetailMessage.CallLogged, state.value.message)
        assertTrue(state.value.detail!!.lastContactByType.isNotEmpty())
    }

    @Test
    fun giftsCanBeNotedGivenAndRemoved() = runViewModelTest {
        g = graph(withKid = true)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveGift("  ", "")
        vm.onSaveGift("Vinyl", "Blue Note")
        vm.onSaveGift("Dino lego", "")
        val gifts = state.value.detail!!.friend.gifts
        assertEquals(listOf("Vinyl", "Dino lego"), gifts.map { it.title })
        vm.onMarkGiftGiven(gifts.first().id, GiftOccasion.BIRTHDAY)
        val given = state.value.detail!!.friend.gifts.first()
        assertEquals(g.clock.today(), given.givenOn)
        assertEquals(GiftOccasion.BIRTHDAY, given.occasion)
        vm.onRemoveGift(gifts.last().id)
        assertEquals(1, state.value.detail!!.friend.gifts.size)
    }

    @Test
    fun aiGiftSuggestionsCanBeKept() = runViewModelTest {
        g = graph(apiKey = "sk-test", withKid = true)
        // Leo is a person now: ideas for him are asked with his parent's profile and kept on his own page.
        val vm = vm("k1")
        val state = collecting(vm.state)
        vm.onSuggestGifts()
        assertEquals(listOf("Vinyl", "Concert tickets"), state.value.giftSuggestions!!.ideas)
        assertEquals("gifts:ana:Leo:en", g.assistant.calls.single())
        vm.onKeepGiftSuggestion("Vinyl")
        assertEquals(listOf("Vinyl"), state.value.detail!!.friend.gifts.map { it.title })
        vm.onKeepGiftSuggestion("Concert tickets")
        assertNull(state.value.giftSuggestions)

        g.assistant.gifts = Result.success(listOf("Vinyl"))
        vm.onSuggestGifts()
        assertEquals(FriendDetailMessage.NoNewGifts, state.value.message)
    }

    @Test
    fun birthdayWishCanBePersonalisedWithAi() = runViewModelTest {
        g = graph(apiKey = "sk-test")
        val vm = vm()
        val state = collecting(vm.state)
        vm.onOpenWish()
        assertTrue(state.value.showWish)
        assertNull(state.value.wishDraft)
        vm.onPersonaliseWish()
        assertEquals("Happy birthday!", state.value.wishDraft)
        vm.onDismissWish()
        assertTrue(!state.value.showWish)
    }

    @Test
    fun birthdayWishWithoutKeyExplains() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onPersonaliseWish()
        assertIs<FriendDetailMessage.AiError>(state.value.message)
        vm.onSuggestGifts()
        assertIs<FriendDetailMessage.AiError>(state.value.message)
    }

    @Test
    fun pauseChatsAndOccasions() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onTogglePause()
        assertTrue(state.value.detail!!.friend.catchUpPaused)
        vm.onTogglePause()
        assertFalse(state.value.detail!!.friend.catchUpPaused)
        vm.onToggleContactCounts()
        assertTrue(state.value.detail!!.friend.contactCountsAsCatchUp)
        vm.onToggleOccasion(Occasion.OUTDOORS)
        vm.onToggleOccasion(Occasion.COFFEE)
        assertEquals(listOf(Occasion.COFFEE, Occasion.OUTDOORS), state.value.detail!!.friend.occasions)
        vm.onToggleOccasion(Occasion.COFFEE)
        assertEquals(listOf(Occasion.OUTDOORS), state.value.detail!!.friend.occasions)
        assertTrue(state.value.showWarmth)
        g.settingsRepo.update { it.copy(showWarmth = false) }
        assertFalse(state.value.showWarmth)
    }

    @Test
    fun plansCanBeMadeChangedConfirmedAndCancelledWithUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onOpenPlanEditor(null)
        assertEquals(PlanEditing(null), state.value.planEditing)
        vm.onSavePlan(PlanInput("Lunch", emptyList(), LocalDate(2026, 3, 12), "Tasca"))
        assertNull(state.value.planEditing)
        assertEquals(FriendDetailMessage.PlanSaved, state.value.message)
        val plan = state.value.detail!!.plans.single()
        assertEquals(listOf(FriendId("ana")), plan.friendIds)

        vm.onOpenPlanEditor(plan)
        vm.onSavePlan(PlanInput("Long lunch", listOf(FriendId("ana")), LocalDate(2026, 3, 13)))
        assertEquals("Long lunch", state.value.detail!!.plans.single().title)
        assertEquals(plan.id, state.value.detail!!.plans.single().id)

        val current = state.value.detail!!.plans.single()
        vm.onCancelPlan(current)
        assertEquals(FriendDetailMessage.PlanCancelled, state.value.message)
        assertTrue(state.value.detail!!.plans.isEmpty())
        vm.onUndoCancelPlan()
        assertEquals(current, state.value.detail!!.plans.single())

        vm.onConfirmPlan(current.id, happened = true)
        assertEquals(FriendDetailMessage.PlanLogged, state.value.message)
        assertTrue(state.value.detail!!.plans.isEmpty())
        vm.onMessageShown()
        assertNull(state.value.undoPlan)
    }

    @Test
    fun aboutSavesTrimmed_keepsContactEmails_andReplacesTypedOnes() = runViewModelTest {
        g.friendRepo.upsert(
            g.friendRepo.get(FriendId("ana"))!!.copy(emails = listOf(FriendEmail("c@x.example", fromContact = true), FriendEmail("old@x.example"))),
        )
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveAbout(
            AboutEdit(
                job = " Designer ",
                livesIn = "Porto",
                met = MetInfo(" ", null),
                introducedBy = Introducer(FriendId("rui"), " Rui "),
                preferredChannel = ContactChannel.EMAIL,
                emails = listOf(" new@x.example ", "NEW@x.example", "C@x.example", ""),
            ),
        )
        val f = state.value.detail!!.friend
        assertEquals("Designer", f.job)
        assertEquals("Porto", f.livesIn)
        assertNull(f.met)
        assertEquals(Introducer(FriendId("rui"), "Rui"), f.introducedBy)
        assertEquals(ContactChannel.EMAIL, f.preferredChannel)
        assertEquals(listOf(FriendEmail("c@x.example", true), FriendEmail("new@x.example")), f.emails)
        vm.onSaveAbout(AboutEdit(introducedBy = Introducer(null, " ")))
        assertNull(state.value.detail!!.friend.introducedBy)
    }

    @Test
    fun relativesCanBeAddedEditedAndRemoved_keepingWhenTheyWereAdded() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveRelative(RelativeEdit(relation = Relation.PARTNER, name = " "))
        assertTrue(state.value.detail!!.friend.relatives.isEmpty())
        vm.onSaveRelative(
            RelativeEdit(
                relation = Relation.PARTNER,
                name = " Tiago ",
                birthday = Birthday(3, 20, 1988),
                likes = listOf("padel"),
                notifyBirthday = true,
            ),
        )
        val added = state.value.detail!!.friend.relatives.single()
        assertEquals("Tiago", added.name)
        assertEquals(Birthday(3, 20, 1988), added.birthday)
        assertEquals(g.clock.now(), added.addedAt)
        assertTrue(added.notifyBirthday)
        vm.onSaveRelative(RelativeEdit(id = added.id, relation = Relation.PARTNER, name = "Tiago", note = " climbs ", notifyBirthday = false))
        val edited = state.value.detail!!.friend.relatives.single()
        assertEquals("climbs", edited.note)
        assertEquals(added.addedAt, edited.addedAt)
        assertFalse(edited.notifyBirthday)
        vm.onRemoveRelative(added.id)
        assertTrue(state.value.detail!!.friend.relatives.isEmpty())
    }

    @Test
    fun tastesByKind_recordWhenTheyWereAdded() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddTastes(TasteKind.FOOD, listOf("ramen"))
        vm.onAddTastes(TasteKind.DRINK, listOf("tea"))
        vm.onAddTastes(TasteKind.INTEREST, listOf("chess", "chess"))
        var f = state.value.detail!!.friend
        assertEquals(listOf("ramen"), f.food)
        assertEquals(listOf("tea"), f.drinks)
        assertEquals(listOf("chess"), f.interests)
        assertEquals(setOf("chess"), f.interestsAddedAt.keys)
        assertEquals(setOf("ramen"), f.foodAddedAt.keys)
        vm.onRemoveTaste(TasteKind.FOOD, "ramen")
        vm.onRemoveTaste(TasteKind.DRINK, "tea")
        vm.onRemoveTaste(TasteKind.INTEREST, "chess")
        f = state.value.detail!!.friend
        assertTrue(f.food.isEmpty() && f.drinks.isEmpty() && f.interests.isEmpty() && f.interestsAddedAt.isEmpty())
    }

    @Test
    fun customFieldsCanBeAddedEdited_andRemovedWithUndoInPlace() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveCustomField(null, " Shoe size ", " 38 ")
        vm.onSaveCustomField(null, "Club", "Benfica")
        vm.onSaveCustomField(null, " ", "x")
        val first = state.value.detail!!.friend.customFields.first()
        assertEquals("Shoe size" to "38", first.label to first.value)
        vm.onSaveCustomField(first.id, "Shoe size", "39")
        assertEquals(listOf("39", "Benfica"), state.value.detail!!.friend.customFields.map { it.value })
        vm.onRemoveCustomField(first.id)
        assertEquals(listOf("Club"), state.value.detail!!.friend.customFields.map { it.label })
        assertEquals(FriendDetailMessage.CustomFieldRemoved("Shoe size"), state.value.message)
        vm.onUndoRemoveCustomField()
        assertEquals(listOf("Shoe size", "Club"), state.value.detail!!.friend.customFields.map { it.label })
        assertNull(state.value.undoCustomField)
        vm.onRemoveCustomField(first.id)
        vm.onMessageShown()
        assertNull(state.value.undoCustomField)
        vm.onUndoRemoveCustomField()
        assertEquals(listOf("Club"), state.value.detail!!.friend.customFields.map { it.label })
    }

    @Test
    fun savedPhotosGoThroughTheShareStep_andRemovedOnesComeBackWithUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddSavedPhoto(null, null, null)
        assertNull(g.shareInbox.pending.value)
        vm.onAddSavedPhoto(ByteArray(0), null, null)
        assertEquals(FriendDetailMessage.PhotoUnreadable, state.value.message)
        vm.onMessageShown()
        val ref = PhotoRef(PhotoPlatform.IOS, "asset-1")
        vm.onAddSavedPhoto(byteArrayOf(1, 2), ref, LocalDate(2026, 3, 1))
        assertEquals(ref, g.shareInbox.pending.value?.photoRef)
        assertEquals(FriendId("ana"), g.shareInbox.preselected.value)

        val saved = Attachment(
            id = "a1",
            friendId = FriendId("ana"),
            circleId = null,
            kind = AttachmentKind.TEXT,
            text = "see you soon",
            url = null,
            title = null,
            photoRef = null,
            thumbnail = null,
            at = g.clock.now(),
            interactionType = null,
            logId = null,
        )
        g.interactionRepo.upsertAttachment(saved)
        assertEquals(listOf("a1"), state.value.attachments.map { it.id })
        vm.onRemoveAttachment(saved)
        assertTrue(state.value.attachments.isEmpty())
        assertEquals(FriendDetailMessage.AttachmentRemoved, state.value.message)
        vm.onUndoRemoveAttachment()
        assertEquals(listOf("a1"), state.value.attachments.map { it.id })
        assertNull(state.value.undoAttachment)
    }

    @Test
    fun aLinkShowsOnBothPages_withTheRightWord_andCanBeRemovedAndUndone() = runViewModelTest {
        g = TestGraph(listOf(friend("rui") { copy(pronouns = Pronouns.HE) }, friend("ana")))
        val rui = vm("rui")
        val ana = vm("ana")
        val ruiState = collecting(rui.state)
        val anaState = collecting(ana.state)

        rui.onSaveLink(LinkEdit(FriendId("ana"), LinkType.DATING, startedOn = LocalDate(2019, 6, 1), otherPronouns = Pronouns.SHE))

        val onRui = ruiState.value.links.single()
        assertEquals(FriendId("ana"), onRui.person.id)
        assertEquals(LinkType.DATING, onRui.role)
        assertEquals(Pronouns.SHE, onRui.person.pronouns)
        val onAna = anaState.value.links.single()
        assertEquals(FriendId("rui"), onAna.person.id)
        assertEquals(LinkType.DATING, onAna.role)

        ana.onRemoveLink(onAna.link.id)
        assertTrue(ruiState.value.links.isEmpty())
        assertEquals(FriendDetailMessage.LinkRemoved, anaState.value.message)
        ana.onUndoRemoveLink()
        assertEquals(1, ruiState.value.links.size)
        assertNull(anaState.value.undoLink)
    }

    @Test
    fun relationToMe_andAddingARelativeAsAPerson() = runViewModelTest {
        g = TestGraph(listOf(friend("rui") { copy(relatives = listOf(Relative("r1", Relation.PARENT, "Graça Costa"))) }))
        val vm = vm("rui")
        val state = collecting(vm.state)

        vm.onSaveRelationToMe(LinkType.SIBLING, Pronouns.HE)
        assertEquals(LinkType.SIBLING, state.value.detail!!.friend.relationToMe)
        assertEquals(Pronouns.HE, state.value.detail!!.friend.pronouns)

        vm.onAddRelativeAsPerson("r1")
        assertTrue(state.value.detail!!.friend.relatives.isEmpty())
        val mother = state.value.links.single()
        assertEquals("Graça Costa", mother.person.name)
        assertEquals(LinkType.PARENT, mother.role)
        assertEquals(FriendDetailMessage.AddedAsPerson("Graça Costa"), state.value.message)
    }

    @Test
    fun theSharedTagListComesFromEveryone() = runViewModelTest {
        g.friendRepo.upsert(friend("pedro", "Pedro").copy(likes = listOf("Sushi"), dislikes = listOf("coriander")))
        val vm = vm()
        val state = collecting(vm.state)
        val sushi = state.value.tags.first { it.name == "Sushi" }
        assertEquals(listOf("Pedro"), sushi.likedBy)
        assertTrue(state.value.tags.any { it.name == "coriander" && it.dislikedBy == listOf("Pedro") })
    }

    @Test
    fun aKinshipProposal_isConfirmedInOneTap_orDismissedForGood() = runViewModelTest {
        g = TestGraph(listOf(friend("rui") { copy(relationToMe = LinkType.SIBLING) }, friend("leonor"), friend("mia")))
        g.linkRepo.upsert(PersonLink("1", FriendId("rui"), FriendId("leonor"), LinkType.CHILD))
        g.linkRepo.upsert(PersonLink("2", FriendId("rui"), FriendId("mia"), LinkType.CHILD))
        val leonor = vm("leonor")
        val state = collecting(leonor.state)
        assertEquals(LinkType.NIECE_NEPHEW, state.value.detail!!.kinship!!.proposed)
        leonor.onConfirmKinship(LinkType.NIECE_NEPHEW)
        assertEquals(LinkType.NIECE_NEPHEW, g.friendRepo.get(FriendId("leonor"))!!.relationToMe)
        assertNull(state.value.detail!!.kinship)

        val mia = vm("mia")
        val miaState = collecting(mia.state)
        assertEquals(LinkType.NIECE_NEPHEW, miaState.value.detail!!.kinship!!.proposed)
        mia.onDismissKinship()
        assertNull(miaState.value.detail!!.kinship)
        assertNull(g.friendRepo.get(FriendId("mia"))!!.relationToMe)
        assertEquals(setOf(LinkType.NIECE_NEPHEW), g.friendRepo.get(FriendId("mia"))!!.dismissedKinship)
    }

    // --- Save on choice / on close, with Undo (#64) -----------------------------------------------------------------

    @Test
    fun closingTheFamilySheet_withAValidRelative_savesIt_andUndoTakesItBack() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        // What the sheet hands over when it is swiped away with a name typed.
        vm.onSaveRelative(RelativeEdit(relation = Relation.SIBLING, name = " Inês ", birthday = Birthday(2, 9)))
        val added = state.value.detail!!.friend.relatives.single()
        assertEquals("Inês", added.name)
        assertEquals(Birthday(2, 9, null), added.birthday) // a birthday without a year (#36) still saves
        assertEquals(FriendDetailMessage.Saved, state.value.message)
        assertEquals(SavedEdit.RelativeBefore(added.id, null), state.value.undoSave)

        vm.onUndoSave()
        vm.onMessageShown()
        assertTrue(state.value.detail!!.friend.relatives.isEmpty())
        assertNull(state.value.undoSave)
    }

    @Test
    fun closingTheFamilySheet_withABlankName_savesNothing_andSaysNothing() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveRelative(RelativeEdit(relation = Relation.PARTNER, name = "  ", birthday = Birthday(1, 1)))
        assertTrue(state.value.detail!!.friend.relatives.isEmpty())
        assertNull(state.value.message)
        assertNull(state.value.undoSave)
    }

    @Test
    fun undoingAnEditedRelative_putsTheOldOneBack() = runViewModelTest {
        g = TestGraph(listOf(friend("ana") { copy(relatives = listOf(Relative("r1", Relation.PARTNER, "Tiago", note = "climbs"))) }))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveRelative(RelativeEdit(id = "r1", relation = Relation.PARTNER, name = "Tiago", note = "runs"))
        assertEquals("runs", state.value.detail!!.friend.relatives.single().note)
        vm.onUndoSave()
        assertEquals("climbs", state.value.detail!!.friend.relatives.single().note)
    }

    @Test
    fun aSavedBirthday_canBeUndone_andAnUnchangedOneSaysNothing() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveBirthday(Birthday(3, 14), notify = false)
        assertEquals(Birthday(3, 14, null), state.value.detail!!.friend.birthday)
        assertEquals(FriendDetailMessage.Saved, state.value.message)
        vm.onUndoSave()
        vm.onMessageShown()
        assertNull(state.value.detail!!.friend.birthday)
        assertTrue(state.value.detail!!.friend.notifyBirthday)

        vm.onSaveBirthday(null, notify = true)
        assertNull(state.value.message)
    }

    @Test
    fun aGiftOccasionMarksItGiven_andUndoMakesItAnIdeaAgain() = runViewModelTest {
        g = TestGraph(listOf(friend("ana") { copy(gifts = listOf(Gift("g1", "Book"))) }))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onMarkGiftGiven("g1", GiftOccasion.CHRISTMAS)
        assertEquals(GiftOccasion.CHRISTMAS, state.value.detail!!.friend.gifts.single().occasion)
        assertEquals(FriendDetailMessage.Saved, state.value.message)
        vm.onUndoSave()
        assertEquals(Gift("g1", "Book"), state.value.detail!!.friend.gifts.single())
    }

    @Test
    fun aKidAddedByMistake_isGoneAfterUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddKid(KidDraft("Mia"))
        val kid = state.value.detail!!.kids.single().person
        assertEquals(FriendDetailMessage.KidAdded("Mia"), state.value.message)
        assertEquals(SavedEdit.KidAdded(kid.id), state.value.undoSave)
        vm.onUndoSave()
        assertTrue(state.value.detail!!.kids.isEmpty())
        assertNull(g.friendRepo.get(kid.id))
    }

    @Test
    fun aChosenRelationToMe_andALinkSavedOnClose_canBeUndone() = runViewModelTest {
        g = TestGraph(listOf(friend("rui"), friend("ana") { copy(pronouns = Pronouns.SHE) }))
        val vm = vm("rui")
        val state = collecting(vm.state)

        vm.onSaveRelationToMe(LinkType.SIBLING, Pronouns.HE)
        assertEquals(SavedEdit.RelationToMeBefore(null, null), state.value.undoSave)
        vm.onUndoSave()
        vm.onMessageShown()
        assertNull(state.value.detail!!.friend.relationToMe)
        assertNull(state.value.detail!!.friend.pronouns)

        vm.onSaveLink(LinkEdit(FriendId("ana"), LinkType.FRIEND, otherPronouns = Pronouns.THEY))
        assertEquals(1, state.value.links.size)
        assertEquals(FriendDetailMessage.Saved, state.value.message)
        vm.onUndoSave()
        vm.onMessageShown()
        assertTrue(state.value.links.isEmpty())
        assertEquals(Pronouns.SHE, g.friendRepo.get(FriendId("ana"))!!.pronouns)

        // Editing a link and undoing it brings back the old one.
        vm.onSaveLink(LinkEdit(FriendId("ana"), LinkType.FRIEND, otherPronouns = Pronouns.SHE))
        vm.onMessageShown()
        val link = state.value.links.single().link
        vm.onSaveLink(LinkEdit(FriendId("ana"), LinkType.COLLEAGUE, otherPronouns = Pronouns.SHE, id = link.id))
        assertEquals(LinkType.COLLEAGUE, state.value.links.single().role)
        vm.onUndoSave()
        assertEquals(link, state.value.links.single().link)
    }
}
