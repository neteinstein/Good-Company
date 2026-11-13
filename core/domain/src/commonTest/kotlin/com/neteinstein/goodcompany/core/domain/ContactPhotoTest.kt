package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import com.neteinstein.goodcompany.core.domain.repository.photoOf
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.domain.usecase.HomeWidgetSummary
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.VCardParser
import com.neteinstein.goodcompany.core.domain.usecase.WidgetSnapshot
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contact photos (issue #15): the type, where they come from, and where they go. */
class ContactPhotoTest {
    private val jpeg = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70)
    private val thumb = ContactPhoto.of(jpeg)!!
    private val otherThumb = ContactPhoto.of(byteArrayOf(1, 2, 3))!!

    // --- The type ---

    @Test
    fun keepsTheBytes_andComparesByContent() {
        assertContentEquals(jpeg, thumb.bytes())
        assertEquals(thumb, ContactPhoto.of(jpeg.copyOf()))
        assertEquals("\"${Base64.encode(jpeg)}\"", Json.encodeToString(ContactPhoto.serializer(), thumb))
    }

    @Test
    fun nothingEmptyOrTooLarge_isNoPhoto() {
        assertNull(ContactPhoto.of(null))
        assertNull(ContactPhoto.of(ByteArray(0)))
        assertNull(ContactPhoto.of(ByteArray(ContactPhoto.MAX_BYTES + 1)))
        assertNotNull(ContactPhoto.of(ByteArray(ContactPhoto.MAX_BYTES)))
        assertEquals(0, ContactPhoto("not base64!").bytes().size)
    }

    @Test
    fun largePictures_areShrunkFirst_orDroppedWhenTheyCantBe() = runTest {
        val big = ByteArray(ContactPhoto.MAX_BYTES * 2)
        assertEquals(thumb, PhotoShrinker.None.photoOf(jpeg))
        assertNull(PhotoShrinker.None.photoOf(big))
        assertEquals(thumb, PhotoShrinker { jpeg }.photoOf(big))
        assertNull(PhotoShrinker { null }.photoOf(big))
        assertNull(PhotoShrinker { error("bad image") }.photoOf(big))
        assertNull(PhotoShrinker { jpeg }.photoOf(null))
    }

    // --- Address book sync ---

    @Test
    fun aSync_bringsTheContactsPhoto_andAChangedOne() {
        val ana = friend("contact-1") { copy(contactId = "1") }
        val synced = ContactMerger.merge(listOf(ana), listOf(ContactRecord("1", "Ana", emptyList(), null, thumb)), addNew = false)
        assertEquals(thumb, synced.single().photo)
        val changed = ContactMerger.merge(synced, listOf(ContactRecord("1", "Ana", emptyList(), null, otherThumb)), addNew = false)
        assertEquals(otherThumb, changed.single().photo)
        assertEquals(thumb, ContactMerger.merge(emptyList(), listOf(ContactRecord("2", "Rui", emptyList(), null, thumb))).single().photo)
    }

    @Test
    fun aContactWithoutAPhoto_neverClearsOne() {
        val ana = friend("contact-1") { copy(contactId = "1", photo = thumb) }
        val synced = ContactMerger.merge(listOf(ana), listOf(ContactRecord("1", "Ana", emptyList(), null)), addNew = false)
        assertEquals(thumb, synced.single().photo)
    }

    // --- Contact picker ---

    @Test
    fun pickingSomeoneAgain_refreshesTheirPhoto_butNeverClearsIt() = runTest {
        val g = TestGraph()
        g.importPickedContacts(listOf(ContactRecord("1", "Ana", emptyList(), null, thumb)))
        assertEquals(thumb, g.friendRepo.get(FriendId("contact-1"))!!.photo)
        g.importPickedContacts(listOf(ContactRecord("1", "Ana", emptyList(), null, otherThumb)))
        assertEquals(otherThumb, g.friendRepo.get(FriendId("contact-1"))!!.photo)
        val again = g.importPickedContacts(listOf(ContactRecord("1", "Ana", emptyList(), null)))
        assertEquals(0, again.updated)
        assertEquals(otherThumb, g.friendRepo.get(FriendId("contact-1"))!!.photo)
    }

    // --- .vcf files ---

    private fun vcard(photoLine: String, name: String = "Ana Silva") = "BEGIN:VCARD\nVERSION:3.0\nFN:$name\n$photoLine\nEND:VCARD\n"

    @Test
    fun vcf_readsInlinePhotosInEveryVersion() {
        val b64 = Base64.encode(jpeg)
        val folded = b64.chunked(4).joinToString("\n  ")
        listOf(
            "PHOTO;ENCODING=b;TYPE=JPEG:$b64",
            "PHOTO;TYPE=JPEG;ENCODING=BASE64:$folded",
            "PHOTO;ENCODING=BASE64;JPEG:$b64",
            "PHOTO:data:image/jpeg;base64,$b64",
            "item1.PHOTO;VALUE=uri:data:image/jpeg;base64,$folded",
        ).forEach { line ->
            assertContentEquals(jpeg, VCardParser.parse(vcard(line)).single().photo, line)
        }
    }

    @Test
    fun vcf_skipsLinksAndBrokenPhotos() {
        listOf(
            "PHOTO;VALUE=uri:https://example.com/ana.jpg",
            "PHOTO:https://example.com/ana.jpg",
            "PHOTO;ENCODING=b:@@not base64@@",
            "PHOTO:data:image/jpeg,rawdata",
            "PHOTO;ENCODING=b:",
        ).forEach { line ->
            val card = VCardParser.parse(vcard(line)).single()
            assertEquals("Ana Silva", card.name)
            assertNull(card.photo, line)
        }
        // A photo stays with its own card.
        val cards = VCardParser.parse(vcard("PHOTO;ENCODING=b:${Base64.encode(jpeg)}") + vcard("NOTE:x", name = "Rui"))
        assertNotNull(cards[0].photo)
        assertNull(cards[1].photo)
        assertEquals(VCardParser.parse(vcard("NOTE:x"))[0], VCardParser.parse(vcard("NOTE:y"))[0])
    }

    @Test
    fun vcfImport_addsPhotos_shrinksLargeOnes_andOnlyFillsGaps() = runTest {
        val g = TestGraph(friends = listOf(friend("rui", "Rui Costa") { copy(photo = otherThumb) }, friend("eva", "Eva Lima")))
        val big = ByteArray(ContactPhoto.MAX_BYTES * 3) { 7 }
        val shrunk = mutableListOf<Int>()
        val import = ImportContactsFileUseCase(g.friendRepo, SequentialIds()) { image ->
            shrunk += image.size
            jpeg
        }
        val vcf = vcard("PHOTO;ENCODING=b:${Base64.encode(big)}", "Ana Silva") +
            vcard("PHOTO;ENCODING=b:${Base64.encode(jpeg)}", "Rui Costa") +
            vcard("PHOTO;ENCODING=b:${Base64.encode(jpeg)}", "Eva Lima")

        val result = import(vcf)

        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(listOf(big.size), shrunk, "only the large picture is shrunk, and only for someone without a photo")
        val all = g.friendRepo.friends.value.associateBy { it.name }
        assertEquals(thumb, all.getValue("Ana Silva").photo)
        assertEquals(otherThumb, all.getValue("Rui Costa").photo, "an existing photo is kept")
        assertEquals(thumb, all.getValue("Eva Lima").photo)
    }

    // --- Widget ---

    @Test
    fun theWidget_getsThePhoto_butNotWhenPrivate() {
        val ana = friend("ana", "Ana Silva") { copy(photo = thumb) }
        val due = Relationships.MeetupStatus(ana, lastSeenDate = null, dueDate = LocalDate(2026, 3, 1), daysSinceLastSeen = null, cadenceDays = 14)
        val today = LocalDate(2026, 3, 10)
        val shown = WidgetSnapshot.from(HomeWidgetSummary(null, due, isPrivate = false, today))
        assertEquals(thumb.base64, shown.catchUp?.photo)
        val private = WidgetSnapshot.from(HomeWidgetSummary(null, due, isPrivate = true, today))
        assertNull(private.catchUp)
        assertTrue(thumb.base64 !in Json.encodeToString(WidgetSnapshot.serializer(), private))
    }
}
