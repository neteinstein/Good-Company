package com.neteinstein.goodcompany.core.data.repository

import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.data.source.PhoneMatcher
import com.neteinstein.goodcompany.core.data.storage.Keys
import com.neteinstein.goodcompany.core.data.storage.PersistentState
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration.Companion.days

class FriendRepositoryImpl(settings: Settings, private val contacts: ContactsSource) : FriendRepository {
    private val store = PersistentState(settings, Keys.FRIENDS, ListSerializer(Friend.serializer()), emptyList())
    override val friends: Flow<List<Friend>> = store.flow

    override suspend fun get(id: FriendId): Friend? = store.value.firstOrNull { it.id == id }

    override suspend fun upsert(friend: Friend) = store.update { list ->
        if (list.any { it.id == friend.id }) list.map { if (it.id == friend.id) friend else it } else list + friend
    }

    override suspend fun delete(id: FriendId) = store.update { list -> list.filterNot { it.id == id } }

    override suspend fun syncContacts(): Int {
        val records = contacts.readContacts()
        // An empty read means "no permission / no address book": never wipe existing friends because of it.
        if (records.isNotEmpty()) store.update { ContactMerger.merge(it, records) }
        return records.size
    }
}

class HangoutRepositoryImpl(settings: Settings) : HangoutRepository {
    private val store = PersistentState(settings, Keys.HANGOUTS, ListSerializer(Hangout.serializer()), emptyList())
    override val hangouts: Flow<List<Hangout>> = store.flow

    override suspend fun add(hangout: Hangout) = store.update { it + hangout }

    override suspend fun delete(id: String) = store.update { list -> list.filterNot { it.id == id } }
}

class ReminderRepositoryImpl(settings: Settings) : ReminderRepository {
    private val store = PersistentState(settings, Keys.REMINDERS, ListSerializer(Reminder.serializer()), emptyList())
    override val reminders: Flow<List<Reminder>> = store.flow

    override suspend fun upsert(reminder: Reminder) = store.update { list ->
        if (list.any { it.id == reminder.id }) list.map { if (it.id == reminder.id) reminder else it } else list + reminder
    }

    override suspend fun delete(id: String) = store.update { list -> list.filterNot { it.id == id } }
}

class InteractionRepositoryImpl(
    settings: Settings,
    private val logs: CommunicationLogSource,
    private val clock: AppClock,
) : InteractionRepository {
    private val store = PersistentState(settings, Keys.INTERACTIONS, ListSerializer(Interaction.serializer()), emptyList())
    private val messageStore = PersistentState(settings, Keys.MESSAGES, ListSerializer(ConversationMessage.serializer()), emptyList())
    override val interactions: Flow<List<Interaction>> = store.flow
    override val messages: Flow<List<ConversationMessage>> = messageStore.flow

    override suspend fun addAll(interactions: List<Interaction>) = store.update { current ->
        (current + interactions).distinct().sortedBy { it.at }.takeLast(MAX_INTERACTIONS)
    }

    override suspend fun addMessages(messages: List<ConversationMessage>) = messageStore.update { current ->
        (current + messages).distinct()
            .groupBy { it.friendId }
            .flatMap { (_, perFriend) -> perFriend.sortedBy { it.at }.takeLast(MAX_MESSAGES_PER_FRIEND) }
    }

    override suspend fun syncCommunicationLogs(friends: List<Friend>): Int {
        val entries = logs.read(since = clock.now() - LOG_WINDOW)
        if (entries.isEmpty()) return 0
        val byPhone = PhoneMatcher.index(friends) { it.phoneNumbers }
        val matched = entries.mapNotNull { e ->
            val friend = PhoneMatcher.key(e.phoneNumber)?.let(byPhone::get) ?: return@mapNotNull null
            Interaction(friend.id, e.type, e.at)
        }
        addAll(matched)
        return matched.size
    }

    private companion object {
        const val MAX_INTERACTIONS = 5_000
        const val MAX_MESSAGES_PER_FRIEND = 60
        val LOG_WINDOW = 90.days
    }
}

class IdeaRepositoryImpl(settings: Settings) : IdeaRepository {
    private val store = PersistentState(settings, Keys.IDEAS, ListSerializer(Idea.serializer()), emptyList())
    override val ideas: Flow<List<Idea>> = store.flow

    override suspend fun addAll(ideas: List<Idea>) = store.update { (it + ideas).takeLast(MAX_IDEAS) }

    override suspend fun dismiss(id: String) = store.update { list ->
        list.map { if (it.id == id) it.copy(dismissed = true) else it }
    }

    private companion object {
        const val MAX_IDEAS = 200
    }
}

class SettingsRepositoryImpl(settings: Settings) : SettingsRepository {
    private val store = PersistentState(settings, Keys.SETTINGS, AppSettings.serializer(), AppSettings())
    override val settings: Flow<AppSettings> = store.flow

    override suspend fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)
}
