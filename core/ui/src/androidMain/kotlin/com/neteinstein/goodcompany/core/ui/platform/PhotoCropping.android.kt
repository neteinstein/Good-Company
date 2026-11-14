package com.neteinstein.goodcompany.core.ui.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

actual suspend fun cropPhoto(image: ByteArray, crop: PhotoCrop, outWidth: Int, outHeight: Int): ByteArray? = withContext(Dispatchers.Default) {
    runCatching {
        val source = BitmapFactory.decodeByteArray(image, 0, image.size) ?: return@runCatching null
        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        // JPEG has no transparency: put see-through pictures on white rather than black.
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                source,
                Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
                Rect(0, 0, outWidth, outHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.JPEG, CROP_JPEG_QUALITY, stream)
            stream.toByteArray()
        }
    }.getOrNull()
}

private const val CROP_JPEG_QUALITY = 85
