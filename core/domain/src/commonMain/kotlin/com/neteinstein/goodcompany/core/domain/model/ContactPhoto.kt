package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.jvm.JvmInline

/**
 * A friend's photo, taken from their contact (issue #15): a small thumbnail (JPEG or PNG), never a full-size picture.
 * Kept as base64 so it compares by content, goes into the backup JSON as is and fits a TEXT column everywhere.
 */
@Serializable
@JvmInline
value class ContactPhoto(val base64: String) {
    /** The image bytes, or empty when the stored text isn't valid base64. */
    fun bytes(): ByteArray = runCatching { Base64.decode(base64) }.getOrDefault(ByteArray(0))

    companion object {
        /** Larger images are shrunk by a [com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker] first, or dropped. */
        const val MAX_BYTES = 64 * 1024

        /** The longest side a shrunk photo gets. Avatars are at most 128 dp. */
        const val MAX_SIDE_PX = 256

        /** A photo from [bytes] when they are a usable size; null for nothing, empty or too large. */
        fun of(bytes: ByteArray?): ContactPhoto? = bytes
            ?.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
            ?.let { ContactPhoto(Base64.encode(it)) }
    }
}
