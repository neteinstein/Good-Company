package org.goodcompany.app.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.goodcompany.app.core.domain.model.CatchUpCounts
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.Interaction
import org.goodcompany.app.core.domain.model.InteractionType
import org.goodcompany.app.core.domain.usecase.Warmth
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
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

    private fun byOne(type: InteractionType, daysAgo: Int = 1) =
        Warmth.score(ana, emptyList(), listOf(Interaction(FriendId("ana"), type, at(2026, 3, 10).minusDays(daysAgo))), 30, today, TimeZone.UTC)

    @Test
    fun emailsWhatsAppGamesAndOtherCountLikeCalls() {
        val byCall = byOne(InteractionType.CALL)
        listOf(InteractionType.EMAIL, InteractionType.WHATSAPP, InteractionType.GAME, InteractionType.OTHER).forEach { type ->
            assertEquals(byCall, byOne(type), "$type")
        }
    }

    @Test
    fun eachKindIsWeighted_inPersonAndLongChatsMost_saidHiLeast() {
        // Issue #101: in person and long chat highest, then video, then call and game, "said hi" lightest.
        assertEquals(1.0, Warmth.weight(InteractionType.MEETUP))
        assertEquals(1.0, Warmth.weight(InteractionType.LONG_CHAT))
        assertTrue(Warmth.weight(InteractionType.VIDEO_CALL) < 1.0)
        assertTrue(Warmth.weight(InteractionType.CALL) < Warmth.weight(InteractionType.VIDEO_CALL))
        assertEquals(Warmth.weight(InteractionType.CALL), Warmth.weight(InteractionType.GAME))
        assertTrue(Warmth.weight(InteractionType.MESSAGE) < Warmth.weight(InteractionType.CALL))
        InteractionType.entries.forEach { assertTrue(Warmth.weight(it) > 0.0, "$it always adds to warmth") }

        val longChat = byOne(InteractionType.LONG_CHAT).value
        val video = byOne(InteractionType.VIDEO_CALL).value
        val call = byOne(InteractionType.CALL).value
        val game = byOne(InteractionType.GAME).value
        val saidHi = byOne(InteractionType.MESSAGE).value
        assertTrue(longChat > video, "long chat $longChat > video $video")
        assertTrue(video > call, "video $video > call $call")
        assertEquals(call, game)
        assertTrue(call > saidHi, "call $call > said hi $saidHi")
        assertTrue(saidHi > 0)
    }

    @Test
    fun aLongChatYesterdayIsAsWarmAsMeetingYesterday_forRecency() {
        val chat = byOne(InteractionType.LONG_CHAT)
        val met = Warmth.score(ana, seen(1), emptyList(), 30, today, TimeZone.UTC)
        // Same recency; the meetup still adds to the meetup frequency, the chat to contact at a distance.
        assertTrue(chat.value > 0 && met.value > 0)
        assertEquals(Warmth.Level.WARM, chat.level)
    }

    @Test
    fun remoteContactCountsWhateverTheCatchUpSwitchSays() {
        val off = ana.copy(catchUpCounts = CatchUpCounts.IN_PERSON_ONLY)
        val on = ana.copy(catchUpCounts = CatchUpCounts.ANY_CONTACT)
        val call = listOf(Interaction(FriendId("ana"), InteractionType.CALL, at(2026, 3, 9)))
        val a = Warmth.score(off, emptyList(), call, 30, today, TimeZone.UTC)
        assertTrue(a.value > 0)
        assertEquals(a, Warmth.score(on, emptyList(), call, 30, today, TimeZone.UTC))
    }

    @Test
    fun manySaidHisAddUpLessThanAsManyCalls() {
        fun of(type: InteractionType) = (1..6).map { Interaction(FriendId("ana"), type, at(2026, 3, 10).minusDays(it * 10)) }
        val hi = Warmth.score(ana, emptyList(), of(InteractionType.MESSAGE), 30, today, TimeZone.UTC)
        val calls = Warmth.score(ana, emptyList(), of(InteractionType.CALL), 30, today, TimeZone.UTC)
        assertTrue(hi.value < calls.value)
    }

    @Test
    fun otherFriendsDontCount() {
        val other = listOf(Hangout("x", listOf(FriendId("rui")), at(2026, 3, 9)))
        assertEquals(0, Warmth.score(ana, other, emptyList(), 30, today, TimeZone.UTC).value)
    }
}
