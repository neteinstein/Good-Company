package com.neteinstein.goodcompany.core.domain.repository

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface FriendRepository {
    val friends: Flow<List<Friend>>

    suspend fun get(id: FriendId): Friend?

    suspend fun upsert(friend: Friend)

    suspend fun delete(id: FriendId)

    /**
     * Applies [transform] to every friend in one transaction (shared tags: rename, merge, delete, F16) and returns the
     * friends that changed, as they were before. Friends it returns unchanged are not written.
     */
    suspend fun updateAll(transform: (Friend) -> Friend): List<Friend>

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

    companion object {
        /** No links: a default for use cases that only read them to add context (kids in AI prompts). */
        val None: PersonLinkRepository = object : PersonLinkRepository {
            override val links: Flow<List<PersonLink>> = flowOf(emptyList())

            override suspend fun upsert(link: PersonLink) = Unit

            override suspend fun delete(id: String) = Unit
        }
    }
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
    /** The circles; [FriendGroup.cover] is filled in only where covers are joined in (`GroupsWithFamilyCircle`). */
    val groups: Flow<List<FriendGroup>>

    /** Saves the circle; its [FriendGroup.cover] is ignored (covers go through [CircleCoverRepository]). */
    suspend fun upsert(group: FriendGroup)

    suspend fun delete(id: String)
}

/**
 * Circles' cover photos (issue #88): one per circle id, with no link to the circle row (the built-in Family circle has
 * none), so a deleted circle's cover can be put back by Undo. Part of the backup.
 */
interface CircleCoverRepository {
    /** Every cover, by circle id. */
    val covers: Flow<Map<String, ContactPhoto>>

    /** Sets or replaces the cover of [circleId]; null removes it. */
    suspend fun set(circleId: String, cover: ContactPhoto?)

    companion object {
        /** No covers, for callers built without the store. */
        val None: CircleCoverRepository = object : CircleCoverRepository {
            override val covers: Flow<Map<String, ContactPhoto>> = flowOf(emptyMap())

            override suspend fun set(circleId: String, cover: ContactPhoto?) = Unit
        }
    }
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

    companion object {
        /** No links: for callers that only read links for context and have none to give. */
        val None: PersonLinkRepository = object : PersonLinkRepository {
            override val links: Flow<List<PersonLink>> = flowOf(emptyList())

            override suspend fun upsert(link: PersonLink) = Unit

            override suspend fun delete(id: String) = Unit
        }
    }
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

/** The user's answers to calendar suggestions (D3): "No" for one occurrence, "Never" for a series. Part of the backup. */
interface CalendarDecisionRepository {
    val decisions: Flow<List<CalendarDecision>>

    suspend fun upsert(decision: CalendarDecision)

    suspend fun delete(key: String)
}

/**
 * Your own photo (issue #71): one small thumbnail like a [ContactPhoto], shown in every tab header, at the centre of the
 * network map and on your year in review. It is in backups, and never written to contacts or sent to the AI.
 */
interface MyPhotoRepository {
    /** Null while none is set. */
    val photo: Flow<ContactPhoto?>

    /** Sets (or replaces) the photo; null removes it. */
    suspend fun set(photo: ContactPhoto?)

    companion object {
        /** No photo, for callers built without the store. */
        val None: MyPhotoRepository = object : MyPhotoRepository {
            override val photo: Flow<ContactPhoto?> = flowOf(null)

            override suspend fun set(photo: ContactPhoto?) = Unit
        }
    }
}

/** Gift lists per occasion (F8). Part of the backup. */
interface GiftListRepository {
    val lists: Flow<List<GiftList>>

    suspend fun get(id: String): GiftList?

    /** Saves the whole list: its people and their items replace what was stored. */
    suspend fun upsert(list: GiftList)

    suspend fun delete(id: String)

    companion object {
        /** No lists, for callers built without the gift-list store. */
        val None: GiftListRepository = object : GiftListRepository {
            override val lists: Flow<List<GiftList>> = flowOf(emptyList())

            override suspend fun get(id: String): GiftList? = null

            override suspend fun upsert(list: GiftList) = Unit

            override suspend fun delete(id: String) = Unit
        }
    }
}
