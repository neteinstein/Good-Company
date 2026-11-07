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
}

/** A local notification to schedule. [id] is stable so rescheduling replaces, never duplicates. */
data class PlannedNotification(
    val id: String,
    val at: kotlin.time.Instant,
    val title: String,
    val body: String,
)

/** A raw entry from the device address book. */
data class ContactRecord(
    val contactId: String,
    val name: String,
    val phoneNumbers: List<String>,
    val birthday: Birthday?,
)
