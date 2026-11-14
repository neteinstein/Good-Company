package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlin.math.roundToInt

/**
 * "Your people" (issue #46): warmth per circle and per closeness, the 90-day trend, who is cooling, meetups per month
 * and people never logged. Warmth is the same [Warmth] score as on each friend's page. With *Show warmth* off every
 * warmth figure is null or empty and only counts remain.
 */
data class PeopleDashboard(
    val people: Int,
    val meetupsLast90Days: Int,
    /** Null when warmth is hidden or there's nobody yet. */
    val averageWarmth: Int?,
    val byCircle: List<GroupWarmth>,
    val byCloseness: List<ClosenessWarmth>,
    /** Average warmth week by week, oldest first ([TREND_WEEKS] + 1 points, the last is today). Empty when hidden. */
    val trend: List<Int>,
    /** Falling warmth, steepest first. Empty when hidden. */
    val cooling: List<CoolingFriend>,
    /** The last [MONTHS] months, oldest first, this month last. */
    val meetupsPerMonth: List<MonthCount>,
    /** Your people with no meetup, call or message at all. */
    val neverLogged: List<Friend>,
    val showWarmth: Boolean,
) {
    /** Change over the [trend] (last point − first), or null without one. */
    val trendDelta: Int? get() = if (trend.size >= 2) trend.last() - trend.first() else null

    data class GroupWarmth(val circle: FriendGroup, val members: Int, val warmth: Int?, val change: Int?) {
        /** Warmth fell by [COOLING_DROP] or more over the last [COMPARE_DAYS] days. */
        val isCooling: Boolean get() = (change ?: 0) <= -COOLING_DROP
    }

    data class ClosenessWarmth(val closeness: Closeness, val people: Int, val warmth: Int?)

    data class CoolingFriend(val friend: Friend, val before: Int, val now: Int, val circle: FriendGroup?)

    data class MonthCount(val year: Int, val month: Int, val meetups: Int)

    companion object {
        const val WINDOW_DAYS = 90
        const val TREND_WEEKS = 12
        const val MONTHS = 6

        /** "Cooling" compares warmth now with this many days ago. */
        const val COMPARE_DAYS = 30

        /** A drop of at least this many points is cooling (a person or a circle's average). */
        const val COOLING_DROP = 10
        const val MAX_COOLING = 5

        /**
         * Who counts as "your people" (here and on the network map): not just someone in the address book, but a real
         * friend, someone with a closeness or a relation to you, or a circle member. Orphaned contacts are left out.
         */
        fun isYours(friend: Friend, inACircle: Boolean): Boolean =
            !friend.isOrphaned && (friend.isCloseFriend || friend.closeness != null || friend.relationToMe != null || inACircle)

        fun build(
            friends: List<Friend>,
            circles: List<FriendGroup>,
            hangouts: List<Hangout>,
            interactions: List<Interaction>,
            settings: AppSettings,
            today: LocalDate,
            zone: TimeZone,
            /** False skips the week-by-week [trend] (the People tab's summary card only needs counts and who's cooling). */
            withTrend: Boolean = true,
        ): PeopleDashboard {
            val inCircles = circles.flatMapTo(HashSet()) { it.memberIds }
            val people = friends.filter { isYours(it, it.id in inCircles) }
            val rules = CadenceRules.from(settings)
            val show = settings.showWarmth
            fun warmthOn(f: Friend, day: LocalDate) = Warmth.score(f, hangouts, interactions, rules.cadenceFor(f), day, zone).value
            val compareDay = today.minus(COMPARE_DAYS, DateTimeUnit.DAY)
            val now = if (show) people.associate { it.id to warmthOn(it, today) } else emptyMap()
            val before = if (show) people.associate { it.id to warmthOn(it, compareDay) } else emptyMap()
            fun avg(values: List<Int>): Int? = if (values.isEmpty()) null else values.average().roundToInt()

            val byCircle = circles.map { c ->
                val members = people.filter { it.id in c.memberIds }
                GroupWarmth(
                    circle = c,
                    members = members.size,
                    warmth = if (show) avg(members.mapNotNull { now[it.id] }) else null,
                    change = if (show) {
                        avg(members.mapNotNull { now[it.id] })?.let { n -> avg(members.mapNotNull { before[it.id] })?.let { n - it } }
                    } else {
                        null
                    },
                )
            }.filter { it.members > 0 }.sortedWith(compareByDescending<GroupWarmth> { it.warmth ?: it.members }.thenBy { it.circle.name.lowercase() })

            val byCloseness = Closeness.entries.mapNotNull { c ->
                val members = people.filter { it.closeness == c }
                if (members.isEmpty()) null else ClosenessWarmth(c, members.size, if (show) avg(members.mapNotNull { now[it.id] }) else null)
            }

            val trend = if (withTrend && show && people.isNotEmpty()) {
                (TREND_WEEKS downTo 0).map { w ->
                    val day = today.minus(w * 7, DateTimeUnit.DAY)
                    people.map { warmthOn(it, day) }.average().roundToInt()
                }
            } else {
                emptyList()
            }

            val firstCircle = circles.flatMap { c -> c.memberIds.map { it to c } }.groupBy({ it.first }, { it.second })
            val cooling = if (show) {
                people.mapNotNull { f ->
                    val b = before.getValue(f.id)
                    val n = now.getValue(f.id)
                    if (b - n >= COOLING_DROP) CoolingFriend(f, b, n, firstCircle[f.id]?.firstOrNull()) else null
                }.sortedWith(compareByDescending<CoolingFriend> { it.before - it.now }.thenBy { it.friend.name.lowercase() }).take(MAX_COOLING)
            } else {
                emptyList()
            }

            val meetupDays = hangouts.map { it.at.toLocalDate(zone) }.filter { it <= today }
            val months = (MONTHS - 1 downTo 0).map { back ->
                val total = today.year * 12 + (today.month.ordinal) - back
                total / 12 to total % 12 + 1
            }
            val perMonth = months.map { (y, m) -> MonthCount(y, m, meetupDays.count { it.year == y && it.month.ordinal + 1 == m }) }

            val logged = hangouts.flatMapTo(HashSet()) { it.friendIds } + interactions.map { it.friendId }
            return PeopleDashboard(
                people = people.size,
                meetupsLast90Days = meetupDays.count { it.daysUntil(today) < WINDOW_DAYS },
                averageWarmth = if (show) avg(now.values.toList()) else null,
                byCircle = byCircle,
                byCloseness = byCloseness,
                trend = trend,
                cooling = cooling,
                meetupsPerMonth = perMonth,
                neverLogged = people.filter { it.id !in logged }.sortedBy { it.name.lowercase() },
                showWarmth = show,
            )
        }
    }
}

class ObservePeopleDashboardUseCase(
    private val friends: FriendRepository,
    private val groups: GroupRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    /** [withTrend] false leaves [PeopleDashboard.trend] empty, for the People tab's summary card. */
    operator fun invoke(withTrend: Boolean = true): Flow<PeopleDashboard> =
        combine(friends.friends, groups.groups, hangouts.hangouts, interactions.interactions, settings.settings) { fs, gs, hs, ints, s ->
            PeopleDashboard.build(fs, gs, hs, ints, s, clock.today(), clock.timeZone, withTrend)
        }
}
