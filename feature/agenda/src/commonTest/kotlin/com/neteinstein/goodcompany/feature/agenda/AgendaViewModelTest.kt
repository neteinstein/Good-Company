package com.neteinstein.goodcompany.feature.agenda

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetReminderDoneUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaViewModel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgendaViewModelTest {
    private val friends = FakeFriendRepository(listOf(friend("ana") { copy(isCloseFriend = true, birthday = Birthday(3, 12)) }))
    private val hangouts = FakeHangoutRepository()
    private val reminders = FakeReminderRepository()
    private val clock = FixedClock()

    private fun vm() = AgendaViewModel(
        ObserveAgendaUseCase(friends, reminders, hangouts, clock),
        ObserveRemindersUseCase(reminders),
        ObserveFriendsUseCase(friends, hangouts, FakeInteractionRepository(), clock),
        AddReminderUseCase(reminders, clock, SequentialIds()),
        SetReminderDoneUseCase(reminders),
        DeleteReminderUseCase(reminders),
        RestoreReminderUseCase(reminders),
        clock,
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
        assertTrue(state.value.reminders.isEmpty())
        vm.onUndoDelete()
        assertEquals(1, state.value.reminders.size)
        // Restored reminder is still done, so Mar 11 is empty; Mar 12 has Ana's birthday.
        assertEquals(listOf(0, 1), state.value.week.take(3).drop(1).map { it.second })
    }
}
