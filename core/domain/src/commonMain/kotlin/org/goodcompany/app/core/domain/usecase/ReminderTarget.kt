package org.goodcompany.app.core.domain.usecase

import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Reminder

/**
 * Issue #135: where a tap on a reminder goes, everywhere it shows (Today, Everything coming up, a person's timeline, a
 * notification). A reminder for a circle opens the circle's page; one for exactly one person (no circle) opens that
 * person; one for several people or nobody opens its editor, so a tap never does nothing. The ⋮ actions don't change.
 */
sealed interface ReminderTarget {
    data class Circle(val circleId: String) : ReminderTarget

    data class Person(val friendId: FriendId) : ReminderTarget

    data class Edit(val reminderId: String) : ReminderTarget

    companion object {
        fun of(reminder: Reminder): ReminderTarget {
            reminder.circleId?.let { return Circle(it) }
            val people = reminder.friendIds.distinct()
            return people.singleOrNull()?.let(::Person) ?: Edit(reminder.id)
        }

        /** The same target as an app link, for a reminder notification's tap. */
        fun linkFor(reminder: Reminder): String = when (val target = of(reminder)) {
            is Circle -> AppLink.circle(target.circleId)
            is Person -> AppLink.person(target.friendId)
            is Edit -> AppLink.reminder(target.reminderId)
        }
    }
}
