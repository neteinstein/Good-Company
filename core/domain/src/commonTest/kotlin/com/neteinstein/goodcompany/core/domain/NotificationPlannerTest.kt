package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationPlannerTest {
    private val now = at(2026, 3, 10, 12)

    @Test
    fun birthdayNotificationsOnlyWhenOptedIn() {
        val friends = listOf(
            friend("ana") {
                copy(notifyBirthday = true, birthday = Birthday(3, 20, 1990), kids = listOf(Kid("k", "Leo", Birthday(3, 25), listOf("lego"))))
            },
            friend("bob") { copy(isCloseFriend = true, birthday = Birthday(3, 21)) },
        )
        val planned = NotificationPlanner.plan(friends, emptyList(), emptyList(), now, TimeZone.UTC, reminderHour = 9)
        assertEquals(listOf("bday:ana:2026-03-20", "kid:k:2026-03-25"), planned.map { it.id })
        assertEquals(at(2026, 3, 20, 9), planned[0].at)
        assertEquals(NotificationContent.FriendBirthday("Ana", 36), planned[0].content)
        assertEquals(listOf("lego"), (planned[1].content as NotificationContent.KidBirthday).likes)
    }

    @Test
    fun todaysBirthdayAfterReminderHourIsSkipped() {
        val friends = listOf(friend("ana") { copy(notifyBirthday = true, birthday = Birthday(3, 10)) })
        val planned = NotificationPlanner.plan(friends, emptyList(), emptyList(), now, TimeZone.UTC, reminderHour = 9)
        assertTrue(planned.none { it.id.startsWith("bday:ana:2026-03-10") })
    }

    @Test
    fun importantEventPingsOnTheDayAndBefore() {
        val friends =
            listOf(friend("ana") { copy(importantEvents = listOf(ImportantEvent("e", "Surgery", LocalDate(2026, 3, 20), remindDaysBefore = 2))) })
        val planned = NotificationPlanner.plan(friends, emptyList(), emptyList(), now, TimeZone.UTC, reminderHour = 9)
        assertEquals(listOf("event:e:before", "event:e:day"), planned.map { it.id })
        assertEquals(at(2026, 3, 18, 9), planned[0].at)
    }

    @Test
    fun remindersFireAtTheirDueTimeAndSkipDoneOrPast() {
        val reminders = listOf(
            Reminder(
                "a",
                "Book tickets",
                listOf(com.neteinstein.goodcompany.core.domain.model.FriendId("ana")),
                at(2026, 3, 11, 18),
                createdAt = now,
            ),
            Reminder("b", "Done", emptyList(), at(2026, 3, 11, 18), done = true, createdAt = now),
            Reminder("c", "Past", emptyList(), at(2026, 3, 9), createdAt = now),
        )
        val planned = NotificationPlanner.plan(listOf(friend("ana")), reminders, emptyList(), now, TimeZone.UTC, 9)
        val single = planned.single()
        assertEquals("reminder:a", single.id)
        assertEquals(NotificationContent.ReminderDue("Book tickets", listOf("Ana")), single.content)
    }

    @Test
    fun overdueMeetupNudgesTomorrowAndFutureOnDueDate() {
        val friends = listOf(
            friend("ana") { copy(meetEveryDays = 7) },
            friend("rui") { copy(meetEveryDays = 30) },
        )
        val hangouts = listOf(Hangout("h", listOf(com.neteinstein.goodcompany.core.domain.model.FriendId("rui")), at(2026, 3, 1)))
        val planned = NotificationPlanner.plan(friends, emptyList(), hangouts, now, TimeZone.UTC, 9)
        assertEquals(listOf("meet:ana:2026-03-11", "meet:rui:2026-03-31"), planned.map { it.id })
    }

    @Test
    fun planIsDeterministic() {
        val friends = listOf(friend("ana") { copy(notifyBirthday = true, birthday = Birthday(4, 1), meetEveryDays = 10) })
        assertEquals(
            NotificationPlanner.plan(friends, emptyList(), emptyList(), now, TimeZone.UTC, 9),
            NotificationPlanner.plan(friends, emptyList(), emptyList(), now, TimeZone.UTC, 9),
        )
    }
}
