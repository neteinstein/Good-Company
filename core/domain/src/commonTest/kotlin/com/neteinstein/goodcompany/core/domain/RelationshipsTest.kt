package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelationshipsTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val hangouts = listOf(
        Hangout("1", listOf(ana), at(2026, 1, 1)),
        Hangout("2", listOf(ana, rui), at(2026, 2, 1)),
        Hangout("3", listOf(rui), at(2026, 3, 1)),
    )

    @Test
    fun lastSeenTakesTheLatestHangoutPerFriend() {
        val seen = Relationships.lastSeen(hangouts)
        assertEquals(at(2026, 2, 1), seen[ana])
        assertEquals(at(2026, 3, 1), seen[rui])
    }

    @Test
    fun lastContactConsidersCallsAndMessages() {
        val contact = Relationships.lastContact(hangouts, listOf(Interaction(ana, InteractionType.CALL, at(2026, 2, 20))))
        assertEquals(at(2026, 2, 20), contact[ana])
    }

    @Test
    fun newContactTypesResetTheRhythmOnlyWithTheSwitchOn() {
        val email = listOf(Interaction(ana, InteractionType.EMAIL, at(2026, 3, 8)), Interaction(ana, InteractionType.VIDEO_CALL, at(2026, 3, 5)))
        val today = LocalDate(2026, 3, 10)
        val off = Relationships.meetupStatuses(
            listOf(
                friend("ana") {
                    copy(meetEveryDays = 30)
                },
            ),
            hangouts,
            today,
            TimeZone.UTC,
            interactions = email,
        )
        assertEquals(LocalDate(2026, 2, 1), off.single().lastSeenDate)
        val on = Relationships.meetupStatuses(
            listOf(friend("ana") { copy(meetEveryDays = 30, contactCountsAsCatchUp = true) }),
            hangouts,
            today,
            TimeZone.UTC,
            interactions = email,
        )
        assertEquals(LocalDate(2026, 3, 8), on.single().lastSeenDate)
        assertFalse(on.single().isOverdue(today))
    }

    @Test
    fun hangoutsWithAllRequiresEveryoneSelected() {
        assertEquals(listOf("2"), Relationships.hangoutsWithAll(hangouts, setOf(ana, rui)).map { it.id })
        assertEquals(listOf("2", "1"), Relationships.hangoutsWithAll(hangouts, setOf(ana)).map { it.id })
        assertEquals(3, Relationships.hangoutsWithAll(hangouts, emptySet()).size)
    }

    @Test
    fun meetupStatusIsDueEveryNDaysAfterLastSeen() {
        val friends = listOf(friend("ana") { copy(meetEveryDays = 30) }, friend("rui"))
        val status = Relationships.meetupStatuses(friends, hangouts, LocalDate(2026, 3, 10), TimeZone.UTC).single()
        assertEquals(LocalDate(2026, 3, 3), status.dueDate)
        assertEquals(37, status.daysSinceLastSeen)
        assertTrue(status.isOverdue(LocalDate(2026, 3, 10)))
        assertFalse(status.isOverdue(LocalDate(2026, 3, 2)))
    }

    @Test
    fun neverLoggedFriendWithRuleIsNotOverdue() {
        // Issue #68: nothing logged and no last-seen answer → the rhythm hasn't started, so no status at all.
        val statuses = Relationships.meetupStatuses(
            listOf(friend("zoe") { copy(meetEveryDays = 14) }),
            hangouts,
            LocalDate(2026, 3, 10),
            TimeZone.UTC,
        )
        assertTrue(statuses.isEmpty())
    }

    @Test
    fun theApproximateLastSeenStartsTheRhythm_withoutShowingAsSeen() {
        val status = Relationships.meetupStatuses(
            listOf(friend("zoe") { copy(meetEveryDays = 14, approxLastSeen = LocalDate(2026, 2, 23)) }),
            hangouts,
            LocalDate(2026, 3, 10),
            TimeZone.UTC,
        ).single()
        assertEquals(LocalDate(2026, 3, 9), status.dueDate)
        assertTrue(status.isOverdue(LocalDate(2026, 3, 10)))
        // Hidden (owner decision 2026-11-14): it isn't a meetup, so nothing reads as "last seen".
        assertNull(status.lastSeenDate)
        assertNull(status.daysSinceLastSeen)

        // "I don't know" = today: due one rhythm from now.
        val unknown = Relationships.meetupStatuses(
            listOf(friend("zoe") { copy(meetEveryDays = 14, approxLastSeen = LocalDate(2026, 3, 10)) }),
            hangouts,
            LocalDate(2026, 3, 10),
            TimeZone.UTC,
        ).single()
        assertEquals(LocalDate(2026, 3, 24), unknown.dueDate)
    }

    @Test
    fun aRealMeetupWinsOverTheApproximateLastSeen() {
        // Rui was last seen on 1 March; an answer of "over a year" (or a later guess) doesn't move that.
        for (guess in listOf(LocalDate(2025, 2, 3), LocalDate(2026, 3, 7))) {
            val status = Relationships.meetupStatuses(
                listOf(friend("rui") { copy(meetEveryDays = 14, approxLastSeen = guess) }),
                hangouts,
                LocalDate(2026, 3, 10),
                TimeZone.UTC,
            ).single()
            assertEquals(LocalDate(2026, 3, 1), status.lastSeenDate)
            assertEquals(LocalDate(2026, 3, 15), status.dueDate)
        }
    }

    @Test
    fun monthStatsCountsThisMonthOnly() {
        val hs = hangouts + listOf(
            Hangout("4", listOf(ana), at(2026, 3, 5), com.neteinstein.goodcompany.core.domain.model.Place("Café")),
            Hangout("5", listOf(ana, rui), at(2026, 3, 8), com.neteinstein.goodcompany.core.domain.model.Place("Café")),
            Hangout("6", listOf(rui), at(2026, 3, 20)), // future
        )
        val stats = Relationships.monthStats(hs, LocalDate(2026, 3, 10), TimeZone.UTC)
        assertEquals(3, stats.meetups) // 1st, 5th, 8th
        assertEquals(2, stats.friendsSeen)
        assertEquals("Café", stats.topPlace)
    }
}
