package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.PersonTimeline
import com.neteinstein.goodcompany.core.domain.usecase.Timeline
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.TogetherItem
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Issue #117: the Together timeline: upcoming above Today (soonest last), every dated thing below, 12 then Show all. */
class PersonTimelineTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 3, 10)
    private val ana = FriendId("ana")
    private val created = at(2026, 1, 1)

    private val person = friend("ana") {
        copy(
            birthday = Birthday(3, 25, 1990),
            importantEvents = listOf(
                ImportantEvent("soon", "Surgery", LocalDate(2026, 3, 20)),
                ImportantEvent("far", "Wedding", LocalDate(2026, 6, 1)),
                ImportantEvent("done", "Moved", LocalDate(2026, 2, 1)),
            ),
        )
    }
    private val hangout = Hangout("h", listOf(ana), at(2026, 3, 5, 19))
    private val plans = listOf(
        Plan("next", listOf(ana), "Lunch", LocalDate(2026, 3, 12), createdAt = created),
        Plan("later", listOf(ana), "Hike", LocalDate(2026, 3, 30), createdAt = created),
        Plan("off", listOf(ana), "Cinema", LocalDate(2026, 2, 20), status = PlanStatus.CANCELLED, createdAt = created),
        Plan("went", listOf(ana), "Dinner", LocalDate(2026, 3, 5), status = PlanStatus.HAPPENED, createdAt = created),
    )
    private val reminders = listOf(
        Reminder("open", "Book tickets", listOf(ana), at(2026, 3, 11, 9), createdAt = created),
        Reminder("closed", "Send photos", listOf(ana), at(2026, 3, 1, 9), done = true, createdAt = created, completedAt = at(2026, 3, 2, 10)),
    )
    private val share = Attachment("a", friendId = ana, kind = AttachmentKind.TEXT, text = "a poem", at = at(2026, 2, 10))

    private fun build(shown: Int = PersonTimeline.SHOWN) = PersonTimeline.build(
        person,
        Timeline.build(ana, listOf(person), listOf(hangout), emptyList(), emptyList(), zone),
        plans,
        reminders,
        listOf(share),
        today,
        zone,
        shown,
    )

    @Test
    fun upcoming_isPlansRemindersBirthdayAndMomentsInTheNext30Days_soonestLast() {
        val keys = build().upcoming.map { it.key }
        assertEquals(listOf("plan-later", "birthday-2026-03-25", "moment-soon", "plan-next", "reminder-open"), keys)
    }

    @Test
    fun past_isEveryDatedThing_newestFirst_andAHappenedPlanShowsAsItsMeetup() {
        val past = build().past
        assertEquals(
            listOf("hangout-h", "reminder-closed", "plan-off", "share-a", "moment-done"),
            past.filter { it !is TogetherItem.Birthday }.map { it.key },
        )
        // Last year's birthday shows too: dated, so it's on the timeline.
        assertTrue(past.any { it is TogetherItem.Birthday && it.date == LocalDate(2025, 3, 25) && it.age == 35 })
        assertTrue(past.none { it.key == "plan-went" })
        assertIs<TogetherItem.DoneReminder>(past.first { it.key == "reminder-closed" }).also { assertEquals(at(2026, 3, 2, 10), it.at) }
    }

    @Test
    fun anUndatedOpenReminderSitsAtTheTop() {
        val undated = Reminder("any", "Ask about the job", listOf(ana), null, createdAt = created)
        val t = PersonTimeline.build(person, emptyList(), emptyList(), listOf(undated), emptyList(), today, zone)
        assertEquals("reminder-any", t.upcoming.first().key)
    }

    @Test
    fun aShareAlreadyOnAMeetupShowsOnce() {
        val logged = share.copy(countsAsInteraction = true, logId = "h")
        val timeline = Timeline.build(ana, listOf(person), listOf(hangout), emptyList(), emptyList(), zone, listOf(logged))
        val t = PersonTimeline.build(person, timeline, emptyList(), emptyList(), listOf(logged), today, zone)
        assertEquals(1, t.past.count { it is TogetherItem.Entry })
        assertEquals("a", assertIs<TimelineEntry.Meetup>((t.past.first { it is TogetherItem.Entry } as TogetherItem.Entry).entry).attachment?.id)
    }

    @Test
    fun twelveThenShowAll() {
        val many = (1..20).map { Hangout("m$it", listOf(ana), at(2026, 1, it)) }
        val timeline = Timeline.build(ana, listOf(person), many, emptyList(), emptyList(), zone)
        val t = PersonTimeline.build(
            person.copy(birthday = null, importantEvents = emptyList()),
            timeline,
            emptyList(),
            emptyList(),
            emptyList(),
            today,
            zone,
        )
        assertEquals(12, t.past.size)
        assertTrue(t.hasMore)
        assertEquals("hangout-m20", t.past.first().key)
        val few = PersonTimeline.build(
            person.copy(birthday = null, importantEvents = emptyList()),
            timeline.take(12),
            emptyList(),
            emptyList(),
            emptyList(),
            today,
            zone,
        )
        assertFalse(few.hasMore)
    }

    @Test
    fun pastBirthdaysGoBackToTheOldestEntry() {
        val old = Hangout("old", listOf(ana), at(2023, 6, 1))
        val timeline = Timeline.build(ana, listOf(person), listOf(old), emptyList(), emptyList(), zone)
        val t = PersonTimeline.build(person.copy(importantEvents = emptyList()), timeline, emptyList(), emptyList(), emptyList(), today, zone)
        assertEquals(
            listOf(LocalDate(2025, 3, 25), LocalDate(2024, 3, 25)),
            t.past.filterIsInstance<TogetherItem.Birthday>().map { it.date },
        )
    }
}
