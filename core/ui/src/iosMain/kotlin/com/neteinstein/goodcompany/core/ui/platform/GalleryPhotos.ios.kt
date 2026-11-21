package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHImageContentModeAspectFit
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * `PHPickerViewController` on the shared photo library: it runs out of process (no library permission to pick) and
 * hands back the asset's `localIdentifier`, the reference D6 keeps. The share extension can't get one (it receives a
 * file), so this is the only path on iOS that keeps a reference.
 */
@Composable
actual fun rememberGalleryPhotoPicker(onPicked: (PickedPhoto?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { GalleryPickerDelegate { callback.value(it) } }
    DisposableEffect(Unit) { onDispose { delegate.onPicked = {} } }
    return {
        val config = PHPickerConfiguration(photoLibrary = PHPhotoLibrary.sharedPhotoLibrary())
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = 1
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

/** Obj-C delegates must be classes, not objects. */
private class GalleryPickerDelegate(var onPicked: (PickedPhoto?) -> Unit) :
    NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onPicked(null)
            return
        }
        val ref = result.assetIdentifier?.let { PhotoRef(PhotoPlatform.IOS, it) }
        result.itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data: NSData?, _ ->
            val jpeg = data?.let { UIImage(data = it) }?.let(::uprightJpeg)
            dispatch_async(dispatch_get_main_queue()) { onPicked(PickedPhoto(jpeg ?: ByteArray(0), ref, takenOn = null)) }
        }
    }
}

private const val IMAGE_TYPE = "public.image"
private const val MOVIE_TYPE = "public.movie"

/**
 * Photos and videos for a note (issue #119): `PHPicker` on the shared library with both filters, several at once.
 * A video's preview is its first frame (`AVAssetImageGenerator` on the file the picker hands over for the call).
 */
@Composable
actual fun rememberGalleryMediaPicker(onPicked: (List<PickedMedia>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { MediaPickerDelegate { callback.value(it) } }
    DisposableEffect(Unit) { onDispose { delegate.onPicked = {} } }
    return {
        val config = PHPickerConfiguration(photoLibrary = PHPhotoLibrary.sharedPhotoLibrary())
        config.filter = PHPickerFilter.anyFilterMatchingSubfilters(listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter))
        config.selectionLimit = MAX_NOTE_MEDIA.toLong()
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class MediaPickerDelegate(var onPicked: (List<PickedMedia>) -> Unit) :
    NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) {
            onPicked(emptyList())
            return
        }
        val picked = arrayOfNulls<PickedMedia>(results.size)
        var left = results.size
        results.forEachIndexed { i, result ->
            val ref = result.assetIdentifier?.let { PhotoRef(PhotoPlatform.IOS, it) }
            val done: (PickedMedia) -> Unit = { media ->
                dispatch_async(dispatch_get_main_queue()) {
                    picked[i] = media
                    left -= 1
                    if (left == 0) onPicked(picked.filterNotNull())
                }
            }
            if (result.itemProvider.hasItemConformingToTypeIdentifier(MOVIE_TYPE)) {
                result.itemProvider.loadFileRepresentationForTypeIdentifier(MOVIE_TYPE) { url: NSURL?, _ ->
                    // The file is only there during this call: take the frame now.
                    done(PickedMedia(url?.let(::firstFrame)?.let(::uprightJpeg) ?: ByteArray(0), isVideo = true, ref = ref))
                }
            } else {
                result.itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data: NSData?, _ ->
                    done(PickedMedia(data?.let { UIImage(data = it) }?.let(::uprightJpeg) ?: ByteArray(0), isVideo = false, ref = ref))
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun firstFrame(url: NSURL): UIImage? {
    val generator = AVAssetImageGenerator(asset = AVURLAsset(uRL = url, options = null))
    generator.appliesPreferredTrackTransform = true
    val image = generator.copyCGImageAtTime(CMTimeMake(value = 0, timescale = 1), actualTime = null, error = null) ?: return null
    return UIImage.imageWithCGImage(image).also { CGImageRelease(image) }
}

/**
 * Loads the original through `PHImageManager`. Reading an asset needs photo library access, so it's asked for here,
 * the first time an original is opened (the user may grant limited access). Without access, or when the photo was
 * deleted, the app shows the thumbnail instead.
 */
@Composable
actual fun rememberGalleryPhotoOpener(): GalleryPhotoOpener = remember {
    GalleryPhotoOpener { ref ->
        if (ref.platform != PhotoPlatform.IOS || !photoAccess()) return@GalleryPhotoOpener OpenedPhoto.Unavailable
        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(ref.value), null).firstObject as? PHAsset
            ?: return@GalleryPhotoOpener OpenedPhoto.Unavailable
        loadImage(asset)?.let { UIImageJPEGRepresentation(it, 0.9)?.toByteArray() }?.let(OpenedPhoto::Loaded)
            ?: OpenedPhoto.Unavailable
    }
}

private suspend fun photoAccess(): Boolean {
    val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
    val granted = if (status == PHAuthorizationStatusNotDetermined) {
        suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { answer -> cont.resume(answer) }
        }
    } else {
        status
    }
    return granted == PHAuthorizationStatusAuthorized || granted == PHAuthorizationStatusLimited
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun loadImage(asset: PHAsset): UIImage? = suspendCancellableCoroutine { cont ->
    val options = PHImageRequestOptions().apply {
        networkAccessAllowed = true
        deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
    }
    var done = false
    PHImageManager.defaultManager().requestImageForAsset(
        asset,
        targetSize = CGSizeMake(VIEWER_MAX_SIDE, VIEWER_MAX_SIDE),
        contentMode = PHImageContentModeAspectFit,
        options = options,
    ) { image, _ ->
        if (!done) {
            done = true
            cont.resume(image)
        }
    }
}

private const val VIEWER_MAX_SIDE = 2048.0
