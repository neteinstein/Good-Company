package com.neteinstein.goodcompany

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.ui.platform.dateTaken
import com.neteinstein.goodcompany.core.ui.platform.keepReference
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Reads "Share → Good Company" (ACTION_SEND with text/plain or image/…) into a [SharedContent].
 * Images are downscaled to at most [MAX_SIDE] px and re-encoded as JPEG, small enough for Claude's vision input.
 */
object ShareIntents {
    private const val MAX_SIDE = 1568
    private const val JPEG_QUALITY = 85

    fun read(intent: Intent?, context: Context): SharedContent? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
        val title = (intent.getStringExtra(Intent.EXTRA_TITLE) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT))
            ?.trim()?.takeIf { it.isNotEmpty() }
        val url = text?.let { Regex("https?://\\S+").find(it)?.value }
        val uri = if (intent.type?.startsWith("image/") == true) stream(intent) else null
        val image = uri?.let { readImage(it, context.contentResolver) }
        // D6: keep a reference to the original only when the sender lets us keep access; otherwise thumbnail only.
        val ref = if (image != null) keepReference(context, uri) else null
        val takenOn = if (image != null) dateTaken(context, uri) else null
        return SharedContent(text = text, url = url, image = image, title = title, photoRef = ref, takenOn = takenOn)
            .takeUnless { it.isEmpty }
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
