package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.MyPhotoRepository
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import com.neteinstein.goodcompany.core.domain.repository.photoOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Your own photo (issue #71), or null while none is set. */
class ObserveMyPhotoUseCase(private val photos: MyPhotoRepository) {
    operator fun invoke(): Flow<ContactPhoto?> = photos.photo.distinctUntilChanged()
}

/**
 * Sets or replaces your photo from a picked (and cropped) picture, shrunk to a thumbnail like a friend's photo. Returns
 * false, and keeps the photo you had, when the picture can't be read.
 */
class SetMyPhotoUseCase(private val photos: MyPhotoRepository, private val shrinker: PhotoShrinker) {
    suspend operator fun invoke(image: ByteArray?): Boolean {
        val photo = shrinker.photoOf(image) ?: return false
        photos.set(photo)
        return true
    }
}

/** Removes your photo: the tab headers show the gear again. */
class RemoveMyPhotoUseCase(private val photos: MyPhotoRepository) {
    suspend operator fun invoke() = photos.set(null)
}
