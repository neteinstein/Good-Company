package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenAnswer
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenQuestions
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #68: never-logged people aren't overdue; the "When did you last see…?" card starts their rhythm. */
class LastSeenTest {
    private val today = LocalDate(2026, 3, 10)

    @Test
    fun answersStoreTheOwnersApproximateDates() {
        assertEquals(LocalDate(2026, 3, 7), LastSeenAnswer.THIS_WEEK.dateFor(today))
        assertEquals(LocalDate(2026, 2, 23), LastSeenAnswer.THIS_MONTH.dateFor(today))
        assertEquals(LocalDate(2025, 12, 10), LastSeenAnswer.FEW_MONTHS.dateFor(today))
        assertEquals(LocalDate(2025, 2, 3), LastSeenAnswer.OVER_A_YEAR.dateFor(today))
        assertEquals(today, LastSeenAnswer.DONT_KNOW.dateFor(today))
    }

    @Test
    fun onlyPeopleWithARhythmThatHasntStartedAreAsked_closestFirst() {
        val friends = listOf(
            friend("zed") { copy(meetEveryDays = 30) },
            friend("good") { copy(closeness = Closeness.GOOD) },
            friend("inner") { copy(closeness = Closeness.INNER) },
            friend("seen") { copy(meetEveryDays = 7) },
            friend("answered") { copy(meetEveryDays = 7, approxLastSeen = today) },
            friend("paused") { copy(meetEveryDays = 7, catchUpPaused = true) },
            friend("norhythm"),
            friend("chatty") { copy(meetEveryDays = 7, contactCountsAsCatchUp = true) },
            friend("called") { copy(meetEveryDays = 7) },
        )
        val hangouts = listOf(Hangout("h", listOf(FriendId("seen")), at(2026, 3, 1)))
        val chats = listOf(
            Interaction(FriendId("chatty"), InteractionType.WHATSAPP, at(2026, 3, 9)),
            // A call doesn't count for someone whose calls don't count as catching up.
            Interaction(FriendId("called"), InteractionType.CALL, at(2026, 3, 9)),
        )
        val asked = LastSeenQuestions.pending(friends, hangouts, chats, CadenceRules())
        assertEquals(listOf("inner", "good", "called", "zed"), asked.map { it.id.value })
    }

    @Test
    fun answering_movesToTheNextPerson_startsTheRhythm_andUndoAsksAgain() = runTest {
        val g = TestGraph(listOf(friend("ana") { copy(meetEveryDays = 30) }, friend("bob") { copy(meetEveryDays = 30) }))
        assertEquals(FriendId("ana"), g.observeLastSeenCard().first().current?.id)
        assertTrue(g.observeMeetups().first().isEmpty())

        assertEquals(LocalDate(2026, 2, 23), g.answerLastSeen(FriendId("ana"), LastSeenAnswer.THIS_MONTH))
        assertEquals(LocalDate(2026, 2, 23), g.friendRepo.get(FriendId("ana"))?.approxLastSeen)
        val card = g.observeLastSeenCard().first()
        assertEquals(listOf(FriendId("bob")), card.pending.map { it.id })
        val status = g.observeMeetups().first().single()
        assertEquals(LocalDate(2026, 3, 25), status.dueDate)
        assertFalse(status.isOverdue(today))

        g.answerLastSeen.undo(FriendId("ana"))
        assertNull(g.friendRepo.get(FriendId("ana"))?.approxLastSeen)
        assertEquals(FriendId("ana"), g.observeLastSeenCard().first().current?.id)
        // Someone who has gone isn't answered.
        assertNull(g.answerLastSeen(FriendId("gone"), LastSeenAnswer.THIS_WEEK))
    }

    @Test
    fun notNow_hidesTheCardUntilTomorrow() = runTest {
        val g = TestGraph(listOf(friend("ana") { copy(meetEveryDays = 30) }))
        g.hideLastSeenCard()
        assertEquals(today, g.settingsRepo.settings.first().lastSeenCardHiddenOn)
        val hidden = g.observeLastSeenCard().first()
        assertTrue(hidden.hiddenToday)
        assertNull(hidden.current)
        assertEquals(1, hidden.pending.size)

        g.clock.instant = at(2026, 3, 11)
        assertEquals(FriendId("ana"), g.observeLastSeenCard().first().current?.id)
    }

    @Test
    fun aFreshImportShowsNoWallOfOverduePeople() {
        val imported = (1..40).map { friend("p$it") { copy(closeness = Closeness.CLOSE) } }
        assertTrue(Relationships.meetupStatuses(imported, emptyList(), today, TimeZone.UTC).none { it.isOverdue(today) })
        assertEquals(40, LastSeenQuestions.pending(imported, emptyList(), emptyList(), CadenceRules()).size)
    }
}
