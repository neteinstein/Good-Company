package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker

/** Shrinks pictures to contact-photo size with the platform's image codec (Android graphics, Skia elsewhere). */
expect fun createPhotoShrinker(): PhotoShrinker

/** The size a picture of [width] × [height] gets so that its longest side is at most [maxSide] (never larger). */
internal fun fitWithin(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longest = maxOf(width, height)
    if (longest <= maxSide || longest <= 0) return width to height
    val scale = maxSide.toDouble() / longest
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}
