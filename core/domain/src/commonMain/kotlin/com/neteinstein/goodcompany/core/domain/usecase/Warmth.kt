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
 * - 70 points: recency of the last contact of any kind (meetups count fully, calls/messages count 60%).
 * - 20 points: how many meetups in the last 90 days vs. what the cadence asks for.
 * - 10 points: calls/messages in the last 90 days (4+ = full).
 */
object Warmth {
    enum class Level { WARM, COOLING, FADING }

    data class Score(val value: Int, val level: Level)

    private const val WINDOW_DAYS = 90

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
        val contacts = interactions.filter { it.friendId == friend.id && it.type != InteractionType.MEETUP }.map { it.at.toLocalDate(zone) }

        fun recency(dates: List<LocalDate>): Double {
            val last = dates.filter { it <= today }.maxOrNull() ?: return 0.0
            return (1.0 - last.daysUntil(today).toDouble() / (2.0 * expected)).coerceIn(0.0, 1.0)
        }
        val recencyPart = maxOf(recency(meetups), recency(contacts) * 0.6) * 70

        fun inWindow(dates: List<LocalDate>) = dates.count { it <= today && it.daysUntil(today) <= WINDOW_DAYS }
        val wantedMeetups = (WINDOW_DAYS.toDouble() / expected).coerceAtLeast(1.0)
        val frequencyPart = (inWindow(meetups) / wantedMeetups).coerceAtMost(1.0) * 20
        val contactPart = (inWindow(contacts) / 4.0).coerceAtMost(1.0) * 10

        val value = (recencyPart + frequencyPart + contactPart).roundToInt().coerceIn(0, 100)
        val level = when {
            value >= 60 -> Level.WARM
            value >= 30 -> Level.COOLING
            else -> Level.FADING
        }
        return Score(value, level)
    }
}
