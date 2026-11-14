package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A kind of contact. [MEETUP] is kept for old data only: meetups are [Hangout]s. Every other type is "contact at a
 * distance": it adds to warmth and resets the rhythm only when the friend's "calls and chats count" switch is on.
 */
enum class InteractionType {
    CALL,
    WHATSAPP,
    MEETUP,
    MESSAGE,
    EMAIL,
    VIDEO_CALL,
    ;

    /** Calls, messages, emails, video calls and WhatsApp days: everything that isn't being together. */
    val isRemote: Boolean get() = this != MEETUP
}

/** Where an [Interaction] came from. Automatic sources ([isAutomatic]) are de-duplicated by friend, type and time. */
enum class InteractionSource {
    MANUAL,
    CALL_LOG,
    CHAT_IMPORT,
    SHARE,
    NOTIFICATION,
    ;

    val isAutomatic: Boolean get() = this == CALL_LOG || this == CHAT_IMPORT
}

/**
 * Evidence of contact with a friend: a call, message, email, video call or WhatsApp day, logged by hand, read from
 * the call log or an imported chat. Logging one contact with several people writes one row per person sharing a
 * [batchId], so each timeline can say who else was there.
 */
@Serializable
data class Interaction(
    val friendId: FriendId,
    val type: InteractionType,
    val at: Instant,
    /** Blank only in backups older than the timeline: storage gives those [automaticId]s. */
    val id: String = "",
    val note: String = "",
    val source: InteractionSource = InteractionSource.MANUAL,
    val durationSeconds: Int? = null,
    val batchId: String? = null,
    /** WhatsApp import: how many messages were sent that day. */
    val messageCount: Int? = null,
) {
    /**
     * This interaction with an id. One without (from a backup older than the timeline) gets its stable automatic id
     * and, like the database migration, counts as a call-log row (calls) or a chat import (WhatsApp).
     */
    fun withId(): Interaction {
        if (id.isNotBlank()) return this
        val legacySource = when (type) {
            InteractionType.CALL -> InteractionSource.CALL_LOG
            InteractionType.WHATSAPP -> InteractionSource.CHAT_IMPORT
            else -> source
        }
        return copy(id = automaticId(friendId, type, at), source = legacySource)
    }

    companion object {
        /** The id of a row from an automatic source: the same call or chat day always gets the same id. */
        fun automaticId(friendId: FriendId, type: InteractionType, at: Instant): String =
            "auto-${type.name.lowercase()}-${friendId.value}-${at.toEpochMilliseconds()}"
    }
}

/** A dated note about a friend ("talked about the move to Porto"). Never counts as contact or warmth. */
@Serializable
data class JournalEntry(
    val id: String,
    val friendId: FriendId,
    val at: Instant,
    val text: String,
)

/** A chat line kept (bounded) as context for AI taste matching. */
@Serializable
data class ConversationMessage(
    val friendId: FriendId,
    val at: Instant,
    val fromMe: Boolean,
    val text: String,
)
