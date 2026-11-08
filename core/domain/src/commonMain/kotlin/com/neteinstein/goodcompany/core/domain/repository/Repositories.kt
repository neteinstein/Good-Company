package com.neteinstein.goodcompany.core.domain.repository

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
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
}

/**
 * Mirrors app data onto the friend's address-book entry (Android sideload build only: it alone declares
 * WRITE_CONTACTS). Every other build uses [None].
 *
 * **Append-only: this port can add, never delete or overwrite.** Whatever is already on the contact stays, whether the
 * user typed it or an earlier write put it there, so the contact only ever gains information. Implementations must be
 * idempotent (writing the same card again adds nothing) and must not throw for "no permission" / "contact gone": they
 * just do nothing.
 */
interface ContactsWriter {
    /** Adds the details in [card] that the contact [contactId] doesn't already show for [friendId]. */
    suspend fun write(contactId: String, friendId: FriendId, card: ContactCard)

    object None : ContactsWriter {
        override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) = Unit
    }
}

interface HangoutRepository {
    val hangouts: Flow<List<Hangout>>

    suspend fun add(hangout: Hangout)

    suspend fun delete(id: String)
}

interface ReminderRepository {
    val reminders: Flow<List<Reminder>>

    suspend fun upsert(reminder: Reminder)

    suspend fun delete(id: String)
}

interface InteractionRepository {
    val interactions: Flow<List<Interaction>>
    val messages: Flow<List<ConversationMessage>>

    suspend fun addAll(interactions: List<Interaction>)

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
