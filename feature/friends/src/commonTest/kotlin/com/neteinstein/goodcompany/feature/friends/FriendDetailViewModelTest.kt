package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaGenerator
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailViewModel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendDetailViewModelTest {
    private val friends = FakeFriendRepository(listOf(friend("ana")))
    private val interactions = FakeInteractionRepository()
    private val ideas = FakeIdeaRepository()
    private val settings = FakeSettingsRepository()
    private val clock = FixedClock()
    private val ids = SequentialIds()
    private val hangouts = FakeHangoutRepository()
    private val reminders = FakeReminderRepository()

    private fun vm(id: String = "ana") = FriendDetailViewModel(
        FriendId(id),
        ObserveFriendDetailUseCase(friends, hangouts, interactions, ideas),
        ObserveSettingsUseCase(settings),
        UpdateFriendUseCase(friends),
        DeleteFriendUseCase(friends),
        ImportChatUseCase(friends, interactions, clock),
        GenerateTalkingPointsUseCase(friends, interactions, settings, ideas, FakeIdeaGenerator(), clock, ids),
        DismissIdeaUseCase(ideas),
        CheckInUseCase(hangouts, clock, ids),
        DeleteHangoutUseCase(hangouts),
        AddReminderUseCase(reminders, clock, ids),
        clock,
        ids,
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
        assertEquals(14, f.meetEveryDays)
        assertEquals(listOf("jazz"), f.likes)
        assertEquals(listOf("crowds"), f.dislikes)
        assertEquals(Birthday(5, 4, 1990), f.birthday)
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
        assertEquals("That doesn't look like a WhatsApp chat export.", state.value.message)
        vm.onChatPicked("01/03/2026, 10:00 - Ana: hi")
        assertEquals("Imported 1 messages · matched \"Ana\"", state.value.message)
        assertEquals(1, state.value.detail!!.messageCount)

        vm.onGenerateIdeas()
        assertTrue(state.value.message!!.contains("API key"))
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
        assertEquals("Logged time with Ana ✨", state.value.message)
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
}
