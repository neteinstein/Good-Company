package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * Builds the "Coming up" agenda for the next [horizonDays] days:
 * birthdays (friend + kids) for friends you care about, important events, open reminders and overdue meetups.
 * Overdue reminders and meetups are pinned to [today] so they stay visible.
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
    ): List<AgendaItem> {
        val end = today.plus(horizonDays, DateTimeUnit.DAY)
        fun inWindow(d: LocalDate) = d in today..end
        val items = mutableListOf<AgendaItem>()

        for (friend in friends) {
            val caresAbout = friend.isCloseFriend || friend.notifyBirthday
            if (caresAbout) {
                friend.birthday?.let { b ->
                    val next = b.nextOccurrence(today)
                    if (inWindow(next)) items += AgendaItem.FriendBirthday(friend, next, b.ageOn(next))
                }
                friend.kids.forEach { kid ->
                    kid.birthday?.let { b ->
                        val next = b.nextOccurrence(today)
                        if (inWindow(next)) items += AgendaItem.KidBirthday(friend, kid, next, b.ageOn(next))
                    }
                }
            }
            friend.importantEvents.filter { inWindow(it.date) }.forEach { items += AgendaItem.FriendEvent(friend, it) }
        }

        val byId = friends.associateBy { it.id }
        reminders.filter { !it.done && it.dueAt != null }.forEach { r ->
            val due = r.dueAt!!.toLocalDate(zone)
            if (due <= end) {
                items += AgendaItem.ReminderDue(r, r.friendIds.mapNotNull(byId::get), maxOf(due, today))
            }
        }

        Relationships.meetupStatuses(friends, hangouts, today, zone, rules)
            .filter { it.isOverdue(today) }
            .forEach { items += AgendaItem.MeetupOverdue(it.friend, it.daysSinceLastSeen, today) }

        Relationships.groupStatuses(groups, friends, hangouts, today, zone)
            .filter { it.isOverdue(today) }
            .forEach { items += AgendaItem.GroupOverdue(it.group, it.members, it.daysSince, today) }

        return items.sortedWith(compareBy<AgendaItem> { it.date }.thenBy { it.sortRank() })
    }

    private fun AgendaItem.sortRank() = when (this) {
        is AgendaItem.MeetupOverdue -> 0
        is AgendaItem.GroupOverdue -> 0
        is AgendaItem.FriendEvent -> 1
        is AgendaItem.FriendBirthday -> 2
        is AgendaItem.KidBirthday -> 3
        is AgendaItem.ReminderDue -> 4
    }
}
