package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** "Remind me to book tickets for X to go with Y." */
@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val friendIds: List<FriendId> = emptyList(),
    val dueAt: Instant? = null,
    val done: Boolean = false,
    val createdAt: Instant,
)
