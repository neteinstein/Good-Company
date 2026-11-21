package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.NotificationSettings
import com.neteinstein.goodcompany.core.domain.model.NotificationType
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.usecase.AppLink
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Issue #133: "Heads-up before birthdays", N days before every birthday that notifies on the day. */
class BirthdayHeadsUpTest {
    private val now = at(2026, 3, 1, 12)

    private val ana = friend("ana") { copy(birthday = Birthday(3, 20, 1982)) }
    private val leo = friend("leo") { copy(name = "Leo", birthday = Birthday(3, 25, 2019), tracksKeepInTouch = false) }
    private val rui = friend("rui") {
        copy(
            relatives = listOf(
                Relative("r1", Relation.PARENT, "Maria", Birthday(3, 18, 1950)),
                Relative("r2", Relation.SIBLING, "Off", Birthday(3, 19), notifyBirthday = false),
            ),
        )
    }
    private val links = listOf(PersonLink("l", ana.id, leo.id, LinkType.CHILD))

    private fun plan(
        settings: NotificationSettings = NotificationSettings(),
        friends: List<com.neteinstein.goodcompany.core.domain.model.Friend> = listOf(ana, leo, rui),
        at: Instant = now,
    ): List<PlannedNotification> = NotificationPlanner.plan(
        friends,
        emptyList(),
        emptyList(),
        at,
        TimeZone.UTC,
        reminderHour = 9,
        links = links,
        notifications = settings,
    )

    private fun List<PlannedNotification>.soon() = filter { it.content is NotificationContent.BirthdaySoon }

    @Test
    fun aWeekBeforeByDefault_forAFriendAKidAndARelative() {
        assertEquals(7, NotificationSettings().birthdayHeadsUpDays)
        val soon = plan().soon().associateBy { it.id }
        assertEquals(setOf("bday-soon:ana:2026-03-20", "bday-soon:leo:2026-03-25", "bday-soon:rui:r1:2026-03-18"), soon.keys)

        val forAna = soon.getValue("bday-soon:ana:2026-03-20")
        assertEquals(at(2026, 3, 13, 9), forAna.at)
        assertEquals(NotificationContent.BirthdaySoon("Ana", 7, 44, ana.id), forAna.content)
        assertEquals(AppLink.person(ana.id), forAna.link)
        assertFalse(forAna.persistent, "only the day's one stays on screen")
        assertEquals(NotificationType.BIRTHDAYS, forAna.content.type)

        // A kid with Stay in touch off still gets it, said as whose kid they are; it opens the kid.
        val forLeo = soon.getValue("bday-soon:leo:2026-03-25").content as NotificationContent.BirthdaySoon
        assertEquals(NotificationContent.BirthdaySoon("Leo", 7, 7, leo.id, parentName = "Ana"), forLeo)

        // A relative opens the friend they belong to.
        val forMaria = soon.getValue("bday-soon:rui:r1:2026-03-18")
        assertEquals(NotificationContent.BirthdaySoon("Maria", 7, 76, rui.id, familyOf = "Rui"), forMaria.content)
        assertEquals(AppLink.person(rui.id), forMaria.link)
    }

    @Test
    fun nDaysBefore() {
        listOf(1, 2, 3, 14).forEach { n ->
            val forAna = plan(NotificationSettings(birthdayHeadsUpDays = n)).soon().single { it.id.startsWith("bday-soon:ana") }
            assertEquals(at(2026, 3, 20 - n, 9), forAna.at, "$n")
            assertEquals(n, (forAna.content as NotificationContent.BirthdaySoon).daysBefore)
        }
    }

    @Test
    fun theDaysOwnNotificationIsUnchanged() {
        val planned = plan()
        val day = planned.single { it.id == "bday:ana:2026-03-20" }
        assertEquals(NotificationContent.FriendBirthday("Ana", 44), day.content)
        assertTrue(day.persistent)
        assertTrue(planned.any { it.id == "kid:leo:2026-03-25" })
        assertTrue(planned.any { it.id == "relative:rui:r1:2026-03-18" })
        val birthdays = planned.filter { it.content.type == NotificationType.BIRTHDAYS }
        assertEquals(birthdays.size, birthdays.map { it.slot }.toSet().size, "the heads-up never shares the day's slot")
    }

    @Test
    fun noneWhenOff_orWhenBirthdaysAreOff_orForSomeoneWhoseDayDoesntNotify() {
        assertTrue(plan(NotificationSettings(birthdayHeadsUpDays = 0)).soon().isEmpty())
        assertTrue(plan(NotificationSettings(birthdays = false)).soon().isEmpty())
        assertTrue(plan(NotificationSettings(enabled = false)).isEmpty())
        assertTrue(plan(friends = listOf(ana.copy(notifyBirthday = false))).soon().isEmpty())
        assertTrue(plan().none { it.id.contains(":r2:") })
    }

    @Test
    fun aHeadsUpThatWouldBeInThePastIsSkipped() {
        // Five days before Ana's birthday: a week's notice is already gone, the day still comes.
        val planned = plan(friends = listOf(ana), at = at(2026, 3, 15, 12))
        assertTrue(planned.soon().isEmpty())
        assertTrue(planned.any { it.id == "bday:ana:2026-03-20" })
    }

    @Test
    fun acrossTheYearBoundary() {
        val jan = friend("jan") { copy(birthday = Birthday(1, 3, 2000)) }
        val forJan = plan(friends = listOf(jan), at = at(2026, 12, 20, 12)).soon().single()
        assertEquals("bday-soon:jan:2027-01-03", forJan.id)
        assertEquals(at(2026, 12, 27, 9), forJan.at)
        assertEquals(27, (forJan.content as NotificationContent.BirthdaySoon).age)
    }

    @Test
    fun noAgeWhenTheYearIsUnknown() {
        val zoe = friend("zoe") { copy(birthday = Birthday(3, 20)) }
        assertNull((plan(friends = listOf(zoe)).soon().single().content as NotificationContent.BirthdaySoon).age)
    }

    @Test
    fun personLinksRoundTrip() {
        assertEquals("goodcompany://person/ana", AppLink.person(FriendId("ana")))
        assertEquals(AppLink.Person(FriendId("ana")), AppLink.parse("goodcompany://person/ana"))
        assertNull(AppLink.parse("goodcompany://person"))
    }
}
