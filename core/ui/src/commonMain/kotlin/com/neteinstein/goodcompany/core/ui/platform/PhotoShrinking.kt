package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.CoverShrinker
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker

/** Shrinks pictures to contact-photo size with the platform's image codec (Android graphics, Skia elsewhere). */
expect fun createPhotoShrinker(): PhotoShrinker

/**
 * Shrinks pictures to a circle's cover size (issue #88): [ContactPhoto.COVER_MAX_SIDE_PX] on the long side, JPEG
 * quality lowered step by step until it fits [ContactPhoto.COVER_MAX_BYTES].
 */
fun createCoverShrinker(): CoverShrinker = CoverShrinker { image -> shrinkCover(image, ::shrinkToJpeg) }

/** Tries each of [COVER_QUALITIES] with [encode] until the result fits; null when unreadable or never small enough. */
internal suspend fun shrinkCover(image: ByteArray, encode: suspend (ByteArray, Int, Int) -> ByteArray?): ByteArray? {
    for (quality in COVER_QUALITIES) {
        val jpeg = encode(image, ContactPhoto.COVER_MAX_SIDE_PX, quality) ?: return null
        if (jpeg.size <= ContactPhoto.COVER_MAX_BYTES) return jpeg
    }
    return null
}

/** Decodes [image], scales it to at most [maxSide] on its long side and encodes it as JPEG at [quality]. */
internal expect suspend fun shrinkToJpeg(image: ByteArray, maxSide: Int, quality: Int): ByteArray?

/** JPEG qualities tried in turn for a cover. */
internal val COVER_QUALITIES = listOf(82, 70, 55, 40)

/** The size a picture of [width] × [height] gets so that its longest side is at most [maxSide] (never larger). */
internal fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide || longest <= 0) return width to height
    val scale = maxSide.toDouble() / longest
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}
