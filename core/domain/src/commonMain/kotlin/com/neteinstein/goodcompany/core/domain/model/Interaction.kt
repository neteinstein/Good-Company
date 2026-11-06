package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

enum class InteractionType { CALL, WHATSAPP, MEETUP }

/** Evidence of contact with a friend, from a check-in, the call log or an imported WhatsApp chat. */
@Serializable
data class Interaction(
    val friendId: FriendId,
    val type: InteractionType,
    val at: Instant,
)

/** A chat line kept (bounded) as context for AI taste matching. */
@Serializable
data class ConversationMessage(
    val friendId: FriendId,
    val at: Instant,
    val fromMe: Boolean,
    val text: String,
)
