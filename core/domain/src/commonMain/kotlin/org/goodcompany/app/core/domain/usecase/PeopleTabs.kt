package org.goodcompany.app.core.domain.usecase

import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import kotlin.time.Instant

/**
 * The People tab's tabs (issue #120): everyone, friends or family. Family is anyone with a family relation to you,
 * which is exactly the built-in Family circle ([FamilyCircle.members]); Friends is everyone else.
 */
enum class PeopleTab {
    ALL,
    FRIENDS,
    FAMILY,
    ;

    fun includes(friend: Friend): Boolean = when (this) {
        ALL -> true
        FAMILY -> friend.isFamily
        FRIENDS -> !friend.isFamily
    }
}

/** How the People list orders people inside one closeness group (issue #120). */
object PeopleOrder {
    /** Most recent interaction first ([lastOf], any logged contact); people never contacted last, A–Z. */
    fun <T> recentFirst(items: List<T>, friendOf: (T) -> Friend, lastOf: (T) -> Instant?): List<T> {
        val (contacted, never) = items.partition { lastOf(it) != null }
        return contacted.sortedWith(
            compareByDescending<T> { lastOf(it) }.thenBy { friendOf(it).displayName.lowercase() },
        ) + never.sortedBy { friendOf(it).displayName.lowercase() }
    }
}

/** The circle dots after a name on People (issue #120): at most [MAX] circles in order, then how many more. */
data class CircleDots(val shown: List<FriendGroup>, val more: Int) {
    companion object {
        const val MAX = 4

        val None = CircleDots(emptyList(), 0)

        /** [circles] (in the order given) that [id] belongs to. */
        fun of(id: FriendId, circles: List<FriendGroup>, max: Int = MAX): CircleDots {
            val mine = circles.filter { id in it.memberIds }
            return CircleDots(mine.take(max), (mine.size - max).coerceAtLeast(0))
        }

        /** [of] for everyone in one pass; people in no circle are left out. */
        fun byPerson(circles: List<FriendGroup>, max: Int = MAX): Map<FriendId, CircleDots> =
            circles.flatMap { it.memberIds }.distinct().associateWith { of(it, circles, max) }
    }
}

/** "Got it" on the People legend tip (issue #120): it doesn't show again on this device. */
class DismissPeopleLegendUseCase(private val settings: org.goodcompany.app.core.domain.repository.SettingsRepository) {
    suspend operator fun invoke() = settings.update { it.copy(peopleLegendSeen = true) }
}
