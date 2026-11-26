package org.goodcompany.app.core.domain.usecase

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.Interaction
import org.goodcompany.app.core.domain.model.PersonLink
import org.goodcompany.app.core.domain.model.Plan
import org.goodcompany.app.core.domain.model.PlanStatus
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.util.toLocalDate

/**
 * Builds the "Coming up" agenda for the next [horizonDays] days:
 * birthdays (friends you care about, kids with their reminder on, shown with their parent, and relatives whose reminder is on), important events, anniversaries, open reminders, planned meetups and
 * overdue catch-ups. Overdue reminders, catch-ups and unconfirmed past plans are pinned to [today] so they stay visible.
 */
object AgendaBuilder {
    const val DEFAULT_HORIZON_DAYS = 60

    fun build(
        friends: List<Friend>,
        reminders: List<Reminder>,
        hangouts: List<Hangout>,
        today: LocalDate,
        zone: TimeZone,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
        rules: CadenceRules = CadenceRules(),
        groups: List<FriendGroup> = emptyList(),
        plans: List<Plan> = emptyList(),
        interactions: List<Interaction> = emptyList(),
        links: List<PersonLink> = emptyList(),
    ): List<AgendaItem> {
        val end = today.plus(horizonDays, DateTimeUnit.DAY)
        fun inWindow(d: LocalDate) = d in today..end
        val items = mutableListOf<AgendaItem>()
        val parents = KidsAsPeople.parentsById(links, friends)

        fun Friend.caresAbout() = isCloseFriend || notifyBirthday
        for (friend in friends) {
            // A kid (F9) is a person with their own reminder; their birthday also shows when you care about a parent,
            // as it did before kids were people, and the row says whose kid they are.
            val parent = parents[friend.id]?.let { ps -> ps.firstOrNull { it.caresAbout() } ?: ps.first() }
            if (friend.caresAbout() || parent?.caresAbout() == true) {
                friend.birthday?.let { b ->
                    val next = b.nextOccurrence(today)
                    if (inWindow(next)) {
                        items += if (parent != null) {
                            AgendaItem.KidBirthday(parent, friend, next, b.ageOn(next))
                        } else {
                            AgendaItem.FriendBirthday(friend, next, b.ageOn(next))
                        }
                    }
                }
            }
            // A relative's birthday has its own switch (on by default, brief decision F10), whatever the friend's settings.
            friend.relatives.filter { it.notifyBirthday }.forEach { relative ->
                relative.birthday?.let { b ->
                    val next = b.nextOccurrence(today)
                    if (inWindow(next)) items += AgendaItem.RelativeBirthday(friend, relative, next, b.ageOn(next))
                }
            }
            friend.importantEvents.filter { inWindow(it.date) }.forEach { items += AgendaItem.FriendEvent(friend, it) }
        }

        // Anniversaries have their own switch on the link, like a relative's birthday.
        PersonLinks.anniversaries(links, friends).forEach { (link, a, b) ->
            val next = link.nextAnniversary(today) ?: return@forEach
            if (inWindow(next)) items += AgendaItem.Anniversary(link, a, b, next, link.yearsOn(next))
        }

        val byId = friends.associateBy { it.id }
        // A snoozed reminder shows on the day its snooze ends (issue #99).
        reminders.filter { !it.done && it.nextAt != null }.forEach { r ->
            val due = r.nextAt!!.toLocalDate(zone)
            if (due <= end) {
                // A circle reminder (issue #88) is for the circle's members as they are now.
                items += AgendaItem.ReminderDue(r, r.peopleIds(groups).mapNotNull(byId::get), maxOf(due, today))
            }
        }

        // Every open occurrence: past ones ask "Did you meet?" (pinned to today), the rest are coming up.
        val circlesById = groups.associateBy { it.id }
        plans.filter { it.status == PlanStatus.PLANNED }.forEach { p ->
            val circle = p.circleId?.let(circlesById::get)
            val with = (p.friendIds + circle?.memberIds.orEmpty()).distinct().mapNotNull(byId::get)
            p.occurrences(minOf(p.date, today), end).forEach { day ->
                items += if (day < today) {
                    AgendaItem.PlanToConfirm(p, with, today, day, circle)
                } else {
                    AgendaItem.PlanUpcoming(p, with, day, circle)
                }
            }
        }

        Relationships.meetupStatuses(friends, hangouts, today, zone, rules, interactions)
            .filter { it.isOverdue(today) }
            .forEach { items += AgendaItem.MeetupOverdue(it.friend, it.daysSinceLastSeen, today) }

        Relationships.groupStatuses(groups, friends, hangouts, today, zone)
            .filter { it.isOverdue(today) }
            .forEach { items += AgendaItem.GroupOverdue(it.group, it.members, it.daysSince, today) }

        return items.sortedWith(compareBy<AgendaItem> { it.date }.thenBy { it.sortRank() })
    }

    private fun AgendaItem.sortRank() = when (this) {
        is AgendaItem.PlanToConfirm -> 0
        is AgendaItem.PlanUpcoming -> 1
        is AgendaItem.MeetupOverdue -> 0
        is AgendaItem.GroupOverdue -> 0
        is AgendaItem.FriendEvent -> 1
        is AgendaItem.Anniversary -> 2
        is AgendaItem.FriendBirthday -> 2
        is AgendaItem.KidBirthday -> 3
        is AgendaItem.RelativeBirthday -> 3
        is AgendaItem.ReminderDue -> 4
    }
}
