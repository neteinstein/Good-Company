package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.NotificationChannel
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.NotificationSettings
import com.neteinstein.goodcompany.core.domain.model.NotificationType
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.CalendarSuggestion
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.NudgePolicy
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #98: Settings → Notifications switches, persistent notifications and the test notification. */
class NotificationSettingsTest {
    private val now = at(2026, 3, 10, 12)
    private val ana = FriendId("ana")

    private val friends = listOf(
        friend("ana") {
            copy(
                birthday = Birthday(3, 20, 1990),
                meetEveryDays = 7,
                approxLastSeen = LocalDate(2026, 3, 1),
                importantEvents = listOf(ImportantEvent("e", "Surgery", LocalDate(2026, 3, 22), remindDaysBefore = 2)),
            )
        },
    )
    private val reminders = listOf(
        Reminder("soon", "Book tickets", listOf(ana), at(2026, 3, 12, 18), createdAt = now),
        Reminder("late", "Call the vet", emptyList(), at(2026, 3, 8, 10), createdAt = now),
    )
    private val plans = listOf(Plan("p", listOf(ana), "Lunch", LocalDate(2026, 3, 14), createdAt = now))
    private val calendar = emptyList<CalendarSuggestion>()

    private fun plan(settings: NotificationSettings): List<PlannedNotification> = NotificationPlanner.plan(
        friends,
        reminders,
        emptyList(),
        now,
        TimeZone.UTC,
        reminderHour = 9,
        plans = plans,
        policy = NudgePolicy(weeklyDigest = false),
        calendar = calendar,
        notifications = settings,
    )

    private fun List<PlannedNotification>.types() = map { it.content.type }.toSet()

    @Test
    fun everyTypeIsPlannedByDefault() {
        val types = plan(NotificationSettings()).types()
        assertEquals(
            setOf(NotificationType.BIRTHDAYS, NotificationType.EVENTS, NotificationType.REMINDERS, NotificationType.PLANS, NotificationType.CATCH_UPS),
            types,
        )
    }

    @Test
    fun theMasterSwitchStopsEverything() {
        assertTrue(plan(NotificationSettings(enabled = false)).isEmpty())
    }

    @Test
    fun eachTypeSwitchRemovesOnlyThatType() {
        listOf(NotificationType.BIRTHDAYS, NotificationType.EVENTS, NotificationType.REMINDERS, NotificationType.PLANS, NotificationType.CATCH_UPS)
            .forEach { type ->
                val types = plan(NotificationSettings().with(type, false)).types()
                assertFalse(type in types, "$type still planned")
                assertEquals(4, types.size, "only $type should go")
            }
    }

    @Test
    fun theCalendarSwitchStopsTheCalendarSummary() {
        val content = NotificationContent.CalendarMeetups(listOf("Ana"), "Dinner", 1)
        assertEquals(NotificationType.CALENDAR, content.type)
        assertFalse(NotificationSettings(calendar = false).allows(NotificationType.CALENDAR))
        assertTrue(NotificationSettings().allows(NotificationType.CALENDAR))
        assertFalse(NotificationSettings(enabled = false).allows(NotificationType.CALENDAR))
    }

    @Test
    fun birthdaysBigDaysAndPlansAreKeptOnScreen_untilTheEndOfTheDay() {
        val planned = plan(NotificationSettings())
        val birthday = planned.single { it.id == "bday:ana:2026-03-20" }
        assertTrue(birthday.persistent)
        assertEquals(at(2026, 3, 21, 0), birthday.until)
        assertEquals(NotificationChannel.IMPORTANT, birthday.channel)
        assertTrue(planned.single { it.id == "event:e:day" }.persistent)
        // The heads-up before a big day isn't.
        val before = planned.single { it.id == "event:e:before" }
        assertFalse(before.persistent)
        assertNull(before.until)
        assertEquals(NotificationChannel.BIRTHDAYS, before.channel)
        assertTrue(planned.single { it.id == "plan:p:day" }.persistent)
        assertTrue(planned.single { it.id == "plan:p:ask" }.let { it.persistent && it.whileOpen })
        // Today's plan and birthdays stay once posted, even when no longer planned.
        assertFalse(birthday.whileOpen || planned.single { it.id == "plan:p:day" }.whileOpen)
        // A catch-up never is, and goes in the quiet channel.
        val catchUp = planned.first { it.content is NotificationContent.MeetupDue }
        assertFalse(catchUp.persistent)
        assertEquals(NotificationChannel.CATCH_UPS, catchUp.channel)
    }

    @Test
    fun aReminderDueLaterIsNotPersistent_untilItIsMarkedImportant() {
        // The important flag arrives with issue #99; until then a due reminder is a normal one.
        val due = plan(NotificationSettings()).single { it.id == "reminder:soon" }
        assertFalse(due.persistent)
        assertEquals(NotificationChannel.REMINDERS, due.channel)
        assertEquals(at(2026, 3, 12, 18), due.at)
    }

    @Test
    fun anOverdueReminderIsAskedEachDay_inOneSlot_untilDone() {
        val planned = plan(NotificationSettings())
        val asks = planned.filter { it.content is NotificationContent.ReminderOverdue && it.slot == "reminder:late" }
        // Today's reminder hour has passed (noon), so the next asks are from tomorrow.
        assertEquals(
            listOf("reminder:late:overdue:2026-03-11", "reminder:late:overdue:2026-03-12", "reminder:late:overdue:2026-03-13"),
            asks.map { it.id },
        )
        assertTrue(asks.all { it.persistent && it.whileOpen && it.channel == NotificationChannel.IMPORTANT })
        assertEquals(at(2026, 3, 11, 9), asks.first().at)
        // The reminder still due later gets its own asks from the day after it's due, in its own slot.
        val soon = planned.filter { it.content is NotificationContent.ReminderOverdue && it.slot == "reminder:soon" }
        assertEquals("reminder:soon:overdue:2026-03-13", soon.first().id)
        // Done: nothing.
        val done = NotificationPlanner.plan(friends, reminders.map { it.copy(done = true) }, emptyList(), now, TimeZone.UTC, 9)
        assertTrue(done.none { it.content.type == NotificationType.REMINDERS })
    }

    @Test
    fun withKeepOnScreenOff_nothingIsPersistentAndOverdueRemindersArentAskedAgain() {
        val planned = plan(NotificationSettings(persistent = false))
        assertTrue(planned.none { it.persistent || it.until != null || it.whileOpen })
        assertTrue(planned.none { it.content is NotificationContent.ReminderOverdue })
        assertTrue(planned.any { it.id == "bday:ana:2026-03-20" })
    }

    @Test
    fun channelsFollowTheType() {
        assertEquals(NotificationChannel.BIRTHDAYS, NotificationChannel.of(NotificationType.EVENTS, persistent = false))
        assertEquals(NotificationChannel.PLANS, NotificationChannel.of(NotificationType.PLANS, persistent = false))
        assertEquals(NotificationChannel.CATCH_UPS, NotificationChannel.of(NotificationType.CALENDAR, persistent = false))
        assertEquals(NotificationChannel.REMINDERS, NotificationChannel.of(NotificationType.REMINDERS, persistent = false))
        assertEquals(NotificationChannel.IMPORTANT, NotificationChannel.of(NotificationType.CATCH_UPS, persistent = true))
        assertEquals(NotificationType.REMINDERS, NotificationContent.Test.type)
        assertEquals(NotificationType.BIRTHDAYS, NotificationContent.Anniversary("A", "B", 3).type)
    }

    @Test
    fun theScheduleCarriesChannelPersistenceAndSlot() = runTest {
        val g = TestGraph(friends, settings = AppSettings(lastOpenedAt = now))
        reminders.forEach { g.reminderRepo.upsert(it) }
        g.keepNotificationsScheduled.once()
        val scheduled = g.scheduler.scheduled.last()
        val ask = scheduled.first { it.id == "reminder:late:overdue:2026-03-11" }
        assertTrue(ask.persistent)
        assertEquals("reminder:late", ask.slot)
        assertTrue(ask.whileOpen)
        assertEquals(NotificationChannel.IMPORTANT, ask.channel)
        assertEquals(at(2026, 3, 12, 0), ask.until)
    }

    @Test
    fun notificationSettingsAreSaved() = runTest {
        val g = TestGraph(friends)
        g.updateNotificationSettings { it.copy(catchUps = false, persistent = false) }
        assertEquals(NotificationSettings(catchUps = false, persistent = false), g.settingsRepo.settings.value.notifications)
        g.keepNotificationsScheduled.once()
        assertTrue(g.scheduler.scheduled.last().none { it.persistent })
    }

    @Test
    fun theTestNotificationShowsRightAway_privateWhenNotificationsArePrivate() = runTest {
        val g = TestGraph(friends, settings = AppSettings(privateNotifications = true))
        g.sendTestNotification()
        val shown = g.scheduler.shownNow.single()
        assertEquals("test", shown.id)
        assertEquals(NotificationChannel.REMINDERS, shown.channel)
        assertTrue(shown.publicText != null)
        assertTrue(g.scheduler.scheduled.isEmpty())
    }

    @Test
    fun notNowHidesTheTodayCard() = runTest {
        val g = TestGraph(friends)
        g.dismissNotificationPrimer()
        assertTrue(g.settingsRepo.settings.value.notificationPrimerDismissed)
    }
}
