package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * One line of a friend's history. Meetups stay [Hangout]s, calls/messages/emails/video calls/WhatsApp days are
 * [Interaction]s, and journal entries, "What's new" and gifts given are their own kinds; the timeline shows them
 * together, newest first.
 */
sealed interface TimelineEntry {
    /** Stable across rebuilds, for list keys and deletes. */
    val key: String
    val at: Instant

    /** Whether it can be removed from the timeline (with Undo). Gifts are managed in their own section. */
    val deletable: Boolean get() = true

    /**
     * A meetup; [others] are everyone else who was there (a group meetup shows on every member's timeline).
     * [attachment] is the shared photo or text it was logged from (D6), shown with its thumbnail.
     */
    data class Meetup(val hangout: Hangout, val others: List<Friend>, val attachment: Attachment? = null) : TimelineEntry {
        override val key get() = "hangout-${hangout.id}"
        override val at get() = hangout.at
    }

    /**
     * A call, message, email, video call or WhatsApp day; [others] were logged in the same go ([Interaction.batchId]).
     * [attachment] is the share it was logged from, if any.
     */
    data class Contact(val interaction: Interaction, val others: List<Friend>, val attachment: Attachment? = null) : TimelineEntry {
        override val key get() = "interaction-${interaction.id}"
        override val at get() = interaction.at
    }

    data class Journal(val entry: JournalEntry) : TimelineEntry {
        override val key get() = "journal-${entry.id}"
        override val at get() = entry.at
    }

    data class WhatsNew(val friendId: FriendId, val update: LifeUpdate) : TimelineEntry {
        override val key get() = "update-${update.id}"
        override val at get() = update.addedAt
    }

    data class GiftGiven(val gift: Gift, val date: LocalDate, override val at: Instant) : TimelineEntry {
        override val key get() = "gift-${gift.id}"
        override val deletable get() = false
    }

    /**
     * Something shared to a circle that isn't shown on a meetup (issue #88: a circle's timeline). A share logged as a
     * meetup shows once, on that [Meetup].
     */
    data class Share(val attachment: Attachment) : TimelineEntry {
        override val key get() = "share-${attachment.id}"
        override val at get() = attachment.at
    }
}

/** The filter chips on the timeline screen. */
enum class TimelineFilter {
    ALL,
    TOGETHER,
    CALLS,
    MESSAGES,
    NOTES,
    GIFTS,
    ;

    fun matches(entry: TimelineEntry): Boolean = when (this) {
        ALL -> true
        TOGETHER -> entry is TimelineEntry.Meetup || (entry is TimelineEntry.Contact && entry.interaction.type == InteractionType.MEETUP) ||
            entry is TimelineEntry.Share
        CALLS -> entry is TimelineEntry.Contact && entry.interaction.type in CALL_TYPES
        MESSAGES -> entry is TimelineEntry.Contact && entry.interaction.type in MESSAGE_TYPES
        NOTES -> entry is TimelineEntry.Journal || entry is TimelineEntry.WhatsNew
        GIFTS -> entry is TimelineEntry.GiftGiven
    }

    companion object {
        val CALL_TYPES = setOf(InteractionType.CALL, InteractionType.VIDEO_CALL)
        val MESSAGE_TYPES = setOf(InteractionType.MESSAGE, InteractionType.WHATSAPP, InteractionType.EMAIL)
    }
}

/** A month heading and its entries, newest first. */
data class TimelineMonth(val year: Int, val month: Month, val entries: List<TimelineEntry>)

/** Pure timeline maths: build, filter and group by month. */
object Timeline {
    /** Gifts carry a date only; they sort as if given at noon. */
    private val GIFT_TIME = LocalTime(12, 0)

    /** Every entry about [friendId], newest first. Plans that happened show up through the meetup they created. */
    fun build(
        friendId: FriendId,
        friends: List<Friend>,
        hangouts: List<Hangout>,
        interactions: List<Interaction>,
        journal: List<JournalEntry>,
        zone: TimeZone,
        attachments: List<Attachment> = emptyList(),
    ): List<TimelineEntry> {
        val friend = friends.firstOrNull { it.id == friendId } ?: return emptyList()
        val byId = friends.associateBy { it.id }
        val byLog = attachments.filter { it.countsAsInteraction && it.logId != null }.associateBy { it.logId }
        val batches = interactions.filter { it.batchId != null }.groupBy { it.batchId }
        val entries = buildList<TimelineEntry> {
            hangouts.filter { friendId in it.friendIds }.forEach { h ->
                add(TimelineEntry.Meetup(h, h.friendIds.filter { it != friendId }.mapNotNull(byId::get), byLog[h.id]))
            }
            interactions.filter { it.friendId == friendId }.forEach { i ->
                val others = i.batchId?.let { batches[it] }.orEmpty().map { it.friendId }.filter { it != friendId }.distinct()
                add(TimelineEntry.Contact(i, others.mapNotNull(byId::get), byLog[i.batchId ?: i.id]))
            }
            journal.filter { it.friendId == friendId }.forEach { add(TimelineEntry.Journal(it)) }
            friend.lifeUpdates.forEach { add(TimelineEntry.WhatsNew(friendId, it)) }
            friend.gifts.forEach { g ->
                val given = g.givenOn ?: return@forEach
                add(TimelineEntry.GiftGiven(g, given, given.atTime(GIFT_TIME).toInstant(zone)))
            }
        }
        return entries.sortedWith(compareByDescending<TimelineEntry> { it.at }.thenBy { it.key })
    }

    fun filter(entries: List<TimelineEntry>, filter: TimelineFilter): List<TimelineEntry> = entries.filter(filter::matches)

    /** [entries] (newest first) under month headings, newest month first. */
    fun byMonth(entries: List<TimelineEntry>, zone: TimeZone): List<TimelineMonth> =
        entries.groupBy { it.at.toLocalDate(zone).let { d -> d.year to d.month } }
            .map { (ym, list) -> TimelineMonth(ym.first, ym.second, list) }
}

/** A friend's whole timeline, newest first (empty when the friend is gone). */
class ObserveTimelineUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val clock: AppClock,
) {
    operator fun invoke(id: FriendId): Flow<List<TimelineEntry>> = combine(
        friends.friends,
        hangouts.hangouts,
        interactions.interactions,
        interactions.journal,
        interactions.attachments,
    ) { fs, hs, ints, journal, attachments -> Timeline.build(id, fs, hs, ints, journal, clock.timeZone, attachments) }
}

/** Removes an entry from the timeline (a whole group meetup goes for everyone). Put it back with [RestoreTimelineEntryUseCase]. */
class DeleteTimelineEntryUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
) {
    suspend operator fun invoke(entry: TimelineEntry) {
        when (entry) {
            is TimelineEntry.Meetup -> hangouts.delete(entry.hangout.id)
            is TimelineEntry.Contact -> interactions.delete(entry.interaction.id)
            is TimelineEntry.Journal -> interactions.deleteJournal(entry.entry.id)
            is TimelineEntry.WhatsNew -> friends.get(entry.friendId)?.let { f ->
                friends.upsert(f.copy(lifeUpdates = f.lifeUpdates.filterNot { it.id == entry.update.id }))
            }
            is TimelineEntry.GiftGiven -> Unit
            is TimelineEntry.Share -> interactions.deleteAttachment(entry.attachment.id)
        }
    }
}

/** Puts back an entry deleted a moment ago (snackbar "Undo"). */
class RestoreTimelineEntryUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
) {
    suspend operator fun invoke(entry: TimelineEntry) {
        when (entry) {
            is TimelineEntry.Meetup -> hangouts.add(entry.hangout)
            is TimelineEntry.Contact -> interactions.addAll(listOf(entry.interaction))
            is TimelineEntry.Journal -> interactions.upsertJournal(entry.entry)
            is TimelineEntry.WhatsNew -> friends.get(entry.friendId)?.let { f ->
                if (f.lifeUpdates.none { it.id == entry.update.id }) {
                    friends.upsert(f.copy(lifeUpdates = (f.lifeUpdates + entry.update).sortedBy { it.addedAt }))
                }
            }
            is TimelineEntry.GiftGiven -> Unit
            is TimelineEntry.Share -> interactions.upsertAttachment(entry.attachment)
        }
    }
}
