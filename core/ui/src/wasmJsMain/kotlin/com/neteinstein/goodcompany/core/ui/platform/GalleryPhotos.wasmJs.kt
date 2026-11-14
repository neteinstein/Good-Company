package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.runtime.Composable

/** The browser has no gallery to point back to: the picked picture becomes a thumbnail only (D6). */
@Composable
actual fun rememberGalleryPhotoPicker(onPicked: (PickedPhoto?) -> Unit): () -> Unit =
    rememberImagePicker { image -> onPicked(image?.let { PickedPhoto(it, ref = null, takenOn = null) }) }

/** Nothing to open on the web: references only come from Android and iOS. */
@Composable
actual fun rememberGalleryPhotoOpener(): GalleryPhotoOpener = GalleryPhotoOpener { OpenedPhoto.Unavailable }
