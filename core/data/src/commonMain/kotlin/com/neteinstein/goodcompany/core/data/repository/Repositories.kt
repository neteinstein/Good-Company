package com.neteinstein.goodcompany.core.data.repository

import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.data.source.PhoneMatcher
import com.neteinstein.goodcompany.core.data.storage.Keys
import com.neteinstein.goodcompany.core.data.storage.PersistentState
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
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.BackupRepository
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Friends, persisted as JSON. Friends that came from the address book are also mirrored onto their contact through
 * [writer] (a no-op except in the Android sideload build): whenever data the contact can hold changes, the contact is
 * updated in the same call, so it never lags behind the app. The mirror never deletes: clearing details or removing a
 * friend from the app never takes anything off their contact, and a changed detail updates its row instead of duplicating it. A failing or unavailable address book never blocks saving.
 */
class FriendRepositoryImpl(
    settings: Settings,
    private val contacts: ContactsSource,
    private val writer: ContactsWriter = ContactsWriter.None,
) : FriendRepository {
    private val store = PersistentState(settings, Keys.FRIENDS, ListSerializer(Friend.serializer()), emptyList())
    override val friends: Flow<List<Friend>> = store.flow

    internal suspend fun replaceAll(list: List<Friend>) = store.update { list }

    internal val current: List<Friend> get() = store.value

    override suspend fun get(id: FriendId): Friend? = store.value.firstOrNull { it.id == id }

    override suspend fun upsert(friend: Friend) {
        var previous: Friend? = null
        store.update { list ->
            previous = list.firstOrNull { it.id == friend.id }
            if (previous != null) list.map { if (it.id == friend.id) friend else it } else list + friend
        }
        mirrorToContact(previous, friend)
    }

    // Deliberately leaves the copy on the contact alone: removing a friend from the app must never take data off
    // their contact (owner decision 2026-11-08).
    override suspend fun delete(id: FriendId) = store.update { list -> list.filterNot { it.id == id } }

    override suspend fun syncContacts(): Int {
        val records = contacts.readContacts()
        // An empty read means "no permission / no address book": never wipe existing friends because of it.
        if (records.isNotEmpty()) {
            store.update { ContactMerger.merge(it, records, addNew = contacts.importsNewContacts) }
            backfillContacts()
        }
        return records.size
    }

    private suspend fun mirrorToContact(previous: Friend?, friend: Friend) {
        val contactId = friend.contactId ?: return
        val card = ContactCard.from(friend)
        val before = previous?.takeIf { it.contactId == contactId }?.let(ContactCard::from)
        if (card == before) return
        if (!card.isEmpty) safely { writer.write(contactId, friend.id, card) }
    }

    /**
     * Data saved before the contact was writable (or before the permission was granted) still needs to reach it.
     * Only friends with details the contact can hold are visited, so a big address book costs nothing; the writer
     * skips contacts that already match.
     */
    private suspend fun backfillContacts() {
        store.value.forEach { friend ->
            val contactId = friend.contactId ?: return@forEach
            val card = ContactCard.from(friend)
            if (!friend.isOrphaned && card.hasExtras) safely { writer.write(contactId, friend.id, card) }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Mirroring is best effort: the app's own copy is already saved.
        }
    }
}

class HangoutRepositoryImpl(settings: Settings) : HangoutRepository {
    private val store = PersistentState(settings, Keys.HANGOUTS, ListSerializer(Hangout.serializer()), emptyList())
    override val hangouts: Flow<List<Hangout>> = store.flow

    internal suspend fun replaceAll(list: List<Hangout>) = store.update { list }

    internal val current: List<Hangout> get() = store.value

    override suspend fun add(hangout: Hangout) = store.update { it + hangout }

    override suspend fun delete(id: String) = store.update { list -> list.filterNot { it.id == id } }
}

class ReminderRepositoryImpl(settings: Settings) : ReminderRepository {
    private val store = PersistentState(settings, Keys.REMINDERS, ListSerializer(Reminder.serializer()), emptyList())
    override val reminders: Flow<List<Reminder>> = store.flow

    internal suspend fun replaceAll(list: List<Reminder>) = store.update { list }

    internal val current: List<Reminder> get() = store.value

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

    internal suspend fun replaceAll(list: List<Interaction>, messages: List<ConversationMessage>) {
        store.update { list }
        messageStore.update { messages }
    }

    internal val current: List<Interaction> get() = store.value
    internal val currentMessages: List<ConversationMessage> get() = messageStore.value

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

    internal suspend fun replaceAll(list: List<Idea>) = store.update { list }

    internal val current: List<Idea> get() = store.value

    override suspend fun addAll(ideas: List<Idea>) = store.update { (it + ideas).takeLast(MAX_IDEAS) }

    override suspend fun dismiss(id: String) = store.update { list ->
        list.map { if (it.id == id) it.copy(dismissed = true) else it }
    }

    override suspend fun markProposed(id: String, at: Instant) = store.update { list ->
        list.map { if (it.id == id) it.copy(proposedAt = at) else it }
    }

    private companion object {
        const val MAX_IDEAS = 200
    }
}

class SettingsRepositoryImpl(settings: Settings) : SettingsRepository {
    private val store = PersistentState(settings, Keys.SETTINGS, AppSettings.serializer(), AppSettings())
    override val settings: Flow<AppSettings> = store.flow

    internal val current: AppSettings get() = store.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) = store.update(transform)
}

class GroupRepositoryImpl(settings: Settings) : GroupRepository {
    private val store = PersistentState(settings, Keys.GROUPS, ListSerializer(FriendGroup.serializer()), emptyList())
    override val groups: Flow<List<FriendGroup>> = store.flow

    internal suspend fun replaceAll(list: List<FriendGroup>) = store.update { list }

    internal val current: List<FriendGroup> get() = store.value

    override suspend fun upsert(group: FriendGroup) = store.update { list ->
        if (list.any { it.id == group.id }) list.map { if (it.id == group.id) group else it } else list + group
    }

    override suspend fun delete(id: String) = store.update { list -> list.filterNot { it.id == id } }
}

class PlanRepositoryImpl(settings: Settings) : PlanRepository {
    private val store = PersistentState(settings, Keys.PLANS, ListSerializer(Plan.serializer()), emptyList())
    override val plans: Flow<List<Plan>> = store.flow

    internal suspend fun replaceAll(list: List<Plan>) = store.update { list }

    internal val current: List<Plan> get() = store.value

    override suspend fun upsert(plan: Plan) = store.update { list ->
        val next = if (list.any { it.id == plan.id }) list.map { if (it.id == plan.id) plan else it } else list + plan
        // Keep open plans; trim old answered ones.
        val (open, closed) = next.partition { it.status == PlanStatus.PLANNED }
        open + closed.sortedBy { it.date }.takeLast(MAX_CLOSED_PLANS)
    }

    override suspend fun delete(id: String) = store.update { list -> list.filterNot { it.id == id } }

    private companion object {
        const val MAX_CLOSED_PLANS = 200
    }
}

/** Export / restore across every store. Writes go through each repository so their flows update at once. */
class BackupRepositoryImpl(
    private val friends: FriendRepositoryImpl,
    private val hangouts: HangoutRepositoryImpl,
    private val reminders: ReminderRepositoryImpl,
    private val interactions: InteractionRepositoryImpl,
    private val ideas: IdeaRepositoryImpl,
    private val groups: GroupRepositoryImpl,
    private val plans: PlanRepositoryImpl,
    private val settings: SettingsRepositoryImpl,
) : BackupRepository {
    override suspend fun snapshot(): Backup = Backup(
        friends = friends.current,
        hangouts = hangouts.current,
        reminders = reminders.current,
        interactions = interactions.current,
        messages = interactions.currentMessages,
        ideas = ideas.current,
        groups = groups.current,
        plans = plans.current,
        settings = settings.current,
    )

    override suspend fun restore(backup: Backup) {
        friends.replaceAll(backup.friends)
        hangouts.replaceAll(backup.hangouts)
        reminders.replaceAll(backup.reminders)
        interactions.replaceAll(backup.interactions, backup.messages)
        ideas.replaceAll(backup.ideas)
        groups.replaceAll(backup.groups)
        plans.replaceAll(backup.plans)
        settings.update { backup.settings }
    }
}
