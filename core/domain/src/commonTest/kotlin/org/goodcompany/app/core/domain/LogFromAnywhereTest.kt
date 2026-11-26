package org.goodcompany.app.core.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.NotificationChannel
import org.goodcompany.app.core.domain.model.NotificationContent
import org.goodcompany.app.core.domain.model.NotificationSettings
import org.goodcompany.app.core.domain.model.NotificationType
import org.goodcompany.app.core.domain.model.RenderedNotification
import org.goodcompany.app.core.domain.usecase.AppLink
import org.goodcompany.app.core.domain.usecase.LogKind
import org.goodcompany.app.core.domain.usecase.LogRequest
import org.goodcompany.app.core.domain.usecase.LogResult
import org.goodcompany.app.core.domain.usecase.LogTimeOfDay
import org.goodcompany.app.core.domain.usecase.NotificationPlanner
import org.goodcompany.app.core.domain.usecase.PlaceBook
import org.goodcompany.app.core.domain.usecase.TimelineEntry
import org.goodcompany.app.core.domain.usecase.asLogRequest
import org.goodcompany.app.core.domain.usecase.logInstant
import org.goodcompany.app.core.domain.usecase.planned
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #116: logging takes a time of day, and a daily reminder asks to log the day. */
class LogFromAnywhereTest {
    private val zone = TimeZone.UTC
    private val now = at(2026, 3, 10, 15, 30)
    private val today = LocalDate(2026, 3, 10)

    @Test
    fun noTime_isNowToday_andNoonOnAnEarlierDay() {
        assertEquals(now, logInstant(null, null, now, zone))
        assertEquals(now, logInstant(today, null, now, zone))
        assertEquals(at(2026, 3, 8, 12), logInstant(LocalDate(2026, 3, 8), null, now, zone))
    }

    @Test
    fun aChipOrAPickedTime_isThatTimeOnThatDay() {
        assertEquals(at(2026, 3, 10, 13), logInstant(today, LogTimeOfDay.LUNCH.time, now, zone))
        assertEquals(at(2026, 3, 8, 20), logInstant(LocalDate(2026, 3, 8), LogTimeOfDay.EVENING.time, now, zone))
        assertEquals(at(2026, 3, 9, 7, 45), logInstant(LocalDate(2026, 3, 9), LocalTime(7, 45), now, zone))
    }

    @Test
    fun laterThanNowToday_isNow_andAFutureDayFails() {
        assertEquals(now, logInstant(today, LogTimeOfDay.EVENING.time, now, zone))
        assertFailsWith<IllegalArgumentException> { logInstant(LocalDate(2026, 3, 11), LocalTime(9, 0), now, zone) }
    }

    @Test
    fun theChipsAreTheBriefsTimes() {
        assertEquals(listOf(LocalTime(9, 0), LocalTime(13, 0), LocalTime(16, 0), LocalTime(20, 0)), LogTimeOfDay.entries.map { it.time })
        assertEquals(LogTimeOfDay.LUNCH, LogTimeOfDay.of(LocalTime(13, 0)))
        assertNull(LogTimeOfDay.of(LocalTime(13, 5)))
    }

    @Test
    fun loggingAtLunch_writesItAtLunch() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        g.clock.instant = now
        val met = g.logInteraction(LogRequest(listOf(FriendId("ana")), LogKind.MEETUP, today, time = LogTimeOfDay.LUNCH.time))
        assertEquals(at(2026, 3, 10, 13), assertIs<LogResult.Met>(met).hangout.at)
        val call = g.logInteraction(LogRequest(listOf(FriendId("ana")), LogKind.CALL, LocalDate(2026, 3, 9)))
        assertEquals(at(2026, 3, 9, 12), assertIs<LogResult.Contacts>(call).interactions.single().at)
    }

    @Test
    fun editingShowsTheEntrysTime_andANewTimeMovesIt() = runTest {
        val h = Hangout("h", listOf(FriendId("ana")), at(2026, 3, 9, 19, 12))
        val g = TestGraph(listOf(friend("ana")), hangouts = listOf(h))
        g.clock.instant = now
        val entry = TimelineEntry.Meetup(h, others = emptyList())
        val request = assertIsNotNull(entry.asLogRequest(FriendId("ana"), zone))
        assertEquals(LocalTime(19, 12), request.time)
        // Unchanged day and time: the entry keeps its instant.
        g.editTimelineEntry(entry, request)
        assertEquals(h.at, g.hangoutRepo.hangouts.value.single().at)
        g.editTimelineEntry(entry, request.copy(time = LogTimeOfDay.MORNING.time))
        assertEquals(at(2026, 3, 9, 9), g.hangoutRepo.hangouts.value.single().at)
    }

    @Test
    fun lastTogether_isTheNewestMeetupWithEveryone() {
        val ana = FriendId("ana")
        val rui = FriendId("rui")
        val book = PlaceBook(
            hangouts = listOf(
                Hangout("1", listOf(ana, rui), at(2026, 2, 1)),
                Hangout("2", listOf(ana), at(2026, 3, 1)),
                Hangout("3", listOf(ana, rui, FriendId("bea")), at(2026, 2, 20)),
            ),
        )
        assertEquals(at(2026, 3, 1), book.lastTogether(listOf(ana)))
        assertEquals(at(2026, 2, 20), book.lastTogether(listOf(ana, rui)))
        assertNull(book.lastTogether(emptyList()))
        assertNull(book.lastTogether(listOf(FriendId("zed"))))
    }

    @Test
    fun dailyLogReminder_onByDefaultAt21_forTheNextDays_opensTheLogPopup() {
        val planned = NotificationPlanner.plan(emptyList(), emptyList(), emptyList(), now, zone, reminderHour = 9)
        val daily = planned.filter { it.content == NotificationContent.DailyLog }
        assertEquals(
            listOf(at(2026, 3, 10, 21), at(2026, 3, 11, 21), at(2026, 3, 12, 21)),
            daily.map { it.at },
        )
        assertTrue(daily.all { it.link == AppLink.log() && it.slot == "daily-log" && !it.persistent })
        assertEquals(NotificationType.DAILY_LOG, NotificationContent.DailyLog.type)
        assertEquals(NotificationChannel.REMINDERS, daily.first().channel)
    }

    @Test
    fun dailyLogReminder_followsItsTime_andItsSwitch() {
        val at2030 = NotificationSettings(dailyLogMinute = 20 * 60 + 30)
        assertEquals(LocalTime(20, 30), at2030.dailyLogTime)
        val late = at(2026, 3, 10, 22)
        val planned = NotificationPlanner.plan(emptyList(), emptyList(), emptyList(), late, zone, 9, notifications = at2030)
        // Today's has passed: tomorrow's first.
        assertEquals(at(2026, 3, 11, 20, 30), planned.first { it.content == NotificationContent.DailyLog }.at)

        val off = NotificationSettings().with(NotificationType.DAILY_LOG, false)
        assertTrue(
            NotificationPlanner.plan(emptyList(), emptyList(), emptyList(), now, zone, 9, notifications = off).none {
                it.content ==
                    NotificationContent.DailyLog
            },
        )
        val allOff = NotificationSettings(enabled = false)
        assertTrue(NotificationPlanner.plan(emptyList(), emptyList(), emptyList(), now, zone, 9, notifications = allOff).isEmpty())
    }

    @Test
    fun dailyLogReminder_isNotThrottledByTheCatchUpBudget() = runTest {
        // The app hasn't been opened for months: catch-ups go silent, the daily log reminder doesn't.
        val policy = org.goodcompany.app.core.domain.usecase.NudgePolicy(weeklyDigest = false, lastOpenedAt = at(2025, 10, 1))
        val planned = NotificationPlanner.plan(emptyList(), emptyList(), emptyList(), now, zone, 9, policy = policy)
        assertTrue(planned.any { it.content == NotificationContent.DailyLog })
    }

    @Test
    fun theRenderedNotificationCarriesTheLink() {
        val daily = NotificationPlanner.plan(emptyList(), emptyList(), emptyList(), now, zone, 9).first { it.content == NotificationContent.DailyLog }
        val rendered = RenderedNotification(daily.id, daily.at, "title", "body").planned(daily, null)
        assertEquals(AppLink.log(), rendered.link)
    }

    private fun <T : Any> assertIsNotNull(value: T?): T {
        kotlin.test.assertNotNull(value)
        return value
    }
}
