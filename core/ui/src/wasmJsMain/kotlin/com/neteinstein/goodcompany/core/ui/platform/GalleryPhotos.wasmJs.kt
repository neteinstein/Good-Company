package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.runtime.Composable

/** The browser has no gallery to point back to: the picked picture becomes a thumbnail only (D6). */
@Composable
actual fun rememberGalleryPhotoPicker(onPicked: (PickedPhoto?) -> Unit): () -> Unit =
    rememberImagePicker { image -> onPicked(image?.let { PickedPhoto(it, ref = null, takenOn = null) }) }

/** The browser picks one picture for a note (issue #119), with no gallery to point back to and no videos. */
@Composable
actual fun rememberGalleryMediaPicker(onPicked: (List<PickedMedia>) -> Unit): () -> Unit =
    rememberImagePicker { image -> onPicked(listOfNotNull(image?.let { PickedMedia(it, isVideo = false, ref = null) })) }

/** Nothing to open on the web: references only come from Android and iOS. */
@Composable
actual fun rememberGalleryPhotoOpener(): GalleryPhotoOpener = GalleryPhotoOpener { OpenedPhoto.Unavailable }
