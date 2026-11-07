package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CadenceAndGroupsTest {
    private val today = LocalDate(2026, 3, 10) // a Tuesday
    private val rules = CadenceRules(innerDays = 7, closeDays = 30, goodDays = 90)

    @Test
    fun customCadenceBeatsCircleAndCircleNeedsRealFriend() {
        assertEquals(7, rules.cadenceFor(friend("a") { copy(isCloseFriend = true, circle = Circle.INNER) }))
        assertEquals(90, rules.cadenceFor(friend("a") { copy(isCloseFriend = true, circle = Circle.GOOD) }))
        assertEquals(14, rules.cadenceFor(friend("a") { copy(isCloseFriend = true, circle = Circle.GOOD, meetEveryDays = 14) }))
        assertNull(rules.cadenceFor(friend("a") { copy(isCloseFriend = false, circle = Circle.INNER) }))
        assertEquals(CadenceRules.Source.CUSTOM, rules.sourceFor(friend("a") { copy(meetEveryDays = 3) }))
        assertEquals(CadenceRules.Source.CIRCLE, rules.sourceFor(friend("a") { copy(isCloseFriend = true, circle = Circle.CLOSE) }))
        assertEquals(CadenceRules(3, 20, 60), CadenceRules.from(AppSettings(innerCircleDays = 3, closeCircleDays = 20, goodFriendsDays = 60)))
    }

    @Test
    fun circleFriendsGetNudgesWithoutPerFriendSetup() {
        val friends = listOf(friend("ana") { copy(isCloseFriend = true, circle = Circle.INNER) })
        val hangouts = listOf(Hangout("h", listOf(FriendId("ana")), at(2026, 3, 1)))
        val status = Relationships.meetupStatuses(friends, hangouts, today, TimeZone.UTC, rules).single()
        assertEquals(LocalDate(2026, 3, 8), status.dueDate)
        assertEquals(7, status.cadenceDays)
        assertTrue(status.isOverdue(today))
    }

    @Test
    fun snoozePushesTheDueDateAndExpires() {
        val snoozed = friend("ana") { copy(meetEveryDays = 7, snoozedUntil = LocalDate(2026, 3, 14)) }
        val status = Relationships.meetupStatuses(listOf(snoozed), emptyList(), today, TimeZone.UTC).single()
        assertEquals(LocalDate(2026, 3, 15), status.dueDate)
        assertFalse(status.isOverdue(today))
        // An old snooze no longer matters.
        val old = snoozed.copy(snoozedUntil = LocalDate(2026, 3, 1))
        assertTrue(Relationships.meetupStatuses(listOf(old), emptyList(), today, TimeZone.UTC).single().isOverdue(today))
    }

    @Test
    fun snoozeOptions() {
        assertEquals(LocalDate(2026, 3, 17), SnoozeOption.ONE_WEEK.until(today))
        assertEquals(LocalDate(2026, 3, 14), SnoozeOption.WEEKEND.until(today))
        assertEquals(LocalDate(2026, 3, 21), SnoozeOption.WEEKEND.until(LocalDate(2026, 3, 14))) // Saturday → next one
        assertEquals(LocalDate(2026, 3, 14), SnoozeOption.WEEKEND.until(LocalDate(2026, 3, 8))) // Sunday
    }

    @Test
    fun groupStatusUsesHangoutsWithEveryMember() {
        val friends = listOf(friend("ana"), friend("rui"), friend("zoe"))
        val ids = friends.map { it.id }
        val hangouts = listOf(
            Hangout("1", ids, at(2026, 2, 1)),
            Hangout("2", ids.take(2), at(2026, 3, 5)),
        )
        val group = FriendGroup("g", "Crew", "🎲", ids, meetEveryDays = 30)
        val status = Relationships.groupStatuses(listOf(group), friends, hangouts, today, TimeZone.UTC).single()
        assertEquals(at(2026, 2, 1), status.lastTogether)
        assertEquals(37, status.daysSince)
        assertTrue(status.isOverdue(today))
        assertEquals(3, status.members.size)

        val noCadence = Relationships.groupStatuses(listOf(group.copy(meetEveryDays = null)), friends, hangouts, today, TimeZone.UTC).single()
        assertNull(noCadence.dueDate)
        assertFalse(noCadence.isOverdue(today))

        val snoozed = Relationships.groupStatuses(
            listOf(group.copy(snoozedUntil = LocalDate(2026, 3, 20))),
            friends,
            hangouts,
            today,
            TimeZone.UTC,
        ).single()
        assertFalse(snoozed.isOverdue(today))
    }

    @Test
    fun overdueGroupsShowInAgendaAndNotifications() {
        val friends = listOf(friend("ana"), friend("rui"))
        val group = FriendGroup("g", "Crew", "🎲", friends.map { it.id }, meetEveryDays = 7)
        val agenda = AgendaBuilder.build(friends, emptyList(), emptyList(), today, TimeZone.UTC, groups = listOf(group))
        val item = agenda.single() as AgendaItem.GroupOverdue
        assertEquals("Crew", item.group.name)

        val planned = NotificationPlanner.plan(friends, emptyList(), emptyList(), at(2026, 3, 10), TimeZone.UTC, 9, groups = listOf(group))
        assertEquals(NotificationContent.GroupMeetupDue("g", "Crew", null, 7), planned.single().content)
        assertEquals("group:g:2026-03-11", planned.single().id)
    }

    @Test
    fun notificationPlannerUsesCircleCadence() {
        val friends = listOf(friend("ana") { copy(isCloseFriend = true, circle = Circle.CLOSE) })
        val planned = NotificationPlanner.plan(friends, emptyList(), emptyList(), at(2026, 3, 10), TimeZone.UTC, 9, rules = rules)
        val content = planned.single().content as NotificationContent.MeetupDue
        assertEquals(30, content.everyDays)
        assertEquals(FriendId("ana"), content.friendId)
    }
}
