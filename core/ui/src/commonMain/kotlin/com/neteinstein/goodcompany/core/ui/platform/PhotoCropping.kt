package com.neteinstein.goodcompany.core.ui.platform

/** A part of a picture, in the picture's own pixels: a square for an avatar, a wide one for a cover (issue #88). */
data class PhotoCrop(val left: Int, val top: Int, val width: Int, val height: Int) {
    companion object {
        fun square(left: Int, top: Int, side: Int) = PhotoCrop(left, top, side, side)
    }
}

/**
 * Cuts [crop] out of [image] (an upright JPEG or PNG, as the image picker hands back) and scales it to [outWidth] ×
 * [outHeight] px, as JPEG (F14). Null when the picture can't be read.
 */
expect suspend fun cropPhoto(image: ByteArray, crop: PhotoCrop, outWidth: Int, outHeight: Int): ByteArray?
