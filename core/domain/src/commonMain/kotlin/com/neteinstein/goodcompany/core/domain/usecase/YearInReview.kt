package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** "Your year with friends" — a Spotify-Wrapped-style summary built only from on-device data. */
data class YearInReview(
    val year: Int,
    val meetups: Int,
    val friendsSeen: Int,
    /** Most-seen friends, best first (max 5). */
    val topFriends: List<Pair<Friend, Int>>,
    /** Most-visited named places (max 3). */
    val topPlaces: List<Pair<String, Int>>,
    val busiestMonth: Pair<Month, Int>?,
    val favouriteWeekday: DayOfWeek?,
    /** Largest gathering by number of friends. */
    val biggestHangout: Hangout?,
    val biggestHangoutFriends: List<Friend>,
    /** Longest run of consecutive weeks (Mon–Sun) with at least one meetup. */
    val longestWeekStreak: Int,
    /** Friends you met for the first time this year (in the app's records). */
    val newFaces: List<Friend>,
    val remindersDone: Int,
    /** Calls, messages, emails, video calls and WhatsApp days this year, by type (types with none are left out). */
    val contactsByType: Map<InteractionType, Int> = emptyMap(),
) {
    val contacts: Int get() = contactsByType.values.sum()

    val isEmpty: Boolean get() = meetups == 0
}

object YearInReviewBuilder {
    fun build(
        year: Int,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        reminders: List<Reminder>,
        zone: TimeZone,
        interactions: List<Interaction> = emptyList(),
    ): YearInReview {
        val byId = friends.associateBy { it.id }
        val inYear = hangouts.filter { it.at.toLocalDate(zone).year == year }
        val counts = inYear.flatMap { it.friendIds }.groupingBy { it }.eachCount()
        val topFriends = counts.entries.mapNotNull { (id, n) -> byId[id]?.let { it to n } }
            .sortedWith(compareByDescending<Pair<Friend, Int>> { it.second }.thenBy { it.first.name })
            .take(5)
        val topPlaces = inYear.mapNotNull { it.place?.name?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3).map { it.key to it.value }
        val dates = inYear.map { it.at.toLocalDate(zone) }
        val busiest = dates.groupingBy {
            it.month
        }.eachCount().maxWithOrNull(compareBy<Map.Entry<Month, Int>> { it.value }.thenByDescending { it.key.ordinal })
        val weekday = dates.groupingBy {
            it.dayOfWeek
        }.eachCount().maxWithOrNull(compareBy<Map.Entry<DayOfWeek, Int>> { it.value }.thenByDescending { it.key.ordinal })?.key
        val biggest = inYear.maxWithOrNull(compareBy<Hangout> { it.friendIds.size }.thenBy { it.at })
        val firstMet = hangouts.flatMap { h -> h.friendIds.map { it to h.at } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.min().toLocalDate(zone) }
        val newFaces = firstMet.filter { (_, d) -> d.year == year }.keys.mapNotNull(byId::get).sortedBy { it.name }
        return YearInReview(
            year = year,
            meetups = inYear.size,
            friendsSeen = counts.keys.count { it in byId },
            topFriends = topFriends,
            topPlaces = topPlaces,
            busiestMonth = busiest?.let { it.key to it.value },
            favouriteWeekday = weekday,
            biggestHangout = biggest?.takeIf { it.friendIds.size > 1 },
            biggestHangoutFriends = biggest?.takeIf { it.friendIds.size > 1 }?.friendIds?.mapNotNull(byId::get).orEmpty(),
            longestWeekStreak = longestWeekStreak(dates),
            newFaces = newFaces,
            remindersDone = reminders.count { r -> r.done && (r.dueAt ?: r.createdAt).toLocalDate(zone).year == year },
            contactsByType = interactions
                .filter { it.type.isRemote && it.friendId in byId && it.at.toLocalDate(zone).year == year }
                .groupingBy { it.type }.eachCount(),
        )
    }

    /** Consecutive Monday-based weeks containing at least one date. */
    fun longestWeekStreak(dates: List<LocalDate>): Int {
        val weeks = dates.map { it.minus(it.dayOfWeek.ordinal, DateTimeUnit.DAY) }.distinct().sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (w in weeks) {
            run = if (prev != null && prev.plus(7, DateTimeUnit.DAY) == w) run + 1 else 1
            best = maxOf(best, run)
            prev = w
        }
        return best
    }

    /** The year worth showing: last year during January (if it has data), otherwise this year. */
    fun defaultYear(today: LocalDate, hangouts: List<Hangout>, zone: TimeZone): Int {
        val lastYearHasData = hangouts.any { it.at.toLocalDate(zone).year == today.year - 1 }
        return if (today.month == Month.JANUARY && lastYearHasData) today.year - 1 else today.year
    }
}

class ObserveYearInReviewUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val reminders: ReminderRepository,
    private val interactions: InteractionRepository,
    private val clock: AppClock,
) {
    /** [year] null = [YearInReviewBuilder.defaultYear]. */
    operator fun invoke(year: Int? = null): Flow<YearInReview> =
        combine(friends.friends, hangouts.hangouts, reminders.reminders, interactions.interactions) { fs, hs, rs, ints ->
            YearInReviewBuilder.build(year ?: YearInReviewBuilder.defaultYear(clock.today(), hs, clock.timeZone), fs, hs, rs, clock.timeZone, ints)
        }
}
