package com.neteinstein.goodcompany.core.ui.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Twice the stored thumbnail size, like the friend-photo picker, so the shrinker has detail to work with. */
private const val GALLERY_PICK_MAX_SIDE = 512

/**
 * The Photo Picker (no gallery permission). Its URIs accept a persistable read permission, so the reference keeps
 * working after a restart; when that is refused the photo is kept as a thumbnail only.
 */
@Composable
actual fun rememberGalleryPhotoPicker(onPicked: (PickedPhoto?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            callback.value(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { uprightJpeg(it, GALLERY_PICK_MAX_SIDE) }
                PickedPhoto(bytes ?: ByteArray(0), keepReference(context, uri), dateTaken(context, uri))
            }
            callback.value(picked)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * Keeps read access to [uri] across restarts. Works for Photo Picker and document URIs; other apps' MediaStore URIs
 * shared through the share sheet usually refuse it, and then there is no reference (D6: thumbnail only).
 */
fun keepReference(context: Context, uri: Uri): PhotoRef? = runCatching {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    PhotoRef(PhotoPlatform.ANDROID, uri.toString())
}.getOrNull()

/** The photo's own date (`DATE_TAKEN`), when the provider exposes it. */
fun dateTaken(context: Context, uri: Uri): LocalDate? = runCatching {
    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) {
            Instant.fromEpochMilliseconds(c.getLong(0)).toLocalDateTime(TimeZone.currentSystemDefault()).date
        } else {
            null
        }
    }
}.getOrNull()

/** Opens the original in the system viewer; without a viewer it is loaded into the app; gone or not allowed → unavailable. */
@Composable
actual fun rememberGalleryPhotoOpener(): GalleryPhotoOpener {
    val context = LocalContext.current
    return remember(context) {
        GalleryPhotoOpener { ref ->
            if (ref.platform != PhotoPlatform.ANDROID) return@GalleryPhotoOpener OpenedPhoto.Unavailable
            val uri = Uri.parse(ref.value)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            } ?: return@GalleryPhotoOpener OpenedPhoto.Unavailable
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(view)
                OpenedPhoto.Shown
            } catch (_: ActivityNotFoundException) {
                withContext(Dispatchers.Default) { uprightJpeg(bytes, VIEWER_MAX_SIDE) }?.let(OpenedPhoto::Loaded) ?: OpenedPhoto.Unavailable
            } catch (_: SecurityException) {
                OpenedPhoto.Unavailable
            }
        }
    }
}

private const val VIEWER_MAX_SIDE = 2048
