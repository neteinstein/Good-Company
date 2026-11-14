package com.neteinstein.goodcompany.core.domain.repository

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface FriendRepository {
    val friends: Flow<List<Friend>>

    suspend fun get(id: FriendId): Friend?

    suspend fun upsert(friend: Friend)

    suspend fun delete(id: FriendId)

    /** Pulls the device address book and merges it into the friend list. Returns the number of contacts seen. */
    suspend fun syncContacts(): Int

    /**
     * Reads the device address book without changing any friend (the "Choose friends" list). Empty when there is no
     * address book or no permission. [withPhotos] false skips the thumbnails, which are heavy for thousands of contacts.
     */
    suspend fun readContacts(withPhotos: Boolean = false): List<ContactRecord>

    /** The thumbnails of just these contacts (those without one are left out). */
    suspend fun contactPhotos(contactIds: Set<String>): Map<String, ContactPhoto>
}

/**
 * Mirrors app data onto the friend's address-book entry (Android sideload build only: it alone declares
 * WRITE_CONTACTS). Every other build uses [None].
 *
 * **This port never deletes.** It adds details and updates the entries it wrote earlier in place (one row per item, so
 * an edit replaces the old row instead of duplicating it); whatever else is on the contact, typed by the user or left
 * from before, stays. Implementations must be idempotent (writing the same card again changes nothing) and must not
 * throw for "no permission" / "contact gone": they just do nothing.
 */
interface ContactsWriter {
    /** Brings the contact [contactId] up to date with [card] for [friendId]: adds new details, updates earlier ones, deletes nothing. */
    suspend fun write(contactId: String, friendId: FriendId, card: ContactCard)

    object None : ContactsWriter {
        override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) = Unit
    }
}

interface HangoutRepository {
    val hangouts: Flow<List<Hangout>>

    suspend fun add(hangout: Hangout)

    suspend fun delete(id: String)

    /** Named places (F11): names given to GPS spots. They never leave the device. */
    val places: Flow<List<NamedPlace>>

    suspend fun upsertPlace(place: NamedPlace)

    /** Forgets the place. Meetups that were there keep their name and their link (so an undo links them again). */
    suspend fun deletePlace(id: String)

    /** Renames the place and every meetup linked to it, in one go. */
    suspend fun renamePlace(id: String, name: String)

    /** Moves every meetup at [fromId] to [intoId] (its id and name), then deletes [fromId], in one go. */
    suspend fun mergePlaces(fromId: String, intoId: String)
}

interface ReminderRepository {
    val reminders: Flow<List<Reminder>>

    suspend fun upsert(reminder: Reminder)

    suspend fun delete(id: String)
}

interface InteractionRepository {
    val interactions: Flow<List<Interaction>>
    val messages: Flow<List<ConversationMessage>>

    /** Dated notes about friends. Kept here, in their own table, but they never count as contact. */
    val journal: Flow<List<JournalEntry>>

    /** Adds [interactions]; automatic ones already stored (same friend, type and time) are skipped. */
    suspend fun addAll(interactions: List<Interaction>)

    suspend fun delete(id: String)

    suspend fun upsertJournal(entry: JournalEntry)

    suspend fun deleteJournal(id: String)

    /** Shared things kept on a friend's or a circle's page (D6, F12). They go with the friend or circle. */
    val attachments: Flow<List<Attachment>>

    /** Adds or replaces [attachment] (skipped when its friend or circle is gone). */
    suspend fun upsertAttachment(attachment: Attachment)

    suspend fun deleteAttachment(id: String)

    suspend fun addMessages(messages: List<ConversationMessage>)

    /** Reads the call log where the platform allows it (Android sideload build only). Returns how many were imported. */
    suspend fun syncCommunicationLogs(friends: List<Friend>): Int
}

interface IdeaRepository {
    val ideas: Flow<List<Idea>>

    suspend fun addAll(ideas: List<Idea>)

    suspend fun dismiss(id: String)

    suspend fun markProposed(id: String, at: kotlin.time.Instant)
}

interface GroupRepository {
    val groups: Flow<List<FriendGroup>>

    suspend fun upsert(group: FriendGroup)

    suspend fun delete(id: String)
}

/**
 * Links between people (issue #39): each stored once, shown from both sides. A link goes when either person goes.
 * Adding or changing one also mirrors both people's links onto their contacts (sideload build, see [ContactsWriter]).
 */
interface PersonLinkRepository {
    val links: Flow<List<PersonLink>>

    /** Adds or replaces [link] (skipped when either person is gone). */
    suspend fun upsert(link: PersonLink)

    suspend fun delete(id: String)
}

interface PlanRepository {
    val plans: Flow<List<Plan>>

    suspend fun upsert(plan: Plan)

    suspend fun delete(id: String)
}

/** Reads and replaces everything the app stores, for export / restore. The API key is never included. */
interface BackupRepository {
    suspend fun snapshot(): Backup

    suspend fun restore(backup: Backup)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Whether what the app writes now survives a restart (false only in a web tab that got temporary storage). */
interface StorageStatus {
    suspend fun isPersistent(): Boolean
}
