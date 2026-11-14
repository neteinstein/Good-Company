package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarmthTest {
    private val today = LocalDate(2026, 3, 10)
    private val ana = friend("ana")
    private fun seen(vararg days: Int) = days.mapIndexed { i, d -> Hangout("h$i", listOf(FriendId("ana")), at(2026, 3, 10).minusDays(d)) }

    private fun kotlin.time.Instant.minusDays(d: Int) = this - kotlin.time.Duration.parse("${d}d")

    @Test
    fun neverInTouchIsFading() {
        val s = Warmth.score(ana, emptyList(), emptyList(), 30, today, TimeZone.UTC)
        assertEquals(0, s.value)
        assertEquals(Warmth.Level.FADING, s.level)
    }

    @Test
    fun seenRecentlyAndOftenIsWarm() {
        val s = Warmth.score(ana, seen(1, 20, 45), emptyList(), 30, today, TimeZone.UTC)
        assertEquals(Warmth.Level.WARM, s.level)
        assertTrue(s.value >= 85, "was ${s.value}")
    }

    @Test
    fun relativeToCadence() {
        // Seen 50 days ago: cooling for a monthly friend, still warm for a quarterly one.
        val monthly = Warmth.score(ana, seen(50), emptyList(), 30, today, TimeZone.UTC)
        val quarterly = Warmth.score(ana, seen(50), emptyList(), 90, today, TimeZone.UTC)
        assertTrue(quarterly.value > monthly.value)
        assertEquals(Warmth.Level.FADING, monthly.level)
        assertEquals(Warmth.Level.WARM, quarterly.level)
    }

    @Test
    fun callsKeepItWarmerButLessThanMeetups() {
        val call = listOf(Interaction(FriendId("ana"), InteractionType.CALL, at(2026, 3, 9)))
        val byCall = Warmth.score(ana, emptyList(), call, 30, today, TimeZone.UTC)
        val byMeetup = Warmth.score(ana, seen(1), emptyList(), 30, today, TimeZone.UTC)
        assertTrue(byCall.value in 1 until byMeetup.value)
        assertEquals(Warmth.Level.COOLING, byCall.level)
    }

    @Test
    fun messagesEmailsAndVideoCallsCountLikeCalls() {
        val byCall = Warmth.score(
            ana,
            emptyList(),
            listOf(Interaction(FriendId("ana"), InteractionType.CALL, at(2026, 3, 9))),
            30,
            today,
            TimeZone.UTC,
        )
        listOf(InteractionType.MESSAGE, InteractionType.EMAIL, InteractionType.VIDEO_CALL, InteractionType.WHATSAPP).forEach { type ->
            val s = Warmth.score(ana, emptyList(), listOf(Interaction(FriendId("ana"), type, at(2026, 3, 9))), 30, today, TimeZone.UTC)
            assertEquals(byCall, s, "$type")
        }
    }

    @Test
    fun otherFriendsDontCount() {
        val other = listOf(Hangout("x", listOf(FriendId("rui")), at(2026, 3, 9)))
        assertEquals(0, Warmth.score(ana, other, emptyList(), 30, today, TimeZone.UTC).value)
    }
}
