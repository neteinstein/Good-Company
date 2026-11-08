package com.neteinstein.goodcompany

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Reads "Share → Good Company" (ACTION_SEND with text/plain or image/…) into a [SharedContent].
 * Images are downscaled to at most [MAX_SIDE] px and re-encoded as JPEG, small enough for Claude's vision input.
 */
object ShareIntents {
    private const val MAX_SIDE = 1568
    private const val JPEG_QUALITY = 85

    fun read(intent: Intent?, resolver: ContentResolver): SharedContent? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
        val url = text?.let { Regex("https?://\\S+").find(it)?.value }
        val body = listOfNotNull(subject, text).distinct().joinToString("\n").ifEmpty { null }
        val image = if (intent.type?.startsWith("image/") == true) stream(intent)?.let { readImage(it, resolver) } else null
        return SharedContent(text = body, url = url, image = image).takeUnless { it.isEmpty }
    }

    private fun stream(intent: Intent): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun readImage(uri: Uri, resolver: ContentResolver): SharedImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            decoded.scale((decoded.width * scale).toInt(), (decoded.height * scale).toInt())
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        SharedImage(out.toByteArray(), "image/jpeg")
    }.getOrNull()
}
