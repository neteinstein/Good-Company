package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.time.Instant

/** Pure relationship maths shared by several use cases. */
object Relationships {
    /** Last time you were physically together, per friend. */
    fun lastSeen(hangouts: List<Hangout>): Map<FriendId, Instant> {
        val result = HashMap<FriendId, Instant>()
        for (h in hangouts) {
            for (id in h.friendIds) {
                val prev = result[id]
                if (prev == null || h.at > prev) result[id] = h.at
            }
        }
        return result
    }

    /** Last contact of any kind (meetups, calls, messages), per friend. */
    fun lastContact(hangouts: List<Hangout>, interactions: List<Interaction>): Map<FriendId, Instant> {
        val result = HashMap(lastSeen(hangouts))
        for (i in interactions) {
            val prev = result[i.friendId]
            if (prev == null || i.at > prev) result[i.friendId] = i.at
        }
        return result
    }

    /**
     * Hangouts that included **all** of [friendIds], newest first.
     * "When was the last time I was with Ana *and* Rui?"
     */
    fun hangoutsWithAll(hangouts: List<Hangout>, friendIds: Set<FriendId>): List<Hangout> =
        hangouts.filter { h -> friendIds.all { it in h.friendIds } }.sortedByDescending { it.at }

    data class MeetupStatus(
        val friend: Friend,
        val lastSeenDate: LocalDate?,
        /** Date the "meet every N days" rule falls due (pushed out by a snooze). */
        val dueDate: LocalDate,
        val daysSinceLastSeen: Int?,
        /** The effective cadence (per-friend override or circle default). */
        val cadenceDays: Int = friend.meetEveryDays ?: 0,
        val snoozedUntil: LocalDate? = null,
    ) {
        fun isOverdue(today: LocalDate) = dueDate <= today
    }

    /**
     * Status of every friend with a cadence (own "meet every" or their circle's default).
     * Never-met friends are due immediately; a snooze pushes the due date to the day after it ends.
     */
    fun meetupStatuses(
        friends: List<Friend>,
        hangouts: List<Hangout>,
        today: LocalDate,
        zone: TimeZone,
        rules: CadenceRules = CadenceRules(),
    ): List<MeetupStatus> {
        val seen = lastSeen(hangouts)
        return friends.mapNotNull { friend ->
            val every = rules.cadenceFor(friend) ?: return@mapNotNull null
            val last = seen[friend.id]?.toLocalDate(zone)
            val due = last?.plus(every, DateTimeUnit.DAY) ?: today
            val snooze = friend.snoozedUntil?.takeIf { it >= today && (last == null || it > last) }
            MeetupStatus(
                friend = friend,
                lastSeenDate = last,
                dueDate = snooze?.let { maxOf(due, it.plus(1, DateTimeUnit.DAY)) } ?: due,
                daysSinceLastSeen = last?.daysUntil(today),
                cadenceDays = every,
                snoozedUntil = snooze,
            )
        }.sortedBy { it.dueDate }
    }

    data class GroupStatus(
        val group: FriendGroup,
        val members: List<Friend>,
        /** Last hangout that included every member. */
        val lastTogether: Instant?,
        val daysSince: Int?,
        /** Null when the group has no cadence. */
        val dueDate: LocalDate?,
    ) {
        fun isOverdue(today: LocalDate) = dueDate != null && dueDate <= today
    }

    fun groupStatuses(groups: List<FriendGroup>, friends: List<Friend>, hangouts: List<Hangout>, today: LocalDate, zone: TimeZone): List<GroupStatus> {
        val byId = friends.associateBy { it.id }
        return groups.map { g ->
            val members = g.memberIds.mapNotNull(byId::get)
            val last = if (g.memberIds.isEmpty()) null else hangoutsWithAll(hangouts, g.memberIds.toSet()).firstOrNull()?.at
            val lastDate = last?.toLocalDate(zone)
            val every = g.meetEveryDays?.takeIf { it > 0 }
            val baseDue = every?.let { lastDate?.plus(it, DateTimeUnit.DAY) ?: today }
            val snooze = g.snoozedUntil?.takeIf { it >= today }
            GroupStatus(
                group = g,
                members = members,
                lastTogether = last,
                daysSince = lastDate?.daysUntil(today),
                dueDate = baseDue?.let { d -> snooze?.let { maxOf(d, it.plus(1, DateTimeUnit.DAY)) } ?: d },
            )
        }
    }

    data class MonthStats(val meetups: Int, val friendsSeen: Int, val topPlace: String?)

    /** Meetups, distinct friends and most frequent place in the calendar month containing [today]. */
    fun monthStats(hangouts: List<Hangout>, today: LocalDate, zone: TimeZone): MonthStats {
        val inMonth = hangouts.filter { h ->
            val d = h.at.toLocalDate(zone)
            d.year == today.year && d.month == today.month && d <= today
        }
        val topPlace = inMonth.mapNotNull { it.place?.name?.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return MonthStats(inMonth.size, inMonth.flatMap { it.friendIds }.distinct().size, topPlace)
    }
}
