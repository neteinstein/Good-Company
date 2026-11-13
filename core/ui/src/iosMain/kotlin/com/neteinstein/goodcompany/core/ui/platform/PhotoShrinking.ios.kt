package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual fun createPhotoShrinker(): PhotoShrinker = PhotoShrinker { image ->
    runCatching {
        val source = Image.makeFromEncoded(image)
        val (w, h) = fitWithin(source.width, source.height, ContactPhoto.MAX_SIDE_PX)
        val surface = Surface.makeRasterN32Premul(w, h)
        // JPEG has no transparency: put see-through pictures on white rather than black.
        surface.canvas.clear(Color.WHITE)
        surface.canvas.drawImageRect(source, Rect.makeWH(w.toFloat(), h.toFloat()))
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)?.bytes
    }.getOrNull()
}

private const val JPEG_QUALITY = 85
