package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.MediaKind
import com.neteinstein.goodcompany.core.domain.model.NoteMedia
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.repository.photoOf
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.LocalDate

/** A photo or video just picked for a note (issue #119): a preview image, what it is and the gallery reference. */
class PickedNoteMedia(val preview: ByteArray, val kind: MediaKind, val ref: PhotoRef?)

/** Turns a picked photo or video into a note's [NoteMedia]: a shrunk thumbnail only (null when it can't be read). */
class PrepareNoteMediaUseCase(private val shrinker: PhotoShrinker, private val ids: IdGenerator) {
    suspend operator fun invoke(picked: PickedNoteMedia): NoteMedia? =
        shrinker.photoOf(picked.preview)?.let { NoteMedia(ids.next(), picked.kind, it, picked.ref) }
}

/**
 * The note popup's Save (issue #119): a new note, or [editing] changed. The date is the day picked (today: now, an
 * earlier day: noon, as logging does); an edit on the same day keeps its time. A note needs words or media.
 */
class SaveNoteUseCase(
    private val interactions: InteractionRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        friendId: FriendId,
        text: String,
        date: LocalDate,
        media: List<NoteMedia>,
        editing: JournalEntry? = null,
    ): JournalEntry? {
        val words = text.trim()
        if (words.isEmpty() && media.isEmpty()) return null
        val zone = clock.timeZone
        val day = minOf(date, clock.today())
        val at = if (editing != null && editing.at.toLocalDate(zone) == day) editing.at else logInstant(day, null, clock.now(), zone)
        val entry = (editing ?: JournalEntry(ids.next(), friendId, at, words)).copy(at = at, text = words, media = media.distinctBy { it.id })
        interactions.upsertJournal(entry)
        return entry
    }
}

/** The first time media goes on a note, the app says what it keeps (issue #119); then never again on this device. */
class MarkMediaExplainedUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke() = settings.update { it.copy(mediaExplained = true) }
}
