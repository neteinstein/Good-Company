package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberImagePicker(onPicked: (image: ByteArray?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { ImagePickerDelegate { callback.value(it) } }
    DisposableEffect(Unit) { onDispose { delegate.onPicked = {} } }
    return {
        // The photo library picker runs out of process: no photo library permission needed.
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

/** Obj-C delegates must be classes, not objects. */
private class ImagePickerDelegate(var onPicked: (ByteArray?) -> Unit) :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        // Empty (not null, which means cancelled) when the picture couldn't be read, so the app can say so.
        onPicked(image?.let(::uprightJpeg) ?: ByteArray(0))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onPicked(null)
    }
}

/**
 * Redraws the picture upright (UIKit applies the camera's rotation; the JPEG decoder used later wouldn't) and no
 * larger than needed, then encodes it as JPEG.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun uprightJpeg(image: UIImage): ByteArray? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, PICKED_MAX_SIDE / maxOf(width, height))
    val w = width * scale
    val h = height * scale
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val upright = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format).imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, w, h))
    }
    return UIImageJPEGRepresentation(upright, 0.9)?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray? {
    val size = length.toInt()
    if (size == 0) return null
    return ByteArray(size).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) } }
}

/** Twice the stored size, so the shrinker still has detail to work with. */
private const val PICKED_MAX_SIDE = 512.0
