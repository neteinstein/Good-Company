package org.goodcompany.app.feature.agenda

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.Birthday
import org.goodcompany.app.core.domain.model.CalendarEvent
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.NotificationSettings
import org.goodcompany.app.core.domain.model.PlanAnswer
import org.goodcompany.app.core.domain.model.PlanStatus
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.model.ReminderSnooze
import org.goodcompany.app.core.domain.model.Repeat
import org.goodcompany.app.core.domain.model.RepeatUnit
import org.goodcompany.app.core.domain.usecase.LastSeenAnswer
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.domain.usecase.ReminderTarget
import org.goodcompany.app.core.domain.usecase.SnoozeOption
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.core.ui.components.ReminderDraft
import org.goodcompany.app.feature.agenda.ui.screens.today.TodayMessage
import org.goodcompany.app.feature.agenda.ui.screens.today.TodayUiState
import org.goodcompany.app.feature.agenda.ui.screens.today.TodayViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TodayViewModelTest {
    // The test clock says Tuesday 10 March 2026.
    private fun vm(g: TestGraph) = TodayViewModel(
        g.observeAgenda,
        g.observeMeetups,
        g.observeStoryBubbles,
        g.observeFriends,
        g.observeSettings,
        g.observeYearReviewCard,
        g.observeLastSeenCard,
        g.addReminder,
        g.editReminder,
        g.completeReminder,
        g.snoozeReminder,
        g.deleteReminder,
        g.restoreReminder,
        g.confirmPlan,
        g.closePlan,
        g.restorePlan,
        g.savePlan,
        g.logCall,
        g.snooze,
        g.draftBirthdayMessage,
        g.answerLastSeen,
        g.hideLastSeenCard,
        g.dismissNotificationPrimer,
        g.clock,
        g.observeGroups,
        g.answerLastMet,
        g.turnOffCircleReminders,
        g.restoreGroup,
        g.observeCalendarQuestions,
        g.answerCalendarQuestion,
        g.undoCalendarDecision,
    )

    private fun birthday(id: String, month: Int, day: Int): Friend = friend(id) { copy(birthday = Birthday(month, day)) }

    @Test
    fun theNotificationsCardCanShow_untilNotNow_orNotificationsAreOffInSettings() = runViewModelTest {
        // Issue #98: the screen adds "the system doesn't allow them"; the state says whether Settings wants them.
        val g = TestGraph(listOf(friend("ana")))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertTrue(state.value.askForNotifications)
        vm.onNotificationsNotNow()
        assertFalse(state.value.askForNotifications)
        assertTrue(g.settingsRepo.settings.value.notificationPrimerDismissed)

        val off = TestGraph(listOf(friend("ana")), settings = AppSettings(notifications = NotificationSettings(enabled = false)))
        assertFalse(collecting(vm(off).state).value.askForNotifications)
    }

    @Test
    fun theReminderEditorGetsEveryCircle_forThePeoplePicker() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana")))
        g.groupRepo.upsert(FriendGroup("choir", "Choir", memberIds = listOf(FriendId("ana"))))
        val state = collecting(vm(g).state)
        assertEquals(listOf("choir"), state.value.circles.map { it.id })
        assertEquals(listOf("ana"), state.value.friends.map { it.id.value })
    }

    @Test
    fun weekIsTheNextSevenDaysIncludingToday() = runViewModelTest {
        val g = TestGraph(
            listOf(birthday("today", 3, 10), birthday("sixth", 3, 16), birthday("seventh", 3, 17), birthday("later", 4, 2)),
        )
        val state = collecting(vm(g).state)
        val week = state.value.week
        assertTrue(week.all { it is AgendaItem.FriendBirthday })
        assertEquals(listOf("today", "sixth"), week.map { (it as AgendaItem.FriendBirthday).friend.id.value })
        assertEquals(listOf(LocalDate(2026, 3, 10), LocalDate(2026, 3, 16)), state.value.weekByDay.map { it.first })
    }

    @Test
    fun atMostThreeCatchUps_theRestCounted_andNoneInTheWeekList() = runViewModelTest {
        // Answered "this month" on the last-seen card (issue #68), so all five are overdue.
        val overdue = (1..5).map { friend("p$it") { copy(meetEveryDays = 14, approxLastSeen = LocalDate(2026, 2, 23)) } }
        val g = TestGraph(overdue)
        val state = collecting(vm(g).state)
        assertEquals(TodayUiState.MAX_CATCH_UPS, state.value.catchUp.size)
        assertEquals(5, state.value.catchUpTotal)
        assertTrue(state.value.week.none { it is AgendaItem.MeetupOverdue })
    }

    @Test
    fun checkInShowsOnceThereIsSomeone() = runViewModelTest {
        val someone = collecting(vm(TestGraph(listOf(friend("ana")))).state)
        assertTrue(someone.value.showCheckIn)
        assertFalse(someone.value.isEmpty)

        val nobody = collecting(vm(TestGraph()).state)
        assertFalse(nobody.value.showCheckIn)
        assertTrue(nobody.value.isEmpty)
    }

    @Test
    fun checkInSavedElsewhereShowsWhoWith() = runViewModelTest {
        val vm = vm(TestGraph(listOf(friend("ana"))))
        val state = collecting(vm.state)
        vm.onCheckedIn(listOf("Ana"))
        assertEquals(TodayMessage.CheckedIn(listOf("Ana")), state.value.message)
        vm.onMessageShown()
        assertEquals(null, state.value.message)
    }

    @Test
    fun newReminderIsSaved() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana")))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onNewReminder()
        assertTrue(state.value.showReminderEditor)
        vm.onSaveReminder(ReminderDraft("  ", emptyList(), null, 9))
        assertTrue(state.value.showReminderEditor)
        vm.onSaveReminder(ReminderDraft("Book tickets", listOf(FriendId("ana")), LocalDate(2026, 3, 12), 18))
        assertFalse(state.value.showReminderEditor)
        assertEquals(TodayMessage.ReminderSaved, state.value.message)
        assertEquals("Book tickets", g.reminderRepo.reminders.first().single().title)
        assertIs<AgendaItem.ReminderDue>(state.value.week.single())
    }

    @Test
    fun aReminderInTheWeek_isDoneSnoozedEditedOrDeleted_withUndo() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana")))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onSaveReminder(ReminderDraft("Buy flowers", listOf(FriendId("ana")), LocalDate(2026, 3, 12), 18))
        val r = g.reminderRepo.reminders.first().single()
        vm.onReminderDone(r)
        assertIs<TodayMessage.Reminder>(state.value.message)
        assertTrue(g.reminderRepo.reminders.first().single().done)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        vm.onUndoReminder()
        assertFalse(g.reminderRepo.reminders.first().single().done)
        vm.onSnoozeReminder(r, ReminderSnooze.Tomorrow)
        assertEquals(LocalDate(2026, 3, 11), (state.value.week.single() as AgendaItem.ReminderDue).date)
        vm.onEditReminder(r)
        assertEquals(r, state.value.editingReminder)
        vm.onSaveReminder(ReminderDraft("Buy tulips", listOf(FriendId("ana")), LocalDate(2026, 3, 12), 18))
        assertEquals("Buy tulips", g.reminderRepo.reminders.first().single().title)
        vm.onDeleteReminder(g.reminderRepo.reminders.first().single())
        assertTrue(g.reminderRepo.reminders.first().isEmpty())
        vm.onUndoReminder()
        assertEquals("Buy tulips", g.reminderRepo.reminders.first().single().title)
        vm.onMessageShown()
        assertNull(state.value.reminderUndo)
    }

    @Test
    fun snoozeAndCallFromACatchUpCard() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana", "Ana Lima") { copy(meetEveryDays = 14) }))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onCalled(FriendId("ana"))
        assertEquals(TodayMessage.CallLogged("Ana"), state.value.message)
        vm.onSnooze(FriendId("ana"), SnoozeOption.ONE_WEEK)
        assertIs<TodayMessage.Snoozed>(state.value.message)
        assertTrue(state.value.catchUp.isEmpty())
    }

    // --- "When did you last see…?" (issue #68) ---

    @Test
    fun lastSeenCard_asksOnePersonAtATime_andAnAnswerMovesOn() = runViewModelTest {
        val g = TestGraph(
            listOf(
                friend("ana", "Ana Lima") { copy(meetEveryDays = 30) },
                friend("bob", "Bob") { copy(meetEveryDays = 30) },
                friend("cat"), // no rhythm: never asked
            ),
        )
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(FriendId("ana"), state.value.lastSeenAsk?.id)
        assertEquals(1, state.value.lastSeenPosition)
        assertEquals(2, state.value.lastSeenTotal)
        // Never-logged people aren't overdue.
        assertTrue(state.value.catchUp.isEmpty())

        vm.onLastSeenAnswer(FriendId("ana"), LastSeenAnswer.OVER_A_YEAR)
        assertEquals(TodayMessage.LastSeenAnswered(FriendId("ana"), "Ana", LastSeenAnswer.OVER_A_YEAR, null), state.value.message)
        assertEquals(LocalDate(2025, 2, 3), g.friendRepo.get(FriendId("ana"))?.approxLastSeen)
        assertEquals(FriendId("bob"), state.value.lastSeenAsk?.id)
        assertEquals(2, state.value.lastSeenPosition)
        assertEquals(2, state.value.lastSeenTotal)
        // "Over a year" puts Ana straight into "Time to catch up".
        assertEquals(listOf(FriendId("ana")), state.value.catchUp.map { it.friend.id })

        vm.onLastSeenAnswer(FriendId("bob"), LastSeenAnswer.DONT_KNOW)
        assertNull(state.value.lastSeenAsk)
        assertEquals(LocalDate(2026, 3, 10), g.friendRepo.get(FriendId("bob"))?.approxLastSeen)
    }

    // --- Circles (issue #150) ---

    @Test
    fun lastSeenCard_asksACircleAfterThePeople_andTheAnswerOnlyStartsItsRhythm() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana", "Ana Lima") { copy(meetEveryDays = 30) }, friend("bob", "Bob")))
        g.groupRepo.upsert(FriendGroup("choir", "Choir", "🎶", listOf(FriendId("ana"), FriendId("bob")), meetEveryDays = 15))
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(FriendId("ana"), state.value.lastSeenAsk?.id)
        assertNull(state.value.lastMetAsk)
        assertEquals(2, state.value.lastSeenTotal)
        // Never met and never answered: no circle row on Today.
        assertTrue(state.value.week.none { it is AgendaItem.GroupOverdue })

        vm.onLastSeenAnswer(FriendId("ana"), LastSeenAnswer.THIS_WEEK)
        assertNull(state.value.lastSeenAsk)
        assertEquals("choir", state.value.lastMetAsk?.id)
        assertEquals(listOf("ana", "bob"), state.value.lastMetMembers.map { it.id.value })
        assertEquals(2, state.value.lastSeenPosition)
        assertEquals(2, state.value.lastSeenTotal)

        vm.onLastMetAnswer("choir", LastSeenAnswer.FEW_MONTHS)
        val answered = assertIs<TodayMessage.LastMetAnswered>(state.value.message)
        assertEquals(TodayMessage.LastMetAnswered("choir", "Choir", LastSeenAnswer.FEW_MONTHS, null), answered)
        assertNull(state.value.lastMetAsk)
        assertEquals(LocalDate(2025, 12, 10), g.groupRepo.groups.value.single().approxLastMet)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        val row = state.value.week.filterIsInstance<AgendaItem.GroupOverdue>().single()
        assertNull(row.daysSince)

        vm.onUndoLastMet(answered)
        assertNull(g.groupRepo.groups.value.single().approxLastMet)
        assertEquals("choir", state.value.lastMetAsk?.id)
    }

    @Test
    fun aCirclesRow_canBeSnoozed_orHaveItsRemindersTurnedOff_withUndo() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana"), friend("bob")))
        val choir =
            FriendGroup("choir", "Choir", "🎶", listOf(FriendId("ana"), FriendId("bob")), meetEveryDays = 15, approxLastMet = LocalDate(2026, 1, 1))
        g.groupRepo.upsert(choir)
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(1, state.value.week.count { it is AgendaItem.GroupOverdue })

        // The same snoozes as people: until Saturday (the test clock is Tuesday 10 March).
        vm.onSnoozeCircle("choir", SnoozeOption.WEEKEND)
        assertEquals(TodayMessage.Snoozed("Choir", LocalDate(2026, 3, 14)), state.value.message)
        assertEquals(LocalDate(2026, 3, 14), g.groupRepo.groups.value.single().snoozedUntil)
        assertTrue(state.value.week.none { it is AgendaItem.GroupOverdue })
        vm.onMessageShown()

        g.groupRepo.upsert(choir)
        vm.onCircleRemindersOff("choir")
        val off = assertIs<TodayMessage.CircleRemindersOff>(state.value.message)
        assertEquals(choir, off.previous)
        assertNull(g.groupRepo.groups.value.single().meetEveryDays)
        assertTrue(state.value.week.none { it is AgendaItem.GroupOverdue })

        vm.onUndoCircleRemindersOff(off)
        assertEquals(15, g.groupRepo.groups.value.single().meetEveryDays)
        assertEquals(1, state.value.week.count { it is AgendaItem.GroupOverdue })
    }

    @Test
    fun lastSeenUndo_asksAgain() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana") { copy(meetEveryDays = 30) }, friend("bob") { copy(meetEveryDays = 30) }))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onLastSeenAnswer(FriendId("ana"), LastSeenAnswer.THIS_WEEK)
        val answered = assertIs<TodayMessage.LastSeenAnswered>(state.value.message)
        assertEquals(FriendId("bob"), state.value.lastSeenAsk?.id)

        vm.onUndoLastSeen(answered)
        assertNull(g.friendRepo.get(FriendId("ana"))?.approxLastSeen)
        assertNull(state.value.message)
        assertEquals(FriendId("ana"), state.value.lastSeenAsk?.id)
        assertEquals(1, state.value.lastSeenPosition)
        assertEquals(2, state.value.lastSeenTotal)
    }

    @Test
    fun lastSeenNotNow_hidesTheCardForToday() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana") { copy(meetEveryDays = 30) }))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onLastSeenNotNow()
        assertNull(state.value.lastSeenAsk)
        assertNull(g.friendRepo.get(FriendId("ana"))?.approxLastSeen)
        assertEquals(LocalDate(2026, 3, 10), g.settingsRepo.settings.first().lastSeenCardHiddenOn)
    }

    @Test
    fun howDidItGo_onToday_cancelsWithUndo_andDidntHappenCloses() = runViewModelTest {
        val g = TestGraph(listOf(friend("ana")))
        val lunch = g.savePlan(null, "Lunch", listOf(FriendId("ana")), LocalDate(2026, 3, 9))
        val vm = vm(g)
        val state = collecting(vm.state)
        val ask = state.value.week.filterIsInstance<AgendaItem.PlanToConfirm>().single()
        vm.onAnswerPlan(ask, PlanAnswer.CANCEL)
        val cancelled = assertIs<TodayMessage.PlanAnswered>(state.value.message)
        assertEquals(lunch, cancelled.previous)
        assertEquals(PlanStatus.CANCELLED, g.planRepo.plans.value.single().status)
        vm.onUndoPlan(cancelled)
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)

        vm.onAnswerPlan(ask, PlanAnswer.DIDNT_HAPPEN)
        assertEquals(TodayMessage.PlanAnswered(PlanAnswer.DIDNT_HAPPEN), state.value.message)
        assertEquals(PlanStatus.SKIPPED, g.planRepo.plans.value.single().status)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
    }

    @Test
    fun aReminderTap_opensItsCircle_itsOnePerson_orItsEditor() = runViewModelTest {
        // Issue #135: a circle's reminder never opens one of its people.
        val g = TestGraph(listOf(friend("ana"), friend("rui")))
        val vm = vm(g)
        val state = collecting(vm.state)
        val created = Instant.fromEpochSeconds(0)
        assertEquals(
            ReminderTarget.Circle("surf"),
            vm.onOpenReminder(Reminder("c", "Wax the boards", listOf(FriendId("rui"), FriendId("ana")), createdAt = created, circleId = "surf")),
        )
        assertEquals(
            ReminderTarget.Person(FriendId("rui")),
            vm.onOpenReminder(Reminder("r", "Call Rui", listOf(FriendId("rui")), createdAt = created)),
        )
        assertFalse(state.value.showReminderEditor)
        val nobody = Reminder("n", "Water the plants", createdAt = created)
        assertEquals(ReminderTarget.Edit("n"), vm.onOpenReminder(nobody))
        assertTrue(state.value.showReminderEditor)
        assertEquals(nobody, state.value.editingReminder)
    }

    @Test
    fun itHappenedMenu_onToday_iDidntGoAndDelete() = runViewModelTest {
        // Issue #136.
        val g = TestGraph(listOf(friend("ana"), friend("rui")))
        val two = listOf(FriendId("ana"), FriendId("rui"))
        val dinner = g.savePlan(null, "Dinner", two, LocalDate(2026, 3, 9))
        val vm = vm(g)
        val state = collecting(vm.state)
        fun ask() = state.value.week.filterIsInstance<AgendaItem.PlanToConfirm>().single()
        vm.onAnswerPlan(ask(), PlanAnswer.NOT_ATTENDED)
        assertEquals(TodayMessage.PlanAnswered(PlanAnswer.NOT_ATTENDED), state.value.message)
        assertEquals(PlanStatus.NOT_ATTENDED, g.planRepo.plans.value.single().status)

        val lunch = g.savePlan(null, "Lunch", two, LocalDate(2026, 3, 9))
        vm.onAnswerPlan(ask(), PlanAnswer.DELETE)
        val deleted = assertIs<TodayMessage.PlanAnswered>(state.value.message)
        assertEquals(lunch, deleted.previous)
        assertEquals(listOf(dinner.id), g.planRepo.plans.value.map { it.id })
        vm.onUndoPlan(deleted)
        assertEquals(setOf(dinner.id, lunch.id), g.planRepo.plans.value.map { it.id }.toSet())
    }

    @Test
    fun upcomingPlanMenu_onToday_editSkipCancelAndDelete() = runViewModelTest {
        // Issue #136.
        val g = TestGraph(listOf(friend("ana")))
        val start = LocalDate(2026, 3, 12)
        val run = g.savePlan(null, "Run", listOf(FriendId("ana")), start, repeat = Repeat(1, RepeatUnit.DAY, start))
        val vm = vm(g)
        val state = collecting(vm.state)
        fun upcoming() = state.value.week.filterIsInstance<AgendaItem.PlanUpcoming>()

        vm.onEditPlan(run)
        assertEquals(run, state.value.editingPlan)
        vm.onSavePlan(PlanInput("Long run", listOf(FriendId("ana")), start, repeat = run.repeat))
        assertNull(state.value.editingPlan)
        assertEquals(TodayMessage.PlanSaved, state.value.message)
        assertEquals("Long run", g.planRepo.plans.value.single().title)

        vm.onSkipPlan(upcoming().first())
        assertEquals(TodayMessage.PlanSkipped, state.value.message)
        assertEquals(LocalDate(2026, 3, 13), upcoming().first().occurrence)

        vm.onAnswerUpcomingPlan(upcoming().first(), PlanAnswer.CANCEL, wholeSeries = true)
        val cancelled = assertIs<TodayMessage.PlanAnswered>(state.value.message)
        assertEquals(PlanStatus.CANCELLED, g.planRepo.plans.value.single().status)
        vm.onUndoPlan(cancelled)
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)

        vm.onAnswerUpcomingPlan(upcoming().first(), PlanAnswer.DELETE, wholeSeries = true)
        assertNotNull(assertIs<TodayMessage.PlanAnswered>(state.value.message).previous)
        assertTrue(g.planRepo.plans.value.isEmpty())
    }

    @Test
    fun whoIsAnaShowsOnToday_thenTheAnswerFillsBeforeYouMeet_withUndo() = runViewModelTest {
        // Decision 2026-11-26: two Anas, an event tomorrow that only says "Ana".
        val g = TestGraph(
            listOf(friend("ana", "Ana"), friend("aj", "Ana Jorge")),
            AppSettings(calendarSuggestions = true, calendarIds = listOf("personal")),
        )
        val start = g.clock.now() + 1.days
        g.calendarReader.events = listOf(CalendarEvent("lunch", "personal", "Almoço com a Ana", start, start + 1.hours))
        val vm = vm(g)
        val state = collecting(vm.state)
        val q = assertNotNull(state.value.calendarQuestion)
        assertTrue(state.value.stories.isEmpty())

        vm.onAnswerCalendarQuestion(q, setOf(FriendId("ana")))
        val message = assertIs<TodayMessage.CalendarAnswered>(state.value.message)
        assertNull(state.value.calendarQuestion)
        assertEquals(listOf("ana"), state.value.stories.map { it.friend.id.value })

        vm.onUndoCalendarAnswer(message)
        assertNotNull(state.value.calendarQuestion)
        vm.onCalendarQuestionNotNow(q)
        assertNull(state.value.calendarQuestion)
    }
}
