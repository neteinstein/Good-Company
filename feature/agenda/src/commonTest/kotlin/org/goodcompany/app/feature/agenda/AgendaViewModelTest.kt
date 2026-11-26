package org.goodcompany.app.feature.agenda

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.AgendaView
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.Birthday
import org.goodcompany.app.core.domain.model.Closeness
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.PlanAnswer
import org.goodcompany.app.core.domain.model.PlanStatus
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.model.ReminderRepeat
import org.goodcompany.app.core.domain.model.ReminderSnooze
import org.goodcompany.app.core.domain.model.Repeat
import org.goodcompany.app.core.domain.model.RepeatUnit
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.domain.usecase.ReminderTarget
import org.goodcompany.app.core.testing.Fixtures
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.core.ui.components.ReminderDraft
import org.goodcompany.app.core.ui.components.ReminderNotice
import org.goodcompany.app.feature.agenda.ui.screens.AgendaMessage
import org.goodcompany.app.feature.agenda.ui.screens.AgendaViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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
        g.savePlan,
        g.setAgendaView,
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
        // Issue #150: a circle with nothing logged starts its rhythm from "When did the circle last meet?".
        g.groupRepo.upsert(
            FriendGroup("g1", "Surf", "🏄", listOf(FriendId("ana"), FriendId("rui")), meetEveryDays = 14, approxLastMet = LocalDate(2026, 1, 1)),
        )
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

    @Test
    fun aReminderTap_opensItsCircle_itsOnePerson_orItsEditor() = runViewModelTest {
        // Issue #135.
        val vm = vm()
        val state = collecting(vm.state)
        val created = Instant.fromEpochSeconds(0)
        val forCircle = Reminder("c", "Book the court", listOf(FriendId("ana")), createdAt = created, circleId = "surf")
        val forAna = Reminder("a", "Call Ana", listOf(FriendId("ana")), createdAt = created)
        val forTwo = Reminder("t", "Dinner", listOf(FriendId("ana"), FriendId("rui")), createdAt = created)
        assertEquals(ReminderTarget.Circle("surf"), vm.onOpenReminder(forCircle))
        assertEquals(ReminderTarget.Person(FriendId("ana")), vm.onOpenReminder(forAna))
        assertFalse(state.value.showReminderEditor, "a circle or a person opens their page, not the editor")
        assertEquals(ReminderTarget.Edit("t"), vm.onOpenReminder(forTwo))
        assertTrue(state.value.showReminderEditor)
        assertEquals(forTwo, state.value.editingReminder)
    }

    @Test
    fun aReminderNotification_opensItsEditorById_andAnUnknownIdOpensNothing() = runViewModelTest {
        val reminder = Reminder("t", "Dinner", listOf(FriendId("ana"), FriendId("rui")), createdAt = Instant.fromEpochSeconds(0))
        g.reminderRepo.upsert(reminder)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onEditReminderById("gone")
        assertFalse(state.value.showReminderEditor)
        vm.onEditReminderById("t")
        assertTrue(state.value.showReminderEditor)
        assertEquals(reminder, state.value.editingReminder)
    }

    @Test
    fun itHappenedMenu_eachAnswerClosesThatPlan() = runViewModelTest {
        // Issue #136: Didn't happen · I didn't go · Cancel plan · Delete behind ▾.
        val vm = vm()
        val state = collecting(vm.state)
        fun ask() = state.value.agenda.filterIsInstance<AgendaItem.PlanToConfirm>().single()
        val two = listOf(FriendId("ana"), FriendId("rui"))
        listOf(PlanAnswer.DIDNT_HAPPEN to PlanStatus.SKIPPED, PlanAnswer.NOT_ATTENDED to PlanStatus.NOT_ATTENDED).forEach { (answer, status) ->
            val p = g.savePlan(null, "Dinner $answer", two, LocalDate(2026, 3, 9))
            vm.onAnswerPlan(ask(), answer)
            assertEquals(AgendaMessage.PlanAnswered(answer), state.value.message)
            assertEquals(status, g.planRepo.plans.value.single { it.id == p.id }.status)
        }
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())

        val lunch = g.savePlan(null, "Lunch", two, LocalDate(2026, 3, 9))
        vm.onAnswerPlan(ask(), PlanAnswer.CANCEL)
        val cancelled = assertIs<AgendaMessage.PlanAnswered>(state.value.message)
        assertEquals(lunch, cancelled.previous)
        assertEquals(PlanStatus.CANCELLED, g.planRepo.plans.value.single { it.id == lunch.id }.status)
        vm.onUndoPlan(cancelled)
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single { it.id == lunch.id }.status)
    }

    @Test
    fun upcomingPlanMenu_editCancelAndDelete_withUndo() = runViewModelTest {
        // Issue #136: Edit · Add to calendar · Skip this one · Cancel plan · Delete in the ⋮.
        val start = LocalDate(2026, 3, 14)
        val climbing = g.savePlan(null, "Climbing", listOf(FriendId("ana")), start, repeat = Repeat(1, RepeatUnit.WEEK, start))
        val vm = vm()
        val state = collecting(vm.state)
        fun upcoming() = state.value.agenda.filterIsInstance<AgendaItem.PlanUpcoming>()

        vm.onEditPlan(climbing)
        assertEquals(climbing, state.value.editingPlan)
        vm.onSavePlan(PlanInput("", listOf(FriendId("ana")), start))
        assertEquals(climbing, state.value.editingPlan, "a blank title doesn't save")
        vm.onSavePlan(PlanInput("Bouldering", listOf(FriendId("ana")), start, repeat = climbing.repeat))
        assertNull(state.value.editingPlan)
        assertEquals(AgendaMessage.PlanSaved, state.value.message)
        assertEquals("Bouldering", g.planRepo.plans.value.single().title)
        assertEquals(climbing.id, g.planRepo.plans.value.single().id)
        vm.onEditPlan(climbing)
        vm.onDismissPlanEditor()
        assertNull(state.value.editingPlan)

        // Cancel just this one: the date comes out, the series goes on; Undo puts it back.
        vm.onAnswerUpcomingPlan(upcoming().first(), PlanAnswer.CANCEL, wholeSeries = false)
        val one = assertIs<AgendaMessage.PlanAnswered>(state.value.message)
        assertEquals(PlanAnswer.CANCEL, one.answer)
        assertEquals(LocalDate(2026, 3, 21), upcoming().first().occurrence)
        vm.onUndoPlan(one)
        assertEquals(start, upcoming().first().occurrence)

        // Delete the whole series; Undo brings it back.
        vm.onAnswerUpcomingPlan(upcoming().first(), PlanAnswer.DELETE, wholeSeries = true)
        val deleted = assertIs<AgendaMessage.PlanAnswered>(state.value.message)
        assertTrue(g.planRepo.plans.value.isEmpty())
        vm.onUndoPlan(deleted)
        assertEquals("Bouldering", g.planRepo.plans.value.single().title)

        // Only Cancel and Delete are answers in the ⋮.
        vm.onMessageShown()
        vm.onAnswerUpcomingPlan(upcoming().first(), PlanAnswer.DIDNT_HAPPEN)
        assertNull(state.value.message)
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)
    }

    @Test
    fun calendar_isTheDefaultView_andTheToggleIsRemembered() = runViewModelTest {
        // Issue #138.
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(AgendaView.CALENDAR, state.value.view)
        vm.onSetView(AgendaView.LIST)
        assertEquals(AgendaView.LIST, state.value.view)
        assertEquals(AgendaView.LIST, g.settingsRepo.settings.first().agendaView)
        vm.onSetView(AgendaView.CALENDAR)
        assertEquals(AgendaView.CALENDAR, state.value.view)
    }

    @Test
    fun calendar_opensOnToday_aTapPicksADay_andMonthsMove() = runViewModelTest {
        // Issue #138. Today is 10 March 2026; Ana's birthday is on the 12th.
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(LocalDate(2026, 3, 1), state.value.month)
        assertEquals(LocalDate(2026, 3, 10), state.value.selectedDay)

        vm.onSelectDay(LocalDate(2026, 3, 12))
        assertEquals(LocalDate(2026, 3, 12), state.value.selectedDay)
        assertIs<AgendaItem.FriendBirthday>(state.value.selectedItems.single())

        vm.onNextMonth()
        assertEquals(LocalDate(2026, 4, 1), state.value.month)
        assertEquals(LocalDate(2026, 4, 1), state.value.selectedDay)
        vm.onPreviousMonth()
        vm.onPreviousMonth()
        assertEquals(LocalDate(2026, 2, 1), state.value.month)

        // A day of the month after, from the grid's last row, moves the calendar there.
        vm.onSelectDay(LocalDate(2026, 3, 2))
        assertEquals(LocalDate(2026, 3, 1), state.value.month)
        vm.onToday()
        assertEquals(LocalDate(2026, 3, 1), state.value.month)
        assertEquals(LocalDate(2026, 3, 10), state.value.selectedDay)
    }

    @Test
    fun calendar_showsLaterMonths_theListKeepsItsWindow_andOverdueStaysOnToday() = runViewModelTest {
        // Issue #138: a birthday in September is past the list's two months but shows when the calendar gets there.
        g.friendRepo.upsert(friend("eva") { copy(closeness = Closeness.CLOSE, birthday = Birthday(9, 2)) })
        g.hangoutRepo.add(Hangout("h", listOf(FriendId("rui")), Fixtures.at(2025, 10, 1)))
        g.friendRepo.upsert(g.friendRepo.get(FriendId("rui"))!!.copy(closeness = Closeness.CLOSE))
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.agenda.none { it.date == LocalDate(2026, 9, 2) })
        repeat(6) { vm.onNextMonth() }
        assertEquals(LocalDate(2026, 9, 1), state.value.month)
        assertTrue(state.value.calendarByDay.getValue(LocalDate(2026, 9, 2)).any { it is AgendaItem.FriendBirthday })
        assertTrue(state.value.agenda.none { it.date == LocalDate(2026, 9, 2) }, "the list keeps its window")

        vm.onToday()
        assertTrue(state.value.selectedItems.any { it is AgendaItem.MeetupOverdue && it.friend.id == FriendId("rui") })
    }
}
