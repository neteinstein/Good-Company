package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual fun createPhotoShrinker(): PhotoShrinker = PhotoShrinker { image -> skiaJpeg(image, ContactPhoto.MAX_SIDE_PX, JPEG_QUALITY) }

internal actual suspend fun shrinkToJpeg(image: ByteArray, maxSide: Int, quality: Int): ByteArray? = skiaJpeg(image, maxSide, quality)

private fun skiaJpeg(image: ByteArray, maxSide: Int, quality: Int): ByteArray? = runCatching {
    val source = Image.makeFromEncoded(image)
    val (w, h) = fitWithin(source.width, source.height, maxSide)
    val surface = Surface.makeRasterN32Premul(w, h)
    // JPEG has no transparency: put see-through pictures on white rather than black.
    surface.canvas.clear(Color.WHITE)
    surface.canvas.drawImageRect(source, Rect.makeWH(w.toFloat(), h.toFloat()))
    surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
}.getOrNull()

private const val JPEG_QUALITY = 85
