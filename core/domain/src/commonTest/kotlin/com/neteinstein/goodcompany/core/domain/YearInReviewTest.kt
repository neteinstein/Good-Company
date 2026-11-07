package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.YearInReviewBuilder
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YearInReviewTest {
    private val friends = listOf(friend("ana"), friend("rui"), friend("zoe"))
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val zoe = FriendId("zoe")

    @Test
    fun summarisesTheYear() {
        val hangouts = listOf(
            Hangout("old", listOf(ana), at(2025, 12, 20)),
            Hangout("1", listOf(ana, rui), at(2026, 1, 3), Place("Café")), // Sat
            Hangout("2", listOf(ana), at(2026, 1, 10), Place("Café")), // Sat
            Hangout("3", listOf(ana, rui, zoe), at(2026, 1, 17), Place("Park")), // Sat
            Hangout("4", listOf(rui), at(2026, 3, 2)), // Mon
        )
        val reminders = listOf(
            Reminder("r1", "x", dueAt = at(2026, 1, 5), done = true, createdAt = at(2026, 1, 1)),
            Reminder("r2", "y", dueAt = at(2026, 1, 5), done = false, createdAt = at(2026, 1, 1)),
            Reminder("r3", "z", dueAt = at(2025, 1, 5), done = true, createdAt = at(2025, 1, 1)),
        )
        val r = YearInReviewBuilder.build(2026, friends, hangouts, reminders, TimeZone.UTC)
        assertEquals(4, r.meetups)
        assertEquals(3, r.friendsSeen)
        assertEquals(listOf("ana" to 3, "rui" to 3, "zoe" to 1), r.topFriends.map { it.first.id.value to it.second })
        assertEquals("Café" to 2, r.topPlaces.first())
        assertEquals(Month.JANUARY to 3, r.busiestMonth)
        assertEquals(DayOfWeek.SATURDAY, r.favouriteWeekday)
        assertEquals("3", r.biggestHangout?.id)
        assertEquals(3, r.biggestHangoutFriends.size)
        assertEquals(3, r.longestWeekStreak)
        assertEquals(listOf("Rui", "Zoe"), r.newFaces.map { it.name }) // Ana was first met in 2025
        assertEquals(1, r.remindersDone)
    }

    @Test
    fun emptyYearAndDefaultYear() {
        val empty = YearInReviewBuilder.build(2026, friends, emptyList(), emptyList(), TimeZone.UTC)
        assertTrue(empty.isEmpty)
        val hs = listOf(Hangout("x", listOf(ana), at(2025, 6, 1)))
        assertEquals(2025, YearInReviewBuilder.defaultYear(LocalDate(2026, 1, 10), hs, TimeZone.UTC))
        assertEquals(2026, YearInReviewBuilder.defaultYear(LocalDate(2026, 2, 10), hs, TimeZone.UTC))
        assertEquals(2026, YearInReviewBuilder.defaultYear(LocalDate(2026, 1, 10), emptyList(), TimeZone.UTC))
    }

    @Test
    fun weekStreak() {
        val d = listOf(LocalDate(2026, 1, 5), LocalDate(2026, 1, 11), LocalDate(2026, 1, 12), LocalDate(2026, 2, 2))
        assertEquals(2, YearInReviewBuilder.longestWeekStreak(d)) // weeks of Jan 5 and Jan 12
        assertEquals(0, YearInReviewBuilder.longestWeekStreak(emptyList()))
    }
}
