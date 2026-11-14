package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate

/**
 * Something shared into the app from another app's share sheet (or picked from the gallery inside the app): a piece
 * of a chat, a link, a photo. The user saves it as an [Attachment] on a friend or a circle (decision D6).
 */
data class SharedContent(
    val text: String? = null,
    val url: String? = null,
    val image: SharedImage? = null,
    /** The link's title, only when the sending app gave one (Android `EXTRA_SUBJECT`/`EXTRA_TITLE`, iOS item title, web `title`). */
    val title: String? = null,
    /** The gallery item the [image] came from, when the platform let the app keep a reference to it. */
    val photoRef: PhotoRef? = null,
    /** The photo's own date, when the platform gave one. */
    val takenOn: LocalDate? = null,
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && url.isNullOrBlank() && image == null

    /**
     * What it will be saved as: a photo wins; a link when the text is nothing but the link (and its title); else text.
     * Text that only mentions a link stays text, so a piece of a chat is kept whole.
     */
    val kind: AttachmentKind
        get() {
            if (image != null) return AttachmentKind.PHOTO
            val link = url?.trim().orEmpty()
            if (link.isEmpty()) return AttachmentKind.TEXT
            val rest = text.orEmpty().replace(link, "").trim()
            return if (rest.isEmpty() || rest == title?.trim()) AttachmentKind.LINK else AttachmentKind.TEXT
        }

    /** A starting point for the caption when there's no AI: the title, the shared text's first line, else the link. */
    fun draft(maxLength: Int = 200): String {
        val body = text?.trim()?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
        val base = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: body?.takeIf { it.isNotEmpty() && it != url?.trim() }
            ?: url?.trim().orEmpty()
        return if (base.length > maxLength) base.take(maxLength - 1).trimEnd() + "…" else base
    }
}

/** A shared image (usually a screenshot), already downscaled by the platform. */
class SharedImage(val bytes: ByteArray, val mediaType: String) {
    override fun equals(other: Any?): Boolean = other is SharedImage && mediaType == other.mediaType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
}
