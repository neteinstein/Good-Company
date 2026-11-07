package com.neteinstein.goodcompany.core.domain.repository

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
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
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
