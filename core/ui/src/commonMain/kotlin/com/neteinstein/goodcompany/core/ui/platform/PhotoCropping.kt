package com.neteinstein.goodcompany.core.ui.platform

/** A square part of a picture, in the picture's own pixels. */
data class PhotoCrop(val left: Int, val top: Int, val side: Int)

/**
 * Cuts [crop] out of [image] (an upright JPEG or PNG, as the image picker hands back) and scales it to [outSide] px
 * square, as JPEG (F14). Null when the picture can't be read.
 */
expect suspend fun cropPhoto(image: ByteArray, crop: PhotoCrop, outSide: Int): ByteArray?
