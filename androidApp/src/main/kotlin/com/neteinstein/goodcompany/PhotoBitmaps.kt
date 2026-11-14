package com.neteinstein.goodcompany

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto

/** Contact photos as small bitmaps for surfaces outside Compose (home-screen widget, app-icon shortcuts). */
internal object PhotoBitmaps {
    /** The photo centre-cropped to a square of at most [maxSide] px; null when it can't be decoded. */
    fun square(photo: ContactPhoto, maxSide: Int): Bitmap? = draw(photo, maxSide, round = false)

    /** Like [square], cropped to a circle (RemoteViews can't clip). */
    fun round(photo: ContactPhoto, maxSide: Int): Bitmap? = draw(photo, maxSide, round = true)

    private fun draw(photo: ContactPhoto, maxSide: Int, round: Boolean): Bitmap? = runCatching {
        val bytes = photo.bytes()
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val side = minOf(source.width, source.height, maxSide)
        val out = createBitmap(side, side)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (round) {
            canvas.drawCircle(side / 2f, side / 2f, side / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        val crop = minOf(source.width, source.height)
        val from = Rect((source.width - crop) / 2, (source.height - crop) / 2, (source.width + crop) / 2, (source.height + crop) / 2)
        canvas.drawBitmap(source, from, Rect(0, 0, side, side), paint)
        out
    }.getOrNull()
}
