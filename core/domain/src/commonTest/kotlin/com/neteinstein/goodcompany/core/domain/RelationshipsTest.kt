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
    fun neverMetFriendWithRuleIsDueToday() {
        val status = Relationships.meetupStatuses(
            listOf(friend("zoe") { copy(meetEveryDays = 14) }),
            hangouts,
            LocalDate(2026, 3, 10),
            TimeZone.UTC,
        ).single()
        assertEquals(LocalDate(2026, 3, 10), status.dueDate)
        assertNull(status.daysSinceLastSeen)
    }
}
