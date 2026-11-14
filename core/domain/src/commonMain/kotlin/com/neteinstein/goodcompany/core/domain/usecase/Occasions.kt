package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

/** A friend who's good company for an occasion, with why they're suggested first. */
data class OccasionMatch(val friend: Friend, val daysSinceLastSeen: Int?, val catchUpDue: Boolean)

/**
 * "Who's free for a weekday lunch?" Friends tagged with the occasion (paused ones left out): those due a catch-up
 * first, then whoever you haven't seen for the longest. Suggestions only: the user always decides.
 */
object OccasionMatcher {
    fun match(
        occasion: Occasion,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        interactions: List<Interaction>,
        today: LocalDate,
        zone: TimeZone,
        rules: CadenceRules = CadenceRules(),
    ): List<OccasionMatch> {
        val tagged = friends.filter { occasion in it.occasions && !it.catchUpPaused }
        val due = Relationships.meetupStatuses(tagged, hangouts, today, zone, rules, interactions)
            .filter { it.isOverdue(today) }.map { it.friend.id }.toSet()
        val seen = Relationships.lastSeen(hangouts)
        return tagged.map { f -> OccasionMatch(f, seen[f.id]?.toLocalDate(zone)?.daysUntil(today), f.id in due) }
            .sortedWith(
                compareByDescending<OccasionMatch> { it.catchUpDue }
                    .thenByDescending { it.daysSinceLastSeen ?: Int.MAX_VALUE }
                    .thenBy { it.friend.displayName.lowercase() },
            )
    }
}

class ObserveOccasionMatchesUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    /** For every occasion that has at least one tagged friend. */
    operator fun invoke(): Flow<Map<Occasion, List<OccasionMatch>>> =
        combine(friends.friends, hangouts.hangouts, interactions.interactions, settings.settings) { fs, hs, ints, s ->
            Occasion.entries.associateWith { o ->
                OccasionMatcher.match(o, fs, hs, ints, clock.today(), clock.timeZone, CadenceRules.from(s))
            }.filterValues { it.isNotEmpty() }
        }
}
