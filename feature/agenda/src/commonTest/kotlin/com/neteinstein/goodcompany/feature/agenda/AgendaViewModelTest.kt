package com.neteinstein.goodcompany.feature.agenda

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.ReminderRepeat
import com.neteinstein.goodcompany.core.domain.model.ReminderSnooze
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import com.neteinstein.goodcompany.core.testing.Fixtures
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.core.ui.components.ReminderDraft
import com.neteinstein.goodcompany.core.ui.components.ReminderNotice
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
        listOf(friend("ana") { copy(closeness = Closeness.CLOSE, birthday = Birthday(3, 12)) }, friend("rui")),
        settings = AppSettings(aiApiKey = apiKey, aiEnabled = apiKey != null),
    )

    private var g = graph()

    private fun vm() = AgendaViewModel(
        g.observeAgenda,
        g.observeReminders,
        g.observeFriends,
        g.observeSettings,
        g.draftBirthdayMessage,
        g.addReminder,
        g.editReminder,
        g.completeReminder,
        g.reopenReminder,
        g.snoozeReminder,
        g.deleteReminder,
        g.restoreReminder,
        g.confirmPlan,
        g.closePlan,
        g.restorePlan,
        g.clock,
        g.observeCalendarSuggestions,
        g.observeGroups,
    )

    @Test
    fun savingAReminderAddsItToAgendaAtLocalHour() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertIs<AgendaItem.FriendBirthday>(state.value.agenda.single())
        vm.onNewReminder()
        assertTrue(state.value.showReminderEditor)
        vm.onSaveReminder(ReminderDraft("Book tickets", listOf(FriendId("ana")), LocalDate(2026, 3, 11), 18))
        assertFalse(state.value.showReminderEditor)
        assertEquals(AgendaMessage.ReminderSaved, state.value.message)
        val r = state.value.reminders.single()
        assertEquals(at(2026, 3, 11, 18), r.dueAt)
        assertEquals(listOf(LocalDate(2026, 3, 11), LocalDate(2026, 3, 12)), state.value.agendaByDay.map { it.first })
    }

    @Test
    fun theReminderEditorGetsEveryCircle_forThePeoplePicker() = runViewModelTest {
        g.groupRepo.upsert(FriendGroup("choir", "Choir", memberIds = listOf(FriendId("ana"))))
        val state = collecting(vm().state)
        assertEquals(listOf("choir"), state.value.circles.map { it.id })
    }

    @Test
    fun doneRemindersLeaveTheAgendaAndCanBeDeleted() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveReminder(ReminderDraft("Call", emptyList(), LocalDate(2026, 3, 11), 9))
        vm.onSaveReminder(ReminderDraft("  ", emptyList(), null, 9))
        val r = state.value.reminders.single()
        vm.onReminderDone(r)
        assertTrue(state.value.reminders.single().done)
        assertEquals(AgendaMessage.Reminder(ReminderNotice.Done(next = null)), state.value.message)
        // Issue #99: it stays in the "Done" group.
        assertEquals(listOf(r.id), state.value.done.map { it.id })
        assertTrue(state.value.open.isEmpty())
        assertTrue(state.value.agenda.none { it is AgendaItem.ReminderDue })
        val done = state.value.done.single()
        vm.onMessageShown()
        vm.onDeleteReminder(done)
        assertEquals(AgendaMessage.Reminder(ReminderNotice.Deleted), state.value.message)
        assertTrue(state.value.reminders.isEmpty())
        vm.onUndoReminder()
        assertEquals(1, state.value.reminders.size)
        // Restored reminder is still done, so Mar 11 is empty; Mar 12 has Ana's birthday.
        assertEquals(listOf(0, 1), state.value.week.take(3).drop(1).map { it.second })
    }

    @Test
    fun doneWithPeople_neverLogsAMeetup_andUndoOpensItAgain() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveReminder(ReminderDraft("Buy flowers", listOf(FriendId("ana")), LocalDate(2026, 3, 11), 9))
        val r = state.value.reminders.single()
        vm.onReminderDone(r)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
        assertTrue(state.value.reminderUndo != null)
        vm.onUndoReminder()
        assertFalse(state.value.reminders.single().done)
        assertNull(state.value.reminders.single().completedAt)
        vm.onReminderDone(state.value.reminders.single())
        vm.onReminderReopen(state.value.reminders.single())
        assertFalse(state.value.reminders.single().done)
    }

    @Test
    fun aRepeatingReminderRollsOnWhenDone() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveReminder(ReminderDraft("Water plants", emptyList(), LocalDate(2026, 3, 10), 18, repeat = ReminderRepeat.WEEKLY))
        vm.onReminderDone(state.value.reminders.single())
        val r = state.value.reminders.single()
        assertFalse(r.done)
        assertEquals(at(2026, 3, 17, 18), r.dueAt)
        assertEquals(AgendaMessage.Reminder(ReminderNotice.Done(next = at(2026, 3, 17, 18))), state.value.message)
    }

    @Test
    fun snoozeMovesItInTheAgenda_andEditChangesIt() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveReminder(ReminderDraft("Call the vet", emptyList(), LocalDate(2026, 3, 10), 18))
        vm.onSnoozeReminder(state.value.reminders.single(), ReminderSnooze.NextWeek)
        val snoozed = state.value.reminders.single()
        assertEquals(at(2026, 3, 17, 9), snoozed.snoozedUntil)
        assertEquals(AgendaMessage.Reminder(ReminderNotice.Snoozed(at(2026, 3, 17, 9))), state.value.message)
        assertNull(state.value.reminderUndo)
        assertTrue(state.value.agenda.filterIsInstance<AgendaItem.ReminderDue>().all { it.date == LocalDate(2026, 3, 17) })

        vm.onEditReminder(snoozed)
        assertEquals(snoozed, state.value.editingReminder)
        vm.onSaveReminder(ReminderDraft("Call the vet again", emptyList(), LocalDate(2026, 3, 12), 9, important = true))
        val edited = state.value.reminders.single()
        assertEquals("Call the vet again", edited.title)
        assertEquals(at(2026, 3, 12, 9), edited.dueAt)
        assertNull(edited.snoozedUntil)
        assertTrue(edited.important)
        assertNull(state.value.editingReminder)
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
    fun overdueGroupIsListed_andTheAgendaNeverLogsAMeetupItself() = runViewModelTest {
        g.groupRepo.upsert(FriendGroup("g1", "Surf", "🏄", listOf(FriendId("ana"), FriendId("rui")), meetEveryDays = 14))
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.agenda.any { it is AgendaItem.GroupOverdue })
        // "We met" opens the "We met" panel (issue #47); the agenda no longer depends on CheckInUseCase at all.
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
    }

    @Test
    fun pastPlansAskDidYouMeetAndAnswerLogsIt() = runViewModelTest {
        g.savePlan(null, "Lunch", listOf(FriendId("ana")), LocalDate(2026, 3, 9))
        val vm = vm()
        val state = collecting(vm.state)
        val ask = state.value.agenda.filterIsInstance<AgendaItem.PlanToConfirm>().single()
        vm.onConfirmPlan(ask.plan.id, happened = true)
        assertEquals(AgendaMessage.PlanLogged, state.value.message)
        assertTrue(state.value.agenda.none { it is AgendaItem.PlanToConfirm })
        assertEquals(1, g.hangoutRepo.hangouts.value.size)

        val next = g.savePlan(null, "Hike", listOf(FriendId("ana")), LocalDate(2026, 3, 8))
        vm.onConfirmPlan(next.id, happened = false)
        assertEquals(AgendaMessage.PlanClosed, state.value.message)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
    }

    @Test
    fun aRecurringPlanShowsEachDate_andSkippingOneKeepsTheSeries() = runViewModelTest {
        val start = LocalDate(2026, 3, 14)
        g.savePlan(null, "Climbing", listOf(FriendId("ana")), start, repeat = Repeat(15, RepeatUnit.DAY, start))
        val vm = vm()
        val state = collecting(vm.state)
        val dates = state.value.agenda.filterIsInstance<AgendaItem.PlanUpcoming>().map { it.occurrence }
        assertEquals(listOf(start, LocalDate(2026, 3, 29), LocalDate(2026, 4, 13)), dates.take(3))
        val second = state.value.agenda.filterIsInstance<AgendaItem.PlanUpcoming>()[1]
        vm.onSkipPlan(second)
        assertEquals(AgendaMessage.PlanSkipped, state.value.message)
        val after = state.value.agenda.filterIsInstance<AgendaItem.PlanUpcoming>().map { it.occurrence }
        assertEquals(listOf(start, LocalDate(2026, 4, 13)), after.take(2))

        // "Did you meet?" for a past occurrence of a series.
        g.clock.instant = Fixtures.at(2026, 3, 16)
        val ask = collecting(vm().state).value.agenda.filterIsInstance<AgendaItem.PlanToConfirm>().single()
        vm.onConfirmPlan(ask.plan.id, happened = true, day = ask.occurrence)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)
        assertEquals(LocalDate(2026, 4, 13), g.planRepo.plans.value.single().date)
    }

    @Test
    fun howDidItGo_closesThatOccurrence_andCancelOrDeleteCanBeUndone() = runViewModelTest {
        val dinner = g.savePlan(null, "Dinner", listOf(FriendId("ana"), FriendId("rui")), LocalDate(2026, 3, 9))
        val vm = vm()
        val state = collecting(vm.state)
        val ask = state.value.agenda.filterIsInstance<AgendaItem.PlanToConfirm>().single()
        // "It happened" is navigation (the "We met" panel): nothing changes here.
        vm.onAnswerPlan(ask, PlanAnswer.HAPPENED)
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)

        vm.onAnswerPlan(ask, PlanAnswer.DELETE)
        val deleted = assertIs<AgendaMessage.PlanAnswered>(state.value.message)
        assertEquals(dinner, deleted.previous)
        assertTrue(g.planRepo.plans.value.isEmpty())
        vm.onUndoPlan(deleted)
        assertEquals(listOf(dinner), g.planRepo.plans.value)

        vm.onAnswerPlan(ask, PlanAnswer.NOT_ATTENDED)
        val notAttended = assertIs<AgendaMessage.PlanAnswered>(state.value.message)
        assertNull(notAttended.previous)
        assertEquals(PlanStatus.NOT_ATTENDED, g.planRepo.plans.value.single().status)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        vm.onUndoPlan(notAttended) // nothing to undo
        assertEquals(PlanStatus.NOT_ATTENDED, g.planRepo.plans.value.single().status)
    }
}
