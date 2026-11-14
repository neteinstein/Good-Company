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
 * [Closeness] gives the default (Inner weekly, Close monthly, Good quarterly — configurable in Settings).
 */
data class CadenceRules(val innerDays: Int = 7, val closeDays: Int = 30, val goodDays: Int = 90) {
    fun daysFor(closeness: Closeness): Int = when (closeness) {
        Closeness.INNER -> innerDays
        Closeness.CLOSE -> closeDays
        Closeness.GOOD -> goodDays
    }

    fun cadenceFor(friend: Friend): Int? = friend.meetEveryDays?.takeIf { it > 0 }
        ?: friend.closeness?.takeIf { friend.isCloseFriend }?.let(::daysFor)

    fun sourceFor(friend: Friend): Source? = when {
        friend.meetEveryDays?.takeIf { it > 0 } != null -> Source.CUSTOM
        friend.isCloseFriend && friend.closeness != null -> Source.CLOSENESS
        else -> null
    }

    enum class Source { CUSTOM, CLOSENESS }

    companion object {
        fun from(settings: AppSettings) = CadenceRules(settings.innerCircleDays, settings.closeCircleDays, settings.goodFriendsDays)
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
