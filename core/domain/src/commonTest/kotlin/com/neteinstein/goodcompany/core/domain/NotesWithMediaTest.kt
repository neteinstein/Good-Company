package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.MediaKind
import com.neteinstein.goodcompany.core.domain.model.NoteMedia
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.usecase.PickedNoteMedia
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.asLogRequest
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #119: a note's own popup, with photos and videos kept as thumbnails plus gallery links. */
class NotesWithMediaTest {
    private val ana = FriendId("ana")
    private val ref = PhotoRef(PhotoPlatform.ANDROID, "content://media/1")

    @Test
    fun aPickedVideoBecomesAThumbnailWithItsReference_andAnUnreadableOneIsDropped() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        val video = assertNotNull(g.prepareNoteMedia(PickedNoteMedia(byteArrayOf(1, 2, 3), MediaKind.VIDEO, ref)))
        assertEquals(MediaKind.VIDEO, video.kind)
        assertEquals(ref, video.ref)
        assertTrue(video.thumbnail.bytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertNull(g.prepareNoteMedia(PickedNoteMedia(ByteArray(0), MediaKind.PHOTO, ref)))
    }

    @Test
    fun savingANote_today_isNow_anEarlierDayNoon_andEditingKeepsTheTimeOnTheSameDay() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        val media = listOf(NoteMedia("m", MediaKind.PHOTO, ContactPhoto("AQID"), ref))
        val today = assertNotNull(g.saveNote(ana, "  Moved to Porto ", LocalDate(2026, 3, 10), media))
        assertEquals(g.clock.now(), today.at)
        assertEquals("Moved to Porto", today.text)
        assertEquals(media, g.interactionRepo.journal.first().single().media)

        val edited = assertNotNull(g.saveNote(ana, "Moved to Porto!", LocalDate(2026, 3, 10), emptyList(), editing = today))
        assertEquals(today.id, edited.id)
        assertEquals(today.at, edited.at)
        assertTrue(g.interactionRepo.journal.first().single().media.isEmpty())

        val earlier = assertNotNull(g.saveNote(ana, "Old", LocalDate(2026, 3, 1), emptyList()))
        assertEquals(at(2026, 3, 1, 12), earlier.at)
        // A day in the future is today.
        assertEquals(g.clock.now(), assertNotNull(g.saveNote(ana, "Later", LocalDate(2026, 4, 1), emptyList())).at)
    }

    @Test
    fun aNoteNeedsWordsOrMedia() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        assertNull(g.saveNote(ana, "   ", LocalDate(2026, 3, 10), emptyList()))
        val onlyMedia = g.saveNote(ana, "", LocalDate(2026, 3, 10), listOf(NoteMedia("m", MediaKind.VIDEO, ContactPhoto("AQID"))))
        assertEquals("", assertNotNull(onlyMedia).text)
    }

    @Test
    fun editingTheTextFromTheLogSheetKeepsTheMedia() = runTest {
        val media = listOf(NoteMedia("m", MediaKind.PHOTO, ContactPhoto("AQID"), ref))
        val note = JournalEntry("j", ana, at(2026, 3, 5), "Hike", media)
        val g = TestGraph(listOf(friend("ana")))
        g.interactionRepo.upsertJournal(note)
        val entry = TimelineEntry.Journal(note)
        g.editTimelineEntry(entry, assertNotNull(entry.asLogRequest(ana, g.clock.timeZone)).copy(note = "Long hike"))
        val saved = g.interactionRepo.journal.first().single()
        assertEquals("Long hike", saved.text)
        assertEquals(media, saved.media)
    }

    @Test
    fun theExplanationIsShownOnce_andIsDeviceOnly() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        g.markMediaExplained()
        assertTrue(g.settingsRepo.settings.first().mediaExplained)
    }
}
