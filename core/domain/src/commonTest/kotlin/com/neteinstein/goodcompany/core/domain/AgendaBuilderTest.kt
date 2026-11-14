package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.friendIds
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.KidsAsPeople
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgendaBuilderTest {
    private val today = LocalDate(2026, 3, 10)

    @Test
    fun includesBirthdaysOnlyForFriendsYouCareAbout() {
        val friends = listOf(
            friend("ana") { copy(closeness = Closeness.CLOSE, birthday = Birthday(3, 20, 1990)) },
            friend("bob") { copy(notifyBirthday = false, birthday = Birthday(3, 21)) },
            friend("cat") { copy(birthday = Birthday(3, 22)) }, // on by default (F10)
        )
        val items = AgendaBuilder.build(friends, emptyList(), emptyList(), today, TimeZone.UTC)
        assertEquals(listOf("ana", "cat"), items.map { (it as AgendaItem.FriendBirthday).friend.id.value })
        assertEquals(36, (items[0] as AgendaItem.FriendBirthday).turning)
    }

    @Test
    fun includesKidsBirthdaysEventsAndOpenReminders() {
        val ana = friend("ana") {
            copy(
                closeness = Closeness.CLOSE,
                kids = listOf(Kid("k1", "Leo", Birthday(4, 1, 2020), listOf("dinosaurs"))),
                importantEvents = listOf(ImportantEvent("e1", "Surgery", LocalDate(2026, 3, 15))),
            )
        }
        val reminders = listOf(
            Reminder("r1", "Book tickets", listOf(FriendId("ana")), at(2026, 3, 12), createdAt = at(2026, 3, 1)),
            Reminder("r2", "Done one", emptyList(), at(2026, 3, 12), done = true, createdAt = at(2026, 3, 1)),
            Reminder("r3", "Someday", emptyList(), null, createdAt = at(2026, 3, 1)),
        )
        // An old-style kid becomes a person linked to Ana (F9); a close friend's kid still shows, with their parent.
        val (people, links) = KidsAsPeople.convert(listOf(ana), emptyList())
        val items = AgendaBuilder.build(people, reminders, emptyList(), today, TimeZone.UTC, links = links)
        assertEquals(3, items.size)
        assertIs<AgendaItem.ReminderDue>(items[0])
        assertEquals("Ana", (items[0] as AgendaItem.ReminderDue).friends.single().name)
        assertIs<AgendaItem.FriendEvent>(items[1])
        val kid = assertIs<AgendaItem.KidBirthday>(items[2])
        assertEquals(6, kid.turning)
        assertEquals("Ana", kid.parent.name)
        assertEquals(setOf(FriendId("ana"), FriendId("k1")), kid.friendIds)
    }

    @Test
    fun overdueReminderIsPinnedToToday() {
        val r = Reminder("r1", "Call", emptyList(), at(2026, 3, 1), createdAt = at(2026, 2, 1))
        val item = AgendaBuilder.build(emptyList(), listOf(r), emptyList(), today, TimeZone.UTC).single()
        assertEquals(today, item.date)
    }

    @Test
    fun overdueMeetupsAppearFirst() {
        val friends = listOf(
            // Answered on the last-seen card (issue #68): never-logged people with no answer aren't overdue.
            friend("ana") {
                copy(meetEveryDays = 7, closeness = Closeness.CLOSE, birthday = Birthday(3, 10), approxLastSeen = LocalDate(2026, 1, 1))
            },
        )
        val items = AgendaBuilder.build(friends, emptyList(), emptyList(), today, TimeZone.UTC)
        assertIs<AgendaItem.MeetupOverdue>(items[0])
        assertIs<AgendaItem.FriendBirthday>(items[1])
    }

    @Test
    fun itemsBeyondHorizonAreExcluded() {
        val friends = listOf(friend("ana") { copy(closeness = Closeness.CLOSE, birthday = Birthday(12, 1)) })
        assertTrue(AgendaBuilder.build(friends, emptyList(), emptyList(), today, TimeZone.UTC, horizonDays = 30).isEmpty())
    }
}
