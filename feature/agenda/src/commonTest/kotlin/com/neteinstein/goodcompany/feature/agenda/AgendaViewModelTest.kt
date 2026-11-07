package com.neteinstein.goodcompany.feature.agenda

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaMessage
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaViewModel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgendaViewModelTest {
    private fun graph(apiKey: String? = null) = TestGraph(
        listOf(friend("ana") { copy(isCloseFriend = true, birthday = Birthday(3, 12)) }, friend("rui")),
        settings = AppSettings(aiApiKey = apiKey),
    )

    private var g = graph()

    private fun vm() = AgendaViewModel(
        g.observeAgenda,
        g.observeReminders,
        g.observeFriends,
        g.observeSettings,
        g.draftBirthdayMessage,
        g.checkIn,
        g.addReminder,
        g.setReminderDone,
        g.deleteReminder,
        g.restoreReminder,
        g.clock,
    )

    @Test
    fun savingAReminderAddsItToAgendaAtLocalHour() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertIs<AgendaItem.FriendBirthday>(state.value.agenda.single())
        vm.onNewReminder()
        assertTrue(state.value.showReminderEditor)
        vm.onSaveReminder("Book tickets", listOf(FriendId("ana")), LocalDate(2026, 3, 11), 18)
        assertFalse(state.value.showReminderEditor)
        assertEquals(AgendaMessage.ReminderSaved, state.value.message)
        val r = state.value.reminders.single()
        assertEquals(at(2026, 3, 11, 18), r.dueAt)
        assertEquals(listOf(LocalDate(2026, 3, 11), LocalDate(2026, 3, 12)), state.value.agendaByDay.map { it.first })
    }

    @Test
    fun doneRemindersLeaveTheAgendaAndCanBeDeleted() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveReminder("Call", emptyList(), LocalDate(2026, 3, 11), 9)
        vm.onSaveReminder("  ", emptyList(), null, 9)
        val r = state.value.reminders.single()
        vm.onToggleDone(r)
        assertTrue(state.value.reminders.single().done)
        assertTrue(state.value.agenda.none { it is AgendaItem.ReminderDue })
        vm.onDeleteReminder(r.id)
        assertEquals(AgendaMessage.ReminderDeleted, state.value.message)
        assertTrue(state.value.reminders.isEmpty())
        vm.onUndoDelete()
        assertEquals(1, state.value.reminders.size)
        // Restored reminder is still done, so Mar 11 is empty; Mar 12 has Ana's birthday.
        assertEquals(listOf(0, 1), state.value.week.take(3).drop(1).map { it.second })
    }

    @Test
    fun birthdayWishesCanBePersonalised() = runViewModelTest {
        g = graph(apiKey = "sk-test")
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.hasAiKey)
        vm.onOpenWish(FriendId("ana"))
        assertEquals("Ana", state.value.wishFor?.name)
        vm.onPersonaliseWish()
        assertEquals("Happy birthday!", state.value.wishDraft)
        vm.onDismissWish()
        assertNull(state.value.wishFor)
    }

    @Test
    fun personalisingWithoutKeyReportsAiError() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onOpenWish(FriendId("ana"))
        vm.onPersonaliseWish()
        assertIs<AgendaMessage.AiError>(state.value.message)
    }

    @Test
    fun overdueGroupCanBeMarkedAsMet() = runViewModelTest {
        g.groupRepo.upsert(FriendGroup("g1", "Surf", "🏄", listOf(FriendId("ana"), FriendId("rui")), meetEveryDays = 14))
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.agenda.any { it is AgendaItem.GroupOverdue })
        vm.onGroupMet("g1")
        assertEquals(AgendaMessage.GroupMet("Surf"), state.value.message)
        assertTrue(state.value.agenda.none { it is AgendaItem.GroupOverdue })
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
    }
}
