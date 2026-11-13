package com.neteinstein.goodcompany.core.ui.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

actual fun createPhotoShrinker(): PhotoShrinker = PhotoShrinker { image ->
    withContext(Dispatchers.Default) { uprightJpeg(image, ContactPhoto.MAX_SIDE_PX) }
}

/**
 * Decodes [image], turns it upright (camera photos carry their rotation in EXIF, which BitmapFactory ignores), scales
 * it so its longest side is at most [maxSide] and encodes it as JPEG. Null when it isn't a readable image.
 */
internal fun uprightJpeg(image: ByteArray, maxSide: Int): ByteArray? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    // Decode at the smallest power-of-two step that is still at least the target size, then scale exactly.
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@runCatching null
    val (w, h) = fitWithin(decoded.width, decoded.height, maxSide)
    val matrix = Matrix().apply {
        postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
        postRotate(rotationOf(image))
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    ByteArrayOutputStream().use { out ->
        upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}.getOrNull()

private fun rotationOf(image: ByteArray): Float = runCatching {
    when (ExifInterface(ByteArrayInputStream(image)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
}.getOrDefault(0f)

private const val JPEG_QUALITY = 85
