package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate

/** One row of the "Coming up" agenda. Sorted by [date]. */
sealed interface AgendaItem {
    val date: LocalDate
    val key: String

    data class FriendBirthday(val friend: Friend, override val date: LocalDate, val turning: Int?) : AgendaItem {
        override val key get() = "bday-${friend.id.value}-$date"
    }

    /** A kid's birthday (F9): the kid is a person with their own reminder, shown with their [parent]. */
    data class KidBirthday(val parent: Friend, val kid: Friend, override val date: LocalDate, val turning: Int?) : AgendaItem {
        override val key get() = "kid-${kid.id.value}-$date"
    }

    /** A relative's birthday; only for relatives with [Relative.notifyBirthday] on. */
    data class RelativeBirthday(val friend: Friend, val relative: Relative, override val date: LocalDate, val turning: Int?) : AgendaItem {
        override val key get() = "relative-${friend.id.value}-${relative.id}-$date"
    }

    /** A couple's anniversary (issue #39): a current romantic link with a start date and its reminder on. */
    data class Anniversary(val link: PersonLink, val first: Friend, val second: Friend, override val date: LocalDate, val years: Int?) : AgendaItem {
        override val key get() = "anniversary-${link.id}-$date"
    }

    data class FriendEvent(val friend: Friend, val event: ImportantEvent) : AgendaItem {
        override val date get() = event.date
        override val key get() = "event-${event.id}"
    }

    data class ReminderDue(val reminder: Reminder, val friends: List<Friend>, override val date: LocalDate) : AgendaItem {
        override val key get() = "reminder-${reminder.id}"
    }

    data class MeetupOverdue(val friend: Friend, val daysSince: Int?, override val date: LocalDate) : AgendaItem {
        override val key get() = "overdue-${friend.id.value}"
    }

    /** A planned meetup coming up. */
    data class PlanUpcoming(val plan: Plan, val friends: List<Friend>) : AgendaItem {
        override val date get() = plan.date
        override val key get() = "plan-${plan.id}"
    }

    /** A plan whose day has passed: "Did you meet?" Pinned to today until answered. */
    data class PlanToConfirm(val plan: Plan, val friends: List<Friend>, override val date: LocalDate) : AgendaItem {
        override val key get() = "plan-confirm-${plan.id}"
    }

    data class GroupOverdue(val group: FriendGroup, val members: List<Friend>, val daysSince: Int?, override val date: LocalDate) : AgendaItem {
        override val key get() = "group-overdue-${group.id}"
    }
}

/** Everyone an agenda item is about (a circle's reminder: its members). Used to filter Home by a circle. */
val AgendaItem.friendIds: Set<FriendId>
    get() = when (this) {
        is AgendaItem.FriendBirthday -> setOf(friend.id)
        is AgendaItem.KidBirthday -> setOf(parent.id, kid.id)
        is AgendaItem.RelativeBirthday -> setOf(friend.id)
        is AgendaItem.FriendEvent -> setOf(friend.id)
        is AgendaItem.Anniversary -> setOf(first.id, second.id)
        is AgendaItem.ReminderDue -> friends.mapTo(mutableSetOf()) { it.id }
        is AgendaItem.MeetupOverdue -> setOf(friend.id)
        is AgendaItem.PlanUpcoming -> friends.mapTo(mutableSetOf()) { it.id }
        is AgendaItem.PlanToConfirm -> friends.mapTo(mutableSetOf()) { it.id }
        is AgendaItem.GroupOverdue -> group.memberIds.toSet()
    }

/** A raw entry from the device address book. */
data class ContactRecord(
    val contactId: String,
    val name: String,
    val phoneNumbers: List<String>,
    val birthday: Birthday?,
    /** The contact's thumbnail, when the platform hands one over. */
    val photo: ContactPhoto? = null,
    /** Email addresses, in the contact's order. */
    val emails: List<String> = emptyList(),
    /** A favourite in the address book (Android `STARRED`; iOS has no favourites API, so always false there). */
    val starred: Boolean = false,
)
