package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlin.math.roundToInt

/**
 * A gentle 0–100 "how warm is this friendship right now" score. It's for sorting and a soft label —
 * never shown as a grade. Inputs are relative to the friend's own cadence (default 30 days), so a
 * quarterly friend seen two months ago is still warm.
 *
 * - 70 points: recency of the last contact of any kind, weighted by kind ([weight]: meetups and long chats count
 *   fully, a video call 80%, a call or an online game 60%, "said hi" 30%). The best weighted contact wins.
 * - 20 points: how many meetups in the last 90 days vs. what the cadence asks for.
 * - 10 points: contacts at a distance in the last 90 days, weighted the same way (4 full-weight ones = full).
 *
 * Contact at a distance always counts here, whatever the friend's "calls and chats count" switch says; that switch only
 * decides whether it resets the rhythm (decision "Logging · ways to keep in touch", issue #101).
 */
object Warmth {
    enum class Level { WARM, COOLING, FADING }

    data class Score(val value: Int, val level: Level)

    private const val WINDOW_DAYS = 90

    /**
     * How much one contact of [type] warms a friendship, from 0 to 1 (issue #101): being together and a long chat
     * most, then a video call, then a call or a game, and "said hi" least. Email, WhatsApp days and "other" sit with
     * calls, where every contact at a distance was before.
     */
    fun weight(type: InteractionType): Double = when (type) {
        InteractionType.MEETUP, InteractionType.LONG_CHAT -> 1.0
        InteractionType.VIDEO_CALL -> 0.8
        InteractionType.CALL, InteractionType.GAME, InteractionType.EMAIL, InteractionType.WHATSAPP, InteractionType.OTHER -> 0.6
        InteractionType.MESSAGE -> 0.3
    }

    /** [score], or null for someone whose keep-in-touch tracking is off (issue #115): they have no warmth at all. */
    fun scoreOrNull(
        friend: Friend,
        hangouts: List<Hangout>,
        interactions: List<Interaction>,
        cadenceDays: Int?,
        today: LocalDate,
        zone: TimeZone,
    ): Score? = if (friend.tracksKeepInTouch) score(friend, hangouts, interactions, cadenceDays, today, zone) else null

    fun score(
        friend: Friend,
        hangouts: List<Hangout>,
        interactions: List<Interaction>,
        cadenceDays: Int?,
        today: LocalDate,
        zone: TimeZone,
    ): Score {
        val expected = (cadenceDays ?: 30).coerceAtLeast(1)
        val meetups = hangouts.filter { friend.id in it.friendIds }.map { it.at.toLocalDate(zone) }
        val contacts = interactions.filter { it.friendId == friend.id && it.type != InteractionType.MEETUP }
            .map { it.at.toLocalDate(zone) to weight(it.type) }
            .filter { (date, _) -> date <= today }

        fun recency(date: LocalDate): Double = (1.0 - date.daysUntil(today).toDouble() / (2.0 * expected)).coerceIn(0.0, 1.0)
        val meetupRecency = meetups.filter { it <= today }.maxOrNull()?.let(::recency) ?: 0.0
        val contactRecency = contacts.maxOfOrNull { (date, w) -> recency(date) * w } ?: 0.0
        val recencyPart = maxOf(meetupRecency, contactRecency) * 70

        val inWindowMeetups = meetups.count { it <= today && it.daysUntil(today) <= WINDOW_DAYS }
        val wantedMeetups = (WINDOW_DAYS.toDouble() / expected).coerceAtLeast(1.0)
        val frequencyPart = (inWindowMeetups / wantedMeetups).coerceAtMost(1.0) * 20
        val contactWeight = contacts.filter { (date, _) -> date.daysUntil(today) <= WINDOW_DAYS }.sumOf { (_, w) -> w }
        val contactPart = (contactWeight / 4.0).coerceAtMost(1.0) * 10

        val value = (recencyPart + frequencyPart + contactPart).roundToInt().coerceIn(0, 100)
        val level = when {
            value >= 60 -> Level.WARM
            value >= 30 -> Level.COOLING
            else -> Level.FADING
        }
        return Score(value, level)
    }
}
