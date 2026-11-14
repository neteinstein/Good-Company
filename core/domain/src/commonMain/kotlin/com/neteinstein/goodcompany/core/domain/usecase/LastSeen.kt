package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
            .filter { f -> f.isCloseFriend || f.meetEveryDays?.takeIf { it > 0 } != null }
            .filter { f -> f.id !in seen && !(f.contactCountsAsCatchUp && f.id in contacted) }
            .sortedWith(compareBy<Friend> { it.closeness.ordinal }.thenBy { it.displayName.lowercase() })
    }
}

/** What Today's last-seen card shows: who is left to ask, or nothing while "Not now" hides it for the day. */
data class LastSeenCard(val pending: List<Friend>, val hiddenToday: Boolean) {
    /** The person the card asks about now; null when there is no one or the card is hidden. */
    val current: Friend? get() = if (hiddenToday) null else pending.firstOrNull()
}

class ObserveLastSeenCardUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<LastSeenCard> =
        combine(friends.friends, hangouts.hangouts, interactions.interactions, settings.settings) { fs, hs, ints, s ->
            LastSeenCard(
                pending = LastSeenQuestions.pending(fs, hs, ints, CadenceRules.from(s)),
                hiddenToday = s.lastSeenCardHiddenOn?.let { it >= clock.today() } ?: false,
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

/** "Not now": hides the last-seen card until tomorrow. */
class HideLastSeenCardUseCase(private val settings: SettingsRepository, private val clock: AppClock) {
    suspend operator fun invoke() = settings.update { it.copy(lastSeenCardHiddenOn = clock.today()) }
}
