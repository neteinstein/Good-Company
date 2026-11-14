package com.neteinstein.goodcompany.core.ui.platform

import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual suspend fun cropPhoto(image: ByteArray, crop: PhotoCrop, outWidth: Int, outHeight: Int): ByteArray? = runCatching {
    val source = Image.makeFromEncoded(image)
    val surface = Surface.makeRasterN32Premul(outWidth, outHeight)
    // JPEG has no transparency: put see-through pictures on white rather than black.
    surface.canvas.clear(Color.WHITE)
    surface.canvas.drawImageRect(
        source,
        Rect.makeXYWH(crop.left.toFloat(), crop.top.toFloat(), crop.width.toFloat(), crop.height.toFloat()),
        Rect.makeWH(outWidth.toFloat(), outHeight.toFloat()),
    )
    surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, CROP_JPEG_QUALITY)?.bytes
}.getOrNull()

private const val CROP_JPEG_QUALITY = 85
