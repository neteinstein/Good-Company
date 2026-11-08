package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.usecase.MissingApiKeyException
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailMessage
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.PlanEditing
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
        settings = AppSettings(aiApiKey = apiKey),
    )

    private var g = graph()
    private val reminders get() = g.reminderRepo

    private fun vm(id: String = "ana") = FriendDetailViewModel(
        FriendId(id),
        g.observeFriendDetail,
        g.observeSettings,
        g.updateFriend,
        g.deleteFriend,
        g.setRealFriend,
        g.setCircle,
        g.snooze,
        g.logCall,
        g.importChat,
        g.generateTalkingPoints,
        g.dismissIdea,
        g.draftBirthdayMessage,
        g.suggestGifts,
        g.checkIn,
        g.deleteHangout,
        g.addReminder,
        g.setCatchUpPaused,
        g.savePlan,
        g.confirmPlan,
        g.cancelPlan,
        g.restorePlan,
        g.clock,
        g.ids,
    )

    @Test
    fun editsFlowBackIntoState() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onToggleClose()
        vm.onToggleBirthdayNotifications()
        vm.onMeetEveryChange(14)
        vm.onAddLikes(listOf("jazz", "sushi"))
        vm.onAddLikes(listOf("jazz"))
        vm.onRemoveLike("sushi")
        vm.onAddDislikes(listOf("crowds"))
        vm.onBirthdayChange(LocalDate(1990, 5, 4))
        val f = state.value.detail!!.friend
        assertTrue(f.isCloseFriend && f.notifyBirthday)
        assertEquals(Circle.CLOSE, f.circle)
        assertEquals(14, f.meetEveryDays)
        assertEquals(listOf("jazz"), f.likes)
        assertEquals(listOf("crowds"), f.dislikes)
        assertEquals(Birthday(5, 4, 1990), f.birthday)
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
        vm.onSaveKid(null, "Mia", null, emptyList())
        vm.onAddEvent("Moving day", LocalDate(2026, 4, 1), 0)
        val f = state.value.detail!!.friend
        assertEquals(listOf("New job at a studio"), f.lifeUpdates.map { it.text })
        assertEquals(now, f.lifeUpdates.single().addedAt)
        // "jazz" was already there, so only the new like gets a date.
        assertEquals(mapOf("climbing" to now), f.likesAddedAt)
        assertEquals(now, f.kids.single().addedAt)
        assertEquals(now, f.importantEvents.single().addedAt)
        assertEquals(4, state.value.freshCount)

        // Editing a kid keeps when it was added.
        g.clock.advanceBy(kotlin.time.Duration.parse("1d"))
        vm.onSaveKid(f.kids.single().id, "Mia", null, listOf("ducks"))
        assertEquals(now, state.value.detail!!.friend.kids.single().addedAt)

        vm.onRemoveLike("climbing")
        vm.onRemoveLifeUpdate(f.lifeUpdates.single().id)
        val after = state.value.detail!!.friend
        assertTrue(after.lifeUpdates.isEmpty())
        assertTrue(after.likesAddedAt.isEmpty())
    }

    @Test
    fun kidsAndEventsCanBeAddedEditedAndRemoved() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveKid(null, "Leo", LocalDate(2020, 4, 1), listOf("lego"))
        val kid = state.value.detail!!.friend.kids.single()
        vm.onSaveKid(kid.id, "Leo", null, listOf("dinosaurs"))
        assertEquals(listOf("dinosaurs"), state.value.detail!!.friend.kids.single().likes)
        vm.onRemoveKid(kid.id)
        assertTrue(state.value.detail!!.friend.kids.isEmpty())

        vm.onAddEvent("Surgery", LocalDate(2026, 3, 20), 1)
        vm.onAddEvent("  ", LocalDate(2026, 3, 21), 1)
        assertEquals(listOf("Surgery"), state.value.detail!!.friend.importantEvents.map { it.title })
    }

    @Test
    fun chatImportAndAiWithoutKeyReportMessages() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onChatPicked("not a chat")
        assertEquals(FriendDetailMessage.ChatInvalid, state.value.message)
        vm.onChatPicked("01/03/2026, 10:00 - Ana: hi")
        assertEquals(FriendDetailMessage.ChatImported(1, "Ana"), state.value.message)
        assertEquals(1, state.value.detail!!.messageCount)

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
    fun togetherNowLogsHangoutCelebratesAndCanBeUndone() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onTogetherNow()
        assertEquals(1, state.value.celebrate)
        assertEquals(FriendDetailMessage.LoggedWith("Ana"), state.value.message)
        assertEquals(1, state.value.detail!!.hangouts.size)
        vm.onUndoTogether()
        assertTrue(state.value.detail!!.hangouts.isEmpty())
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
        vm.onCircleChange(Circle.INNER)
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
        vm.onSaveGift("  ", "", null)
        vm.onSaveGift("Vinyl", "Blue Note", null)
        vm.onSaveGift("Dino lego", "", "k1")
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
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSuggestGifts("k1")
        assertEquals(listOf("Vinyl", "Concert tickets"), state.value.giftSuggestions!!.ideas)
        assertEquals("gifts:ana:Leo:en", g.assistant.calls.single())
        vm.onKeepGiftSuggestion("Vinyl")
        assertEquals("k1", state.value.detail!!.friend.gifts.single().forKidId)
        vm.onKeepGiftSuggestion("Concert tickets")
        assertNull(state.value.giftSuggestions)

        g.assistant.gifts = Result.success(listOf("Vinyl"))
        vm.onSuggestGifts("k1")
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
        vm.onSuggestGifts(null)
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
        vm.onSavePlan("Lunch", emptyList(), LocalDate(2026, 3, 12), "Tasca")
        assertNull(state.value.planEditing)
        assertEquals(FriendDetailMessage.PlanSaved, state.value.message)
        val plan = state.value.detail!!.plans.single()
        assertEquals(listOf(FriendId("ana")), plan.friendIds)

        vm.onOpenPlanEditor(plan)
        vm.onSavePlan("Long lunch", listOf(FriendId("ana")), LocalDate(2026, 3, 13), null)
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
}
