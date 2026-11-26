package org.goodcompany.app.core.domain

import kotlinx.datetime.TimeZone
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.NotificationSettings
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.usecase.AppLink
import org.goodcompany.app.core.domain.usecase.NotificationPlanner
import org.goodcompany.app.core.domain.usecase.OpenInbox
import org.goodcompany.app.core.domain.usecase.ReminderTarget
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Issue #135: where a tap on a reminder goes, and the same for its notification. */
class ReminderTargetTest {
    private val created = Instant.fromEpochSeconds(0)
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")

    private fun reminder(vararg people: String, circle: String? = null, id: String = "r") =
        Reminder(id, "Title", people.map(::FriendId), dueAt = at(2026, 3, 12, 10), createdAt = created, circleId = circle)

    @Test
    fun aCircleWins_evenWithPeople() {
        assertEquals(ReminderTarget.Circle("surf"), ReminderTarget.of(reminder(circle = "surf")))
        assertEquals(ReminderTarget.Circle("surf"), ReminderTarget.of(reminder("ana", circle = "surf")))
        assertEquals(ReminderTarget.Circle("surf"), ReminderTarget.of(reminder("ana", "rui", circle = "surf")))
    }

    @Test
    fun exactlyOnePersonOpensThem() {
        assertEquals(ReminderTarget.Person(ana), ReminderTarget.of(reminder("ana")))
        assertEquals(ReminderTarget.Person(ana), ReminderTarget.of(reminder("ana", "ana")), "the same person twice is one person")
    }

    @Test
    fun severalPeopleOrNobodyOpensTheEditor() {
        assertEquals(ReminderTarget.Edit("r"), ReminderTarget.of(reminder("ana", "rui")))
        assertEquals(ReminderTarget.Edit("r"), ReminderTarget.of(reminder()))
    }

    @Test
    fun linksMatchTheRule_andParseBack() {
        assertEquals("goodcompany://circle/surf", ReminderTarget.linkFor(reminder("ana", circle = "surf")))
        assertEquals("goodcompany://person/ana", ReminderTarget.linkFor(reminder("ana")))
        assertEquals("goodcompany://reminder/r", ReminderTarget.linkFor(reminder("ana", "rui")))
        assertEquals(AppLink.Circle("surf"), AppLink.parse("goodcompany://circle/surf"))
        assertEquals(AppLink.Reminder("r"), AppLink.parse("goodcompany://reminder/r"))
        assertEquals(AppLink.Person(ana), AppLink.parse(AppLink.person(ana)))
        assertNull(AppLink.parse("goodcompany://circle"))
        assertNull(AppLink.parse("goodcompany://reminder/"))
    }

    @Test
    fun theOpenInboxKeepsOnlyPagesToOpen() {
        val inbox = OpenInbox()
        assertFalse(inbox.offer(AppLink.Log(ana)))
        assertFalse(inbox.offer(AppLink.Met(ana)))
        assertNull(inbox.pending.value)
        assertTrue(inbox.offer(AppLink.Reminder("r")))
        assertEquals(AppLink.Reminder("r"), inbox.pending.value)
        inbox.clear()
        assertNull(inbox.pending.value)
    }

    @Test
    fun reminderNotificationsCarryTheTapTarget_andTheirActionsStay() {
        val reminders = listOf(reminder("ana", circle = "surf", id = "c"), reminder("ana", id = "p"), reminder("ana", "rui", id = "e"))
        val planned = NotificationPlanner.plan(
            listOf(friend("ana"), friend("rui")),
            reminders,
            emptyList(),
            at(2026, 3, 10, 12),
            TimeZone.UTC,
            reminderHour = 9,
            notifications = NotificationSettings(persistent = false, dailyLog = false, birthdayHeadsUpDays = 0),
        ).associateBy { it.id }
        assertEquals("goodcompany://circle/surf", planned.getValue("reminder:c").link)
        assertEquals("goodcompany://person/ana", planned.getValue("reminder:p").link)
        assertEquals("goodcompany://reminder/e", planned.getValue("reminder:e").link)
    }
}
