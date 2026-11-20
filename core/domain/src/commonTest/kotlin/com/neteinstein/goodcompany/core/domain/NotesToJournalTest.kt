package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.usecase.NotesToJournal
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class NotesToJournalTest {
    private val now = Instant.parse("2026-11-20T10:00:00Z")
    private val exported = Instant.parse("2026-03-01T09:00:00Z")

    @Test
    fun eachNoteBecomesOneDatedNote_withTheTextExactlyAsItWas() {
        val text = "Partner: Tiago.\nAllergic to peanuts.  "
        val backup = Backup(friends = listOf(friend("ana") { copy(notes = text) }), exportedAt = exported)
        val converted = NotesToJournal.convert(backup, now)
        assertEquals(listOf(JournalEntry("note-ana", FriendId("ana"), exported, text)), converted.journal)
        assertTrue(converted.notesInJournal)
        // The free text stays where it was too: nothing is lost while the migration is verified.
        assertEquals(text, converted.friends.single().notes)
    }

    @Test
    fun emptyOrBlankNotes_makeNoEntry() {
        val friends = listOf(
            friend("a") { copy(notes = "") },
            friend("b") { copy(notes = "   ") },
            friend("c") { copy(notes = "\n\t \r\n") },
        )
        assertTrue(NotesToJournal.entries(friends, emptyList(), now).isEmpty())
        assertTrue(NotesToJournal.convert(Backup(friends = friends), now).journal.isEmpty())
    }

    @Test
    fun withoutAnExportDate_theNoteIsDatedNow_andRunningTwiceAddsNothing() {
        val backup = Backup(friends = listOf(friend("ana") { copy(notes = "Loves hiking") }))
        val once = NotesToJournal.convert(backup, now)
        assertEquals(now, once.journal.single().at)
        assertSame(once, NotesToJournal.convert(once, now))
        // An entry with the same id already there (e.g. the database's migration ran) is kept, not doubled.
        assertEquals(once.journal, NotesToJournal.entries(backup.friends, emptyList(), now))
        assertTrue(NotesToJournal.entries(backup.friends, once.journal, now).isEmpty())
    }

    @Test
    fun aBackupFromThisBuild_isLeftAsItIs() {
        val backup = Backup(friends = listOf(friend("ana") { copy(notes = "x") }), notesInJournal = true)
        assertSame(backup, NotesToJournal.convert(backup, now))
    }
}
