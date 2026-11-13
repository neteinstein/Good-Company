package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate

/** One row of the "Coming up" agenda. Sorted by [date]. */
sealed interface AgendaItem {
    val date: LocalDate
    val key: String

    data class FriendBirthday(val friend: Friend, override val date: LocalDate, val turning: Int?) : AgendaItem {
        override val key get() = "bday-${friend.id.value}-$date"
    }

    data class KidBirthday(val parent: Friend, val kid: Kid, override val date: LocalDate, val turning: Int?) : AgendaItem {
        override val key get() = "kid-${kid.id}-$date"
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

/** A raw entry from the device address book. */
data class ContactRecord(
    val contactId: String,
    val name: String,
    val phoneNumbers: List<String>,
    val birthday: Birthday?,
    /** The contact's thumbnail, when the platform hands one over. */
    val photo: ContactPhoto? = null,
)
