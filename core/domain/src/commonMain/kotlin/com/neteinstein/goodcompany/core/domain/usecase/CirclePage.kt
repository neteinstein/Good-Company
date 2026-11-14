package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Something on a circle's "Coming up" (issue #88): an open plan or a reminder not done. */
sealed interface CircleUpcoming {
    /** Null for a reminder with no date (those come last). */
    val date: LocalDate?

    /** An open plan with the circle; a recurring one shows once, at its open occurrence. */
    data class PlanItem(val plan: Plan) : CircleUpcoming {
        override val date: LocalDate get() = plan.date
    }

    data class ReminderItem(val reminder: Reminder, override val date: LocalDate?) : CircleUpcoming
}

/**
 * A circle's page (issue #88): its status (members, last meetup, rhythm), what's coming up and its timeline.
 * [timeline] holds [TimelineEntry.Meetup]s whose `others` are everyone who came (the circle's members first, then
 * anyone else) and [TimelineEntry.Share]s, newest first.
 */
data class CirclePage(
    val status: Relationships.GroupStatus,
    val upcoming: List<CircleUpcoming>,
    val timeline: List<TimelineEntry>,
) {
    val group: FriendGroup get() = status.group
    val members: List<Friend> get() = status.members

    /** How many of the circle's members were at [meetup] ("4 of 6"). */
    fun membersAt(meetup: TimelineEntry.Meetup): Int = meetup.hangout.friendIds.distinct().count { it in group.memberIds }

    /** Whether every member was there ("Everyone"). */
    fun everyoneAt(meetup: TimelineEntry.Meetup): Boolean = group.memberIds.isNotEmpty() && group.memberIds.all { it in meetup.hangout.friendIds }
}

/** Pure maths behind a circle's page (issue #88). */
object CirclePages {
    /**
     * The page of [circleId], or null when it isn't in [groups] (deleted elsewhere: the page closes). [groups] should
     * include the built-in Family circle when the caller wants its page.
     */
    fun build(
        circleId: String,
        groups: List<FriendGroup>,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        plans: List<Plan>,
        reminders: List<Reminder>,
        attachments: List<Attachment>,
        today: LocalDate,
        zone: TimeZone,
    ): CirclePage? {
        val group = groups.firstOrNull { it.id == circleId } ?: return null
        val status = Relationships.groupStatuses(listOf(group), friends, hangouts, today, zone).single()
        return CirclePage(status, upcoming(group, plans, reminders, zone), timeline(group, friends, hangouts, attachments))
    }

    /** The circle's open plans and its reminders not done, soonest first (undated reminders last). */
    fun upcoming(group: FriendGroup, plans: List<Plan>, reminders: List<Reminder>, zone: TimeZone): List<CircleUpcoming> {
        val items = plans.filter { it.circleId == group.id && it.status == PlanStatus.PLANNED }.map { CircleUpcoming.PlanItem(it) } +
            reminders.filter { it.circleId == group.id && !it.done }.map { CircleUpcoming.ReminderItem(it, it.dueAt?.toLocalDate(zone)) }
        return items.sortedWith(compareBy<CircleUpcoming> { it.date == null }.thenBy { it.date })
    }

    /**
     * Meetups with at least [Relationships.meetupQuorum] of the circle's current members, and the shares saved to the
     * circle. A share logged as one of those meetups ([Attachment.logId]) shows once, on the meetup.
     */
    fun timeline(group: FriendGroup, friends: List<Friend>, hangouts: List<Hangout>, attachments: List<Attachment>): List<TimelineEntry> {
        val memberIds = group.memberIds.toSet()
        val byId = friends.associateBy { it.id }
        val quorum = Relationships.meetupQuorum(memberIds.size)
        val meetups = if (memberIds.isEmpty()) {
            emptyList()
        } else {
            hangouts.filter { h -> h.friendIds.distinct().count { it in memberIds } >= quorum }
        }
        val mine = attachments.filter { it.circleId == group.id }
        val byLog = mine.filter { it.countsAsInteraction && it.logId != null }.associateBy { it.logId }
        val shownOnMeetup = mutableSetOf<String>()
        val entries = buildList<TimelineEntry> {
            meetups.forEach { h ->
                val came = h.friendIds.distinct()
                // Members first, in the circle's order, then anyone else who came.
                val people = group.memberIds.filter { it in came } + came.filterNot { it in memberIds }
                val share = byLog[h.id]?.also { shownOnMeetup += it.id }
                add(TimelineEntry.Meetup(h, people.mapNotNull(byId::get), share))
            }
            mine.filterNot { it.id in shownOnMeetup }.forEach { add(TimelineEntry.Share(it)) }
        }
        return entries.sortedWith(compareByDescending<TimelineEntry> { it.at }.thenBy { it.key })
    }
}

/** A circle's page (issue #88), the built-in Family circle's too; emits null once the circle is gone. */
class ObserveCirclePageUseCase(
    private val groups: GroupRepository,
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val plans: PlanRepository,
    private val reminders: ReminderRepository,
    private val interactions: InteractionRepository,
    private val clock: AppClock,
) {
    operator fun invoke(id: String): Flow<CirclePage?> = combine(
        groups.groups,
        friends.friends,
        hangouts.hangouts,
        combine(plans.plans, reminders.reminders, interactions.attachments, ::PageExtras),
    ) { gs, fs, hs, (ps, rs, atts) ->
        CirclePages.build(id, gs, fs, hs, ps, rs, atts, clock.today(), clock.timeZone)
    }.distinctUntilChanged()
}

private data class PageExtras(val plans: List<Plan>, val reminders: List<Reminder>, val attachments: List<Attachment>)
