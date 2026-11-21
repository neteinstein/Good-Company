package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.Friend
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * How often you want to see each friend. A per-friend "meet every N days" wins; otherwise a real friend's
 * [Closeness] gives the default (Inner weekly, Close monthly, Friends quarterly — configurable in Settings).
 * Acquaintances have none unless [acquaintanceDays] is set in Settings (issue #69), real friend or not.
 */
data class CadenceRules(
    val innerDays: Int = 7,
    val closeDays: Int = 30,
    val goodDays: Int = 90,
    val acquaintanceDays: Int? = null,
) {
    /** The default rhythm of a level; null for Acquaintances while Settings has none. */
    fun daysFor(closeness: Closeness): Int? = when (closeness) {
        Closeness.INNER -> innerDays
        Closeness.CLOSE -> closeDays
        Closeness.GOOD -> goodDays
        Closeness.ACQUAINTANCE -> acquaintanceDays?.takeIf { it > 0 }
    }

    /**
     * Null for someone with no rhythm: their custom rhythm is Off ([Friend.RHYTHM_OFF], issue #139), or it follows a
     * level that has none, and always while keep-in-touch tracking is off (issue #115). A custom rhythm wins over the
     * closeness one; null [Friend.meetEveryDays] follows closeness.
     */
    fun cadenceFor(friend: Friend): Int? = when {
        !friend.tracksKeepInTouch -> null
        friend.rhythmOff -> null
        else -> friend.customRhythm ?: levelDays(friend)
    }

    fun sourceFor(friend: Friend): Source? = when {
        !friend.tracksKeepInTouch || friend.rhythmOff -> null
        friend.customRhythm != null -> Source.CUSTOM
        levelDays(friend) != null -> Source.CLOSENESS
        else -> null
    }

    /**
     * The rhythm Warmth measures against (issue #139): like [cadenceFor], but a custom Off still uses the closeness
     * rhythm, so turning catch-ups off doesn't change how warm someone reads.
     */
    fun warmthDaysFor(friend: Friend): Int? =
        if (!friend.tracksKeepInTouch) null else friend.customRhythm ?: levelDays(friend)

    /** The rhythm a person's closeness gives them (what "From closeness" means on their page); null when it has none. */
    fun levelDays(friend: Friend): Int? =
        daysFor(friend.closeness)

    enum class Source { CUSTOM, CLOSENESS }

    companion object {
        fun from(settings: AppSettings) = CadenceRules(
            settings.innerCircleDays,
            settings.closeCircleDays,
            settings.goodFriendsDays,
            settings.acquaintanceDays,
        )
    }
}

/** Snooze choices for a catch-up nudge. */
enum class SnoozeOption {
    ONE_WEEK,

    /** Until the next Saturday (strictly after today). */
    WEEKEND,
    ;

    fun until(today: LocalDate): LocalDate = when (this) {
        ONE_WEEK -> today.plus(7, DateTimeUnit.DAY)
        WEEKEND -> {
            val daysToSaturday = (DayOfWeek.SATURDAY.ordinal - today.dayOfWeek.ordinal + 7) % 7
            today.plus(if (daysToSaturday == 0) 7 else daysToSaturday, DateTimeUnit.DAY)
        }
    }
}
