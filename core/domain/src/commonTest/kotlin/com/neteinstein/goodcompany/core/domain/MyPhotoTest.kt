package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.SetMyPhotoUseCase
import com.neteinstein.goodcompany.core.testing.FakeMyPhotoRepository
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Your photo (issue #71): set, replace, remove, and it travels with the backup. */
class MyPhotoTest {
    private val jpeg = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70)
    private val other = byteArrayOf(1, 2, 3)

    @Test
    fun setReplaceAndRemove() = runTest {
        val g = TestGraph()
        assertNull(g.observeMyPhoto().first())

        assertTrue(g.setMyPhoto(jpeg))
        assertEquals(ContactPhoto.of(jpeg), g.observeMyPhoto().first())

        assertTrue(g.setMyPhoto(other))
        assertEquals(ContactPhoto.of(other), g.myPhotoRepo.photo.value)

        g.removeMyPhoto()
        assertNull(g.observeMyPhoto().first())
    }

    @Test
    fun anUnreadablePictureKeepsThePhotoYouHad() = runTest {
        val g = TestGraph()
        g.setMyPhoto(jpeg)
        assertFalse(g.setMyPhoto(null))
        assertFalse(g.setMyPhoto(ByteArray(0)))
        assertEquals(ContactPhoto.of(jpeg), g.myPhotoRepo.photo.value)
    }

    @Test
    fun aLargePictureIsShrunkToAThumbnail() = runTest {
        val repo = FakeMyPhotoRepository()
        val shrink = SetMyPhotoUseCase(repo, PhotoShrinker { byteArrayOf(9, 9) })
        assertTrue(shrink(ByteArray(ContactPhoto.MAX_BYTES + 1) { 1 }))
        assertEquals(ContactPhoto.of(byteArrayOf(9, 9)), repo.photo.value)
        // A picture that can't be shrunk is dropped, not stored at full size.
        val failing = SetMyPhotoUseCase(repo, PhotoShrinker { null })
        assertFalse(failing(ByteArray(ContactPhoto.MAX_BYTES + 1)))
        assertEquals(ContactPhoto.of(byteArrayOf(9, 9)), repo.photo.value)
    }

    @Test
    fun theBackupCarriesYourPhoto() = runTest {
        val g = TestGraph()
        g.setMyPhoto(jpeg)
        val exported = g.exportBackup()
        assertTrue("\"myPhoto\"" in exported)

        g.removeMyPhoto()
        g.restoreBackup(exported)
        assertEquals(ContactPhoto.of(jpeg), g.myPhotoRepo.photo.value)

        // A backup from before your photo existed leaves you with none.
        val older = exported.replace(Regex(",\\s*\"myPhoto\":\\s*\"[^\"]*\""), "")
        assertFalse("myPhoto" in older)
        assertTrue(g.restoreBackup(older) is RestoreResult.Restored)
        assertNull(g.myPhotoRepo.photo.value)
    }
}
