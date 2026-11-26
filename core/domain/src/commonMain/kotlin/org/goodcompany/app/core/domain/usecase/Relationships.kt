package org.goodcompany.app.core.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.Interaction
import org.goodcompany.app.core.domain.util.toLocalDate
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
     * The last time each of [friends] caught up with you: their last meetup, or a later contact at a distance that
     * [policy] counts for them (decision 2026-11-21 "Catch-up · what counts").
     */
    fun lastCaughtUp(
        friends: List<Friend>,
        hangouts: List<Hangout>,
        interactions: List<Interaction>,
        policy: CatchUpPolicy,
    ): Map<FriendId, Instant> {
        val result = HashMap(lastSeen(hangouts))
        val byId = friends.associateBy { it.id }
        for (i in interactions) {
            val friend = byId[i.friendId] ?: continue
            if (!policy.counts(friend, i)) continue
            val prev = result[i.friendId]
            if (prev == null || i.at > prev) result[i.friendId] = i.at
        }
        return result
    }

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
     * Status of every friend with a cadence (own "meet every" or their circle's default) whose rhythm has started: a
     * meetup is logged, or the "When did you last see…?" card gave an approximate date ([Friend.approxLastSeen],
     * issue #68). Someone with nothing logged and no answer isn't overdue and has no status (the card asks about them
     * instead; see [LastSeenQuestions]). A real meetup always wins over the approximate date, which only moves the due
     * date: [MeetupStatus.lastSeenDate] and [MeetupStatus.daysSinceLastSeen] stay null until something is logged.
     * A snooze pushes the due date to the day after it ends. Paused friends and people whose keep-in-touch tracking is
     * off ([Friend.tracksKeepInTouch]) are left out. Besides meetups, the contacts from [interactions] that
     * [CadenceRules.catchUp] counts for each friend ([Friend.catchUpCounts]) restart the rhythm ([lastCaughtUp]).
     */
    fun meetupStatuses(
        friends: List<Friend>,
        hangouts: List<Hangout>,
        today: LocalDate,
        zone: TimeZone,
        rules: CadenceRules = CadenceRules(),
        interactions: List<Interaction> = emptyList(),
    ): List<MeetupStatus> {
        val caughtUp = lastCaughtUp(friends, hangouts, interactions, rules.catchUp)
        return friends.mapNotNull { friend ->
            if (friend.catchUpPaused || !friend.tracksKeepInTouch) return@mapNotNull null
            val every = rules.cadenceFor(friend) ?: return@mapNotNull null
            val last = caughtUp[friend.id]?.toLocalDate(zone)
            val since = last ?: friend.approxLastSeen ?: return@mapNotNull null
            val due = since.plus(every, DateTimeUnit.DAY)
            val snooze = friend.snoozedUntil?.takeIf { it >= today && it > since }
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
        /** Last time the circle met: the newest hangout with enough of its members ([meetupQuorum]). */
        val lastTogether: Instant?,
        val daysSince: Int?,
        /**
         * Null when the group has no cadence, or when its rhythm hasn't started: nothing logged and no answer to
         * "When did the circle last meet?" yet (issue #150).
         */
        val dueDate: LocalDate?,
    ) {
        fun isOverdue(today: LocalDate) = dueDate != null && dueDate <= today
    }

    fun groupStatuses(
        groups: List<FriendGroup>,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        today: LocalDate,
        zone: TimeZone,
    ): List<GroupStatus> {
        val byId = friends.associateBy { it.id }
        return groups.map { g ->
            val members = g.memberIds.mapNotNull(byId::get)
            val last = lastCircleMeetup(hangouts, g.memberIds.toSet())
            val lastDate = last?.toLocalDate(zone)
            val every = g.meetEveryDays?.takeIf { it > 0 }
            // Issue #150 (D 2026-11-21): like people, a circle with nothing logged isn't overdue; its rhythm starts
            // from the last-seen card's answer ([FriendGroup.approxLastMet]). A real meetup always wins.
            val since = lastDate ?: g.approxLastMet
            val baseDue = every?.let { e -> since?.plus(e, DateTimeUnit.DAY) }
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

    /**
     * How many members a hangout needs to count as the circle meeting: everyone in a circle of one or two, otherwise
     * at least half (and never fewer than two). A group check-in ticks only who came (F20), so "every member" would
     * almost never happen in a big circle such as a choir.
     */
    fun meetupQuorum(memberCount: Int): Int = when {
        memberCount <= 2 -> memberCount
        else -> maxOf(2, (memberCount + 1) / 2)
    }

    /** The newest hangout that counts as a meetup of a circle with [memberIds] (see [meetupQuorum]). */
    fun lastCircleMeetup(hangouts: List<Hangout>, memberIds: Set<FriendId>): Instant? {
        if (memberIds.isEmpty()) return null
        val quorum = meetupQuorum(memberIds.size)
        return hangouts.filter { h -> h.friendIds.count { it in memberIds } >= quorum }.maxOfOrNull { it.at }
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
