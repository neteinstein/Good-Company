package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * An answer on Today's "When did you last see…?" card (issue #68) and the approximate date it stores, in days before
 * today (owner decision 2026-11-14). [DONT_KNOW] starts the rhythm today.
 */
enum class LastSeenAnswer(val daysAgo: Int) {
    THIS_WEEK(3),
    THIS_MONTH(15),
    FEW_MONTHS(90),
    OVER_A_YEAR(400),
    DONT_KNOW(0),
    ;

    fun dateFor(today: LocalDate): LocalDate = today.minus(daysAgo, DateTimeUnit.DAY)
}

/** Who the last-seen card asks about (issue #68). */
object LastSeenQuestions {
    /**
     * People with a catch-up rhythm whose rhythm hasn't started: nothing logged that counts for them (a meetup, or any
     * contact when calls and chats count) and no answer yet. Paused people and Acquaintances (issue #69, even with a
     * rhythm from Settings) are left out. Closest first, then by name.
     */
    fun pending(
        friends: List<Friend>,
        hangouts: List<Hangout>,
        interactions: List<Interaction>,
        rules: CadenceRules,
    ): List<Friend> {
        val seen = Relationships.lastSeen(hangouts).keys
        val contacted = interactions.mapTo(HashSet()) { it.friendId }
        return friends
            .filter { f -> !f.catchUpPaused && f.approxLastSeen == null && rules.cadenceFor(f) != null }
            // Acquaintances are asked only for a rhythm set on their page, never for the Settings default (#69). Since
            // #86 everyone without a level is one, so this keeps asking whoever was asked before.
            .filter { f -> f.isCloseFriend || f.customRhythm != null }
            .filter { f -> f.id !in seen && !(f.contactCountsAsCatchUp && f.id in contacted) }
            .sortedWith(compareBy<Friend> { it.closeness.ordinal }.thenBy { it.displayName.lowercase() })
    }

    /**
     * Circles the card asks "When did the circle last meet?" about (issue #150, decision 2026-11-21): their reminder is
     * on, they have members, no meetup counts for them yet ([Relationships.lastCircleMeetup], the quorum) and there is
     * no answer yet. By name.
     */
    fun pendingCircles(groups: List<FriendGroup>, hangouts: List<Hangout>): List<FriendGroup> = groups
        .filter { g -> (g.meetEveryDays ?: 0) > 0 && g.memberIds.isNotEmpty() && g.approxLastMet == null }
        .filter { g -> Relationships.lastCircleMeetup(hangouts, g.memberIds.toSet()) == null }
        .sortedBy { it.name.lowercase() }
}

/**
 * What Today's last-seen card shows: who is left to ask (people first, then circles, issue #150), or nothing while
 * "Not now" hides it for the day.
 */
data class LastSeenCard(val pending: List<Friend>, val hiddenToday: Boolean, val pendingCircles: List<FriendGroup> = emptyList()) {
    /** The person the card asks about now; null when there is no one or the card is hidden. */
    val current: Friend? get() = if (hiddenToday) null else pending.firstOrNull()

    /** The circle the card asks about now: only once no person is left to ask. */
    val currentCircle: FriendGroup? get() = if (hiddenToday || pending.isNotEmpty()) null else pendingCircles.firstOrNull()

    /** Everyone and every circle still to ask. */
    val pendingCount: Int get() = pending.size + pendingCircles.size
}

class ObserveLastSeenCardUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val groups: GroupRepository,
) {
    operator fun invoke(): Flow<LastSeenCard> =
        combine(friends.friends, hangouts.hangouts, interactions.interactions, settings.settings, groups.groups) { fs, hs, ints, s, gs ->
            LastSeenCard(
                pending = LastSeenQuestions.pending(fs, hs, ints, CadenceRules.from(s)),
                hiddenToday = s.lastSeenCardHiddenOn?.let { it >= clock.today() } ?: false,
                pendingCircles = LastSeenQuestions.pendingCircles(gs, hs),
            )
        }
}

/** Stores an answer as the person's approximate last-seen date; [undo] puts back what was there before. */
class AnswerLastSeenUseCase(private val friends: FriendRepository, private val clock: AppClock) {
    /** Returns the date stored, or null when the person is gone. */
    suspend operator fun invoke(id: FriendId, answer: LastSeenAnswer): LocalDate? {
        val f = friends.get(id) ?: return null
        val date = answer.dateFor(clock.today())
        friends.upsert(f.copy(approxLastSeen = date))
        return date
    }

    suspend fun undo(id: FriendId, previous: LocalDate? = null) {
        val f = friends.get(id) ?: return
        friends.upsert(f.copy(approxLastSeen = previous))
    }
}

/**
 * Stores an answer to "When did the circle last meet?" (issue #150) as the circle's approximate last meetup, with the
 * same answers as people. It only starts the circle's rhythm: no meetup, nothing on a timeline. [undo] puts back what
 * was there before.
 */
class AnswerLastMetUseCase(private val groups: GroupRepository, private val clock: AppClock) {
    /** Returns the date stored, or null when the circle is gone. */
    suspend operator fun invoke(id: String, answer: LastSeenAnswer): LocalDate? {
        val g = groups.groups.first().firstOrNull { it.id == id } ?: return null
        val date = answer.dateFor(clock.today())
        groups.upsert(g.copy(approxLastMet = date))
        return date
    }

    suspend fun undo(id: String, previous: LocalDate? = null) {
        val g = groups.groups.first().firstOrNull { it.id == id } ?: return
        groups.upsert(g.copy(approxLastMet = previous))
    }
}

/** "Not now": hides the last-seen card until tomorrow. */
class HideLastSeenCardUseCase(private val settings: SettingsRepository, private val clock: AppClock) {
    suspend operator fun invoke() = settings.update { it.copy(lastSeenCardHiddenOn = clock.today()) }
}
