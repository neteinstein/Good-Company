package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

enum class IdeaKind { EVENT, NEWS, TALKING_POINT }

/** An AI suggestion: an event to go to together, news they'd care about, or something to bring up. */
@Serializable
data class Idea(
    val id: String,
    val friendIds: List<FriendId>,
    val kind: IdeaKind,
    val title: String,
    val description: String,
    val url: String? = null,
    val whenText: String? = null,
    val createdAt: Instant,
    val dismissed: Boolean = false,
    /** Set when the user sent this plan to the friends ("Propose"). */
    val proposedAt: Instant? = null,
)
