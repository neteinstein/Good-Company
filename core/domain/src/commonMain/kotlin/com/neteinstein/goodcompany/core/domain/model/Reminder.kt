package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * "Remind me to book tickets for X to go with Y." It can be about people, a circle ([circleId], issue #88), or both,
 * like a [Plan]. A circle reminder is for the circle's members as they are now ([peopleIds]), so someone who joins later
 * sees it too; a deleted circle reads as none, and the reminder keeps its own people.
 */
@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val friendIds: List<FriendId> = emptyList(),
    val dueAt: Instant? = null,
    val done: Boolean = false,
    val createdAt: Instant,
    /** Missing in backups made before circle reminders: those restore with none. */
    val circleId: String? = null,
) {
    /** Who it is for: its own people, then the current members of its circle (when that circle is in [circles]). */
    fun peopleIds(circles: List<FriendGroup>): List<FriendId> {
        val circle = circleId?.let { id -> circles.firstOrNull { it.id == id } } ?: return friendIds
        return (friendIds + circle.memberIds).distinct()
    }

    /** Whether it is for [friend] ([peopleIds]). */
    fun isFor(friend: FriendId, circles: List<FriendGroup>): Boolean = friend in peopleIds(circles)
}
