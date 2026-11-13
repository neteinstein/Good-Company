package com.neteinstein.goodcompany.core.ui.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

actual fun createPhotoShrinker(): PhotoShrinker = PhotoShrinker { image ->
    withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        // Decode at the smallest power-of-two step that is still at least the target size, then scale exactly.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ContactPhoto.MAX_SIDE_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val (w, h) = fitWithin(decoded.width, decoded.height, ContactPhoto.MAX_SIDE_PX)
        val scaled = if (w == decoded.width && h == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }
}

private const val JPEG_QUALITY = 85
