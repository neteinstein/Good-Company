package com.neteinstein.goodcompany.feature.agenda

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.agenda.ui.screens.today.TodayMessage
import com.neteinstein.goodcompany.feature.agenda.ui.screens.today.TodayUiState
import com.neteinstein.goodcompany.feature.agenda.ui.screens.today.TodayViewModel
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TodayViewModelTest {
    // The test clock says Tuesday 10 March 2026.
    private fun vm(g: TestGraph) = TodayViewModel(
        g.observeAgenda,
        g.observeMeetups,
        g.observeStoryBubbles,
        g.observeFriends,
        g.observeSettings,
        g.addReminder,
        g.confirmPlan,
        g.logCall,
        g.snooze,
        g.draftBirthdayMessage,
        g.clock,
    )

    private fun birthday(id: String, month: Int, day: Int): Friend = friend(id) { copy(birthday = Birthday(month, day)) }

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
        val overdue = (1..5).map { friend("p$it") { copy(meetEveryDays = 14) } }
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
        vm.onSaveReminder("  ", emptyList(), null, 9)
        assertTrue(state.value.showReminderEditor)
        vm.onSaveReminder("Book tickets", listOf(FriendId("ana")), LocalDate(2026, 3, 12), 18)
        assertFalse(state.value.showReminderEditor)
        assertEquals(TodayMessage.ReminderSaved, state.value.message)
        assertEquals("Book tickets", g.reminderRepo.reminders.first().single().title)
        assertIs<AgendaItem.ReminderDue>(state.value.week.single())
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
}
