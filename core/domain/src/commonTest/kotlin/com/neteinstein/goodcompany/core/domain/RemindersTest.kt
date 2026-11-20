package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ReminderRepeat
import com.neteinstein.goodcompany.core.domain.model.ReminderSnooze
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.CompleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.EditReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ReopenReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeReminderUseCase
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Issue #99: reminders work like a reminders app (done, snooze, repeat, important). The clock says Tue 10 Mar 2026, 12:00 UTC. */
class RemindersTest {
    private val clock = FixedClock()
    private val repo = FakeReminderRepository()
    private val complete = CompleteReminderUseCase(repo, clock)
    private val snooze = SnoozeReminderUseCase(repo, clock)

    private fun reminder(due: kotlin.time.Instant? = at(2026, 3, 10, 18), repeat: ReminderRepeat = ReminderRepeat.NONE) =
        Reminder("r", "Buy flowers", listOf(FriendId("ana")), due, createdAt = clock.now(), repeat = repeat)

    @Test
    fun doneClosesAOneOff_withWhenItWasDone() = runTest {
        val saved = complete(reminder())
        assertTrue(saved.done)
        assertEquals(clock.now(), saved.completedAt)
        assertEquals(saved, repo.reminders.first().single())
        // It keeps its people: done never turns into a meetup (no hangout repository is even involved).
        assertEquals(listOf(FriendId("ana")), saved.friendIds)
    }

    @Test
    fun doneRollsARepeatingOneOn_andClearsTheSnooze() = runTest {
        val weekly = reminder(repeat = ReminderRepeat.WEEKLY).copy(snoozedUntil = at(2026, 3, 11, 9))
        val saved = complete(weekly)
        assertFalse(saved.done)
        assertNull(saved.completedAt)
        assertNull(saved.snoozedUntil)
        assertEquals(at(2026, 3, 17, 18), saved.dueAt)
    }

    @Test
    fun aRepeatSkipsTheDatesItMissed_andKeepsTheLocalTime() {
        val zone = TimeZone.UTC
        // Due daily from Mar 1 at 08:00; done on Mar 10 at noon → Mar 11, 08:00.
        assertEquals(at(2026, 3, 11, 8), reminder(at(2026, 3, 1, 8), ReminderRepeat.DAILY).nextDueAfter(clock.now(), zone))
        assertEquals(at(2026, 4, 10, 18), reminder(repeat = ReminderRepeat.MONTHLY).nextDueAfter(clock.now(), zone))
        assertEquals(at(2027, 3, 10, 18), reminder(repeat = ReminderRepeat.YEARLY).nextDueAfter(clock.now(), zone))
        // The 31st falls on the last day of a shorter month.
        assertEquals(at(2026, 2, 28, 9), reminder(at(2026, 1, 31, 9), ReminderRepeat.MONTHLY).nextDueAfter(at(2026, 2, 1), zone))
        assertNull(reminder(repeat = ReminderRepeat.NONE).nextDueAfter(clock.now(), zone))
        assertNull(reminder(due = null, repeat = ReminderRepeat.DAILY).nextDueAfter(clock.now(), zone))
        assertFalse(reminder(due = null, repeat = ReminderRepeat.DAILY).isRepeating)
    }

    @Test
    fun anUndatedRepeatingReminder_justCloses() = runTest {
        assertTrue(complete(reminder(due = null, repeat = ReminderRepeat.DAILY)).done)
    }

    @Test
    fun snoozePresets() {
        assertEquals(clock.now() + 1.hours, snooze.until(ReminderSnooze.OneHour))
        assertEquals(at(2026, 3, 10, 20), snooze.until(ReminderSnooze.Tonight))
        assertEquals(at(2026, 3, 11, 9), snooze.until(ReminderSnooze.Tomorrow))
        assertEquals(at(2026, 3, 17, 9), snooze.until(ReminderSnooze.NextWeek))
        assertEquals(at(2026, 3, 13, 13), snooze.until(ReminderSnooze.Until(at(2026, 3, 13, 13))))
        // A time already past (tonight at 21:00, or a picked time) becomes an hour from now.
        clock.instant = at(2026, 3, 10, 21)
        assertFalse(snooze.tonightAvailable())
        assertEquals(at(2026, 3, 10, 22), snooze.until(ReminderSnooze.Tonight))
        assertEquals(at(2026, 3, 10, 22), snooze.until(ReminderSnooze.Until(at(2026, 3, 1))))
        clock.instant = at(2026, 3, 10, 12)
        assertTrue(snooze.tonightAvailable())
    }

    @Test
    fun snoozeMovesOnlyWhenItShows_theDueDateStays() = runTest {
        val saved = snooze(reminder(), ReminderSnooze.Tomorrow)
        assertEquals(at(2026, 3, 11, 9), saved.snoozedUntil)
        assertEquals(at(2026, 3, 10, 18), saved.dueAt)
        assertEquals(at(2026, 3, 11, 9), saved.nextAt)
        assertTrue(saved.isSnoozed)
        // By id (a notification): an unknown id does nothing.
        assertNull(snooze("gone", ReminderSnooze.OneHour))
        assertNull(complete("gone"))
        assertEquals(1, repo.reminders.first().size)
    }

    @Test
    fun editKeepsTheSnooze_onlyWhileTheTimeIsTheSame_andAnUndatedOneCantRepeat() = runTest {
        val edit = EditReminderUseCase(repo)
        val snoozed = reminder().copy(snoozedUntil = at(2026, 3, 11, 9))
        val renamed = edit(snoozed, "Buy tulips", snoozed.friendIds, snoozed.dueAt, null)
        assertEquals(at(2026, 3, 11, 9), renamed.snoozedUntil)
        val moved = edit(renamed, "Buy tulips", renamed.friendIds, at(2026, 3, 12, 9), null, ReminderRepeat.WEEKLY, important = true)
        assertNull(moved.snoozedUntil)
        assertEquals(ReminderRepeat.WEEKLY, moved.repeat)
        assertTrue(moved.important)
        assertEquals(ReminderRepeat.NONE, edit(moved, "Buy tulips", moved.friendIds, null, null).repeat)
        val added = AddReminderUseCase(repo, clock, SequentialIds())("Stretch", emptyList(), null, repeat = ReminderRepeat.DAILY, important = true)
        assertEquals(ReminderRepeat.NONE, added.repeat)
        assertTrue(added.important)
    }

    @Test
    fun reopenAndOrder_openBySnoozeOrDueTime_thenDoneNewestFirst() = runTest {
        repo.upsert(reminder().copy(id = "a", dueAt = at(2026, 3, 12), snoozedUntil = null))
        repo.upsert(reminder().copy(id = "b", dueAt = at(2026, 3, 10, 18), snoozedUntil = at(2026, 3, 15)))
        repo.upsert(reminder().copy(id = "c", done = true, completedAt = at(2026, 3, 1)))
        repo.upsert(reminder().copy(id = "d", done = true, completedAt = at(2026, 3, 5)))
        assertEquals(listOf("a", "b", "d", "c"), ObserveRemindersUseCase(repo)().first().map { it.id })
        val reopened = repo.reminders.first().single { it.id == "c" }
        ReopenReminderUseCase(repo)(reopened)
        val c = repo.reminders.first().single { it.id == "c" }
        assertFalse(c.done)
        assertNull(c.completedAt)
    }

    @Test
    fun theAgendaShowsASnoozedReminderOnTheDayItsSnoozeEnds() {
        val snoozed = reminder().copy(snoozedUntil = at(2026, 3, 13, 9))
        val items = AgendaBuilder.build(listOf(friend("ana")), listOf(snoozed), emptyList(), LocalDate(2026, 3, 10), TimeZone.UTC)
        assertEquals(LocalDate(2026, 3, 13), items.filterIsInstance<AgendaItem.ReminderDue>().single().date)
    }
}
