package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import kotlin.time.Instant

/**
 * Dated notes (issue #103): the old free-text [Friend.notes] becomes one [JournalEntry] per person, so the Notes card
 * lists every note with its date. The database does the same in SQL (migration `23.sqm`); this is the rule for data
 * that arrives the old way (a backup from before #103, the old JSON store).
 *
 * Nothing is lost: [Friend.notes] keeps its text (the column stays until the migration is verified), the entry gets the
 * text exactly as it was, and empty or blank notes make no entry. The entry's id is [idFor], so running it twice adds
 * nothing.
 */
object NotesToJournal {
    /** The id of the entry made from [friend]'s old notes. */
    fun idFor(friend: Friend): String = "note-${friend.id.value}"

    /** The entries [friends]' notes become, dated [at]; none for blank notes or an entry already in [journal]. */
    fun entries(friends: List<Friend>, journal: List<JournalEntry>, at: Instant): List<JournalEntry> {
        val taken = journal.mapTo(HashSet()) { it.id }
        return friends.filter { it.notes.isNotBlank() && idFor(it) !in taken }.map { JournalEntry(idFor(it), it.id, at, it.notes) }
    }

    /**
     * [backup] with its notes as journal entries, dated when it was exported (or [now] without a date), and marked
     * [Backup.notesInJournal]. A backup that is already marked comes back as it is.
     */
    fun convert(backup: Backup, now: Instant): Backup {
        if (backup.notesInJournal) return backup
        val added = entries(backup.friends, backup.journal, backup.exportedAt ?: now)
        return backup.copy(journal = backup.journal + added, notesInJournal = true)
    }
}
