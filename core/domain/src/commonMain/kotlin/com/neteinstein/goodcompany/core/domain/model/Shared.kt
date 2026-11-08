package com.neteinstein.goodcompany.core.domain.model

/**
 * Something shared into the app from another app's share sheet: an Instagram story, a LinkedIn post, a link,
 * a screenshot. Never stored as-is; the user turns it into a "What's new" line for one friend.
 */
data class SharedContent(
    val text: String? = null,
    val url: String? = null,
    val image: SharedImage? = null,
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && url.isNullOrBlank() && image == null

    /** A starting point for the "What's new" line when there's no AI: the shared text, else the link. */
    fun draft(maxLength: Int = 200): String {
        val body = text?.trim()?.lines()?.firstOrNull { it.isNotBlank() }?.trim()
        val base = body?.takeIf { it.isNotEmpty() && it != url?.trim() } ?: url?.trim().orEmpty()
        return if (base.length > maxLength) base.take(maxLength - 1).trimEnd() + "…" else base
    }
}

/** A shared image (usually a screenshot), already downscaled by the platform. */
class SharedImage(val bytes: ByteArray, val mediaType: String) {
    override fun equals(other: Any?): Boolean = other is SharedImage && mediaType == other.mediaType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
}
