package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Which entry of whose timeline to edit (`LogInteractionRoute` in edit mode, issue #102). [key] is [TimelineEntry.key]. */
data class TimelineEntryRef(val friendId: FriendId, val key: String)

/** Whether the Log sheet can edit [this] (meetups, contacts and journal entries; news has its own dialog). */
val TimelineEntry.editsInLogSheet: Boolean
    get() = this is TimelineEntry.Meetup || this is TimelineEntry.Contact || this is TimelineEntry.Journal

/** What the Log sheet opens with when it edits [this]: the same fields a new log would have. Null when it can't. */
fun TimelineEntry.asLogRequest(friendId: FriendId, zone: TimeZone): LogRequest? = when (this) {
    is TimelineEntry.Meetup -> LogRequest(
        friendIds = hangout.friendIds,
        kind = LogKind.MEETUP,
        date = hangout.at.toLocalDate(zone),
        time = hangout.at.timeOfDay(zone),
        note = hangout.note,
        placeName = hangout.place?.label,
        location = hangout.place?.point,
        activity = hangout.activity,
        activityName = hangout.activityName,
    )
    is TimelineEntry.Contact -> LogRequest(
        friendIds = listOf(interaction.friendId) + others.map { it.id },
        kind = LogKind.parse(interaction.type.name) ?: LogKind.OTHER,
        date = interaction.at.toLocalDate(zone),
        time = interaction.at.timeOfDay(zone),
        note = interaction.note,
        label = interaction.label,
    )
    is TimelineEntry.Journal ->
        LogRequest(listOf(entry.friendId), LogKind.JOURNAL, entry.at.toLocalDate(zone), entry.text, time = entry.at.timeOfDay(zone))
    is TimelineEntry.WhatsNew ->
        LogRequest(listOf(friendId), LogKind.JOURNAL, update.addedAt.toLocalDate(zone), update.text, time = update.addedAt.timeOfDay(zone))
    is TimelineEntry.GiftGiven, is TimelineEntry.Share -> null
}

/** The time of day of [this] in [zone], to the minute (the time row's precision, issue #116). */
private fun Instant.timeOfDay(zone: TimeZone): LocalTime = toLocalDateTime(zone).time.let { LocalTime(it.hour, it.minute) }

private val Place.point: GeoPoint?
    get() {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return GeoPoint(lat, lon)
    }

/**
 * Edits a timeline entry (issue #102): its date, kind, people, place, activity and note, as the Log sheet has them in
 * [request]. The same day keeps the entry's time; a time from the time row (issue #116) moves it there; without one,
 * another day moves it to noon (today: now); no future days.
 *
 * - A meetup stays the same hangout (everyone on it sees the change); its place is matched again (F11) only when the
 *   name or the fix changed, so a named place stays linked.
 * - A call, message… edits every row of its batch: people taken out lose their row, people added get one in the same
 *   batch, and the rows that stay keep their id (an automatic call keeps its automatic id, so a re-sync doesn't add it
 *   again). A WhatsApp day edited as "Said hi" stays a WhatsApp day.
 * - A journal entry keeps its row for its own person; others added get their own entry (journal entries have no batch).
 * - Changing between a meetup, a contact and a journal entry removes the old rows and logs new ones at the same time;
 *   a share the entry was logged from follows it.
 * - "What's new" changes its text and date ([LogRequest.note], [LogRequest.date]).
 *
 * Gifts given and circle shares aren't edited here: a gift opens on its own, a share is only deleted.
 */
class EditTimelineEntryUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val checkIn: CheckInUseCase,
    private val log: LogInteractionUseCase,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(entry: TimelineEntry, request: LogRequest) {
        when (entry) {
            is TimelineEntry.Meetup -> meetup(entry, request)
            is TimelineEntry.Contact -> contact(entry, request)
            is TimelineEntry.Journal -> journal(entry, request)
            is TimelineEntry.WhatsNew -> news(entry, request)
            is TimelineEntry.GiftGiven, is TimelineEntry.Share -> Unit
        }
    }

    private fun people(request: LogRequest): List<FriendId> =
        request.friendIds.distinct().also { require(it.isNotEmpty()) { "Pick at least one friend" } }

    private suspend fun meetup(entry: TimelineEntry.Meetup, request: LogRequest) {
        val h = entry.hangout
        val people = people(request)
        val at = instantFor(h.at, request)
        if (request.kind == LogKind.MEETUP) {
            val place = if (samePlace(h.place, request.placeName, request.location)) h.place else checkIn.place(request.placeName, request.location)
            hangouts.add(
                h.copy(
                    friendIds = people,
                    at = at,
                    place = place,
                    note = request.note.trim(),
                    activity = request.activity,
                    activityName = request.activityName?.trim()?.ifBlank {
                        null
                    }.takeIf {
                        request.activity ==
                            null
                    },
                ),
            )
        } else {
            requireText(request)
            hangouts.delete(h.id)
            relink(entry.attachment, log.logAt(request.copy(friendIds = people), at))
        }
    }

    private suspend fun contact(entry: TimelineEntry.Contact, request: LogRequest) {
        val i = entry.interaction
        val people = people(request)
        val at = instantFor(i.at, request)
        val rows = i.batchId?.let { batch -> interactions.interactions.first().filter { it.batchId == batch } }?.ifEmpty { null } ?: listOf(i)
        val type = request.kind.interactionType
        // An old "together" contact row stays a contact when it's still "in person"; any other change to in person is a meetup.
        if (type == null || (request.kind == LogKind.MEETUP && i.type != InteractionType.MEETUP)) {
            requireText(request)
            rows.forEach { interactions.delete(it.id) }
            relink(entry.attachment, log.logAt(request.copy(friendIds = people), at))
            return
        }
        val newType = if (type == InteractionType.MESSAGE && i.type == InteractionType.WHATSAPP) InteractionType.WHATSAPP else type
        // A single row that gains people starts a batch under its own id, so a share linked to it stays linked.
        val batch = i.batchId ?: i.id.takeIf { people.size > 1 }
        val label = request.label.trim().take(LogInteractionUseCase.LABEL_MAX).takeIf { request.kind == LogKind.OTHER }.orEmpty()
        val byFriend = rows.associateBy { it.friendId }
        val next = people.map { p ->
            (byFriend[p] ?: Interaction(friendId = p, type = newType, at = at, id = ids.next()))
                .copy(type = newType, at = at, note = request.note.trim(), batchId = batch, label = label)
        }
        // Rows are written with INSERT OR IGNORE: take the old ones out first.
        rows.forEach { interactions.delete(it.id) }
        interactions.addAll(next)
    }

    private suspend fun journal(entry: TimelineEntry.Journal, request: LogRequest) {
        val e = entry.entry
        val people = people(request)
        val at = instantFor(e.at, request)
        if (request.kind != LogKind.JOURNAL) {
            interactions.deleteJournal(e.id)
            log.logAt(request.copy(friendIds = people), at)
            return
        }
        val text = requireText(request)
        if (e.friendId !in people) interactions.deleteJournal(e.id)
        people.forEach { p ->
            interactions.upsertJournal(if (p == e.friendId) e.copy(at = at, text = text) else JournalEntry(ids.next(), p, at, text))
        }
    }

    private suspend fun news(entry: TimelineEntry.WhatsNew, request: LogRequest) {
        val text = requireText(request)
        val at = instantFor(entry.update.addedAt, request)
        val f = friends.get(entry.friendId) ?: return
        friends.upsert(f.copy(lifeUpdates = f.lifeUpdates.map { if (it.id == entry.update.id) it.copy(text = text, addedAt = at) else it }))
    }

    /** Journal entries and news need text, as on the Log sheet. */
    private fun requireText(request: LogRequest): String {
        val text = request.note.trim()
        require(request.kind != LogKind.JOURNAL || text.isNotEmpty()) { "A journal entry needs text" }
        return text
    }

    /** The share a meetup or contact was logged from (D6) points at whatever it became. */
    private suspend fun relink(attachment: Attachment?, result: LogResult) {
        attachment ?: return
        val (logId, type) = when (result) {
            is LogResult.Met -> result.hangout.id to InteractionType.MEETUP
            is LogResult.Contacts -> result.interactions.first().let { (it.batchId ?: it.id) to it.type }
            is LogResult.Journal -> return
        }
        interactions.upsertAttachment(attachment.copy(logId = logId, interactionType = type))
    }

    private fun samePlace(old: Place?, name: String?, location: GeoPoint?): Boolean =
        name?.trim()?.ifBlank { null } == old?.label && location == old?.point

    /**
     * The same day and time keep the entry's own instant (to the second). A new time from the time row (issue #116)
     * puts it at that time on the chosen day. Another day with the time left as it was keeps the rule of issue #102:
     * noon (today: now).
     */
    private fun instantFor(old: Instant, request: LogRequest): Instant {
        val zone = clock.timeZone
        val date = request.date ?: old.toLocalDate(zone)
        val newTime = request.time?.takeIf { it != old.timeOfDay(zone) }
        if (date == old.toLocalDate(zone) && newTime == null) return old
        return logInstant(date, newTime, clock.now(), zone)
    }
}
