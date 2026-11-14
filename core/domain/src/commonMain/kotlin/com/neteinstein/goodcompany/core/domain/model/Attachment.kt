package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** What a shared [Attachment] holds. */
enum class AttachmentKind { TEXT, LINK, PHOTO }

/** Which gallery a [PhotoRef] points into. A reference from another platform (a restored backup) is never opened. */
enum class PhotoPlatform { ANDROID, IOS }

/**
 * A reference to a photo that stays in the device gallery (decision D6): an Android content URI the app holds a
 * persistable permission for, or an iOS `PHAsset.localIdentifier`. The full photo is never copied into the app.
 */
@Serializable
data class PhotoRef(val platform: PhotoPlatform, val value: String)

/**
 * Something shared into the app and kept on a friend's page (D6), or on a circle's (F12: it then shows on every
 * member's page). Exactly one of [friendId] and [circleId] is set.
 *
 * - [AttachmentKind.TEXT]: [text] whole (at most [MAX_TEXT] characters).
 * - [AttachmentKind.LINK]: [url] and [title] only when the sending app gave one. The page is never fetched.
 * - [AttachmentKind.PHOTO]: a small [thumbnail] and, when the platform allowed it, a [photoRef] to the original.
 *
 * When [countsAsInteraction] it was also logged as [interactionType] (a hangout for MEETUP, else interactions);
 * [logId] is the hangout id or the interactions' batch id (or the one interaction's id) so the timeline can show it.
 */
@Serializable
data class Attachment(
    val id: String,
    val friendId: FriendId? = null,
    val circleId: String? = null,
    val kind: AttachmentKind,
    val text: String? = null,
    val url: String? = null,
    val title: String? = null,
    val photoRef: PhotoRef? = null,
    val thumbnail: ContactPhoto? = null,
    val caption: String = "",
    /** When it happened (the date the user picked; for a photo, its own date when known). */
    val at: Instant,
    val countsAsInteraction: Boolean = false,
    val interactionType: InteractionType? = null,
    val logId: String? = null,
    val createdAt: Instant = at,
) {
    /** Whether it shows on [friend]'s page: their own, or one of a circle they are in. */
    fun isFor(friend: FriendId, circles: List<FriendGroup>): Boolean =
        friendId == friend || (circleId != null && circles.any { it.id == circleId && friend in it.memberIds })

    /** The host of [url] ("wikiloc.com"), for the link card. */
    val host: String?
        get() = url?.substringAfter("://", url)?.substringBefore('/')?.substringBefore('?')?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_TEXT = 20_000
    }
}
