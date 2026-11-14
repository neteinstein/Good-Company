package com.neteinstein.goodcompany.core.data.repository

import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.FRIEND_TABLES
import com.neteinstein.goodcompany.core.data.db.GROUP_TABLES
import com.neteinstein.goodcompany.core.data.db.GoodCompanyDatabase
import com.neteinstein.goodcompany.core.data.db.HANGOUT_TABLES
import com.neteinstein.goodcompany.core.data.db.IDEA_TABLES
import com.neteinstein.goodcompany.core.data.db.INTERACTION_TABLES
import com.neteinstein.goodcompany.core.data.db.JOURNAL_TABLES
import com.neteinstein.goodcompany.core.data.db.MESSAGE_TABLES
import com.neteinstein.goodcompany.core.data.db.PLAN_TABLES
import com.neteinstein.goodcompany.core.data.db.PREFERENCE_TABLES
import com.neteinstein.goodcompany.core.data.db.REMINDER_TABLES
import com.neteinstein.goodcompany.core.data.db.nextFriendPosition
import com.neteinstein.goodcompany.core.data.db.readFriends
import com.neteinstein.goodcompany.core.data.db.readGroups
import com.neteinstein.goodcompany.core.data.db.readHangouts
import com.neteinstein.goodcompany.core.data.db.readIdeas
import com.neteinstein.goodcompany.core.data.db.readInteractions
import com.neteinstein.goodcompany.core.data.db.ATTACHMENT_TABLES
import com.neteinstein.goodcompany.core.data.db.readAttachments
import com.neteinstein.goodcompany.core.data.db.readJournal
import com.neteinstein.goodcompany.core.data.db.writeAttachment
import com.neteinstein.goodcompany.core.data.db.readMessages
import com.neteinstein.goodcompany.core.data.db.readPlans
import com.neteinstein.goodcompany.core.data.db.readPreferences
import com.neteinstein.goodcompany.core.data.db.readReminders
import com.neteinstein.goodcompany.core.data.db.toColumn
import com.neteinstein.goodcompany.core.data.db.writeFriend
import com.neteinstein.goodcompany.core.data.db.writeGroup
import com.neteinstein.goodcompany.core.data.db.writeHangout
import com.neteinstein.goodcompany.core.data.db.writeIdea
import com.neteinstein.goodcompany.core.data.db.writeInteraction
import com.neteinstein.goodcompany.core.data.db.writeJournal
import com.neteinstein.goodcompany.core.data.db.writeMessage
import com.neteinstein.goodcompany.core.data.db.writePlan
import com.neteinstein.goodcompany.core.data.db.writePreferences
import com.neteinstein.goodcompany.core.data.db.writeReminder
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.util.PhoneMatcher
import com.neteinstein.goodcompany.core.data.storage.PersistentState
import com.neteinstein.goodcompany.core.domain.model.AppSettings
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
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/*
 * The repositories over SQLite (issue #14). Same interfaces and behaviour as the JSON ones in Repositories.kt, which
 * they replace once the one-off JSON → SQLite import exists (step 3); until then they're built only by tests.
 * The shared contract tests (commonTest RepositoryContract*) run against both.
 */

/**
 * Friends. Like [FriendRepositoryImpl], friends from the address book are mirrored onto their contact through [writer]
 * (sideload build only), never deleting anything there; a failing address book never blocks saving.
 */
class SqlFriendRepository(
    private val db: AppDatabase,
    private val contacts: ContactsSource,
    private val writer: ContactsWriter = ContactsWriter.None,
) : FriendRepository {
    override val friends: Flow<List<Friend>> = db.observe(*FRIEND_TABLES) { it.readFriends() }

    override suspend fun get(id: FriendId): Friend? = db.access { it.readFriends() }.firstOrNull { it.id == id }

    override suspend fun upsert(friend: Friend) {
        val previous = db.transaction { database ->
            val friends = database.readFriends()
            val index = friends.indexOfFirst { it.id == friend.id }
            val position = if (index >= 0) index.toLong() else database.nextFriendPosition()
            database.writeFriend(friend, position)
            friends.getOrNull(index)
        }
        mirrorToContact(previous, friend)
    }

    // Leaves their contact alone: removing a friend from the app never takes data off it (owner decision 2026-11-08).
    // Their hangout, plan, reminder, idea and group memberships, interactions and chat lines go with them.
    override suspend fun delete(id: FriendId) {
        db.access { it.friendQueries.delete(id.value) }
    }

    override suspend fun syncContacts(): Int {
        val records = contacts.readContacts(withPhotos = true)
        // An empty read means "no permission / no address book": never wipe existing friends because of it.
        if (records.isNotEmpty()) {
            val merged = db.transaction { database ->
                val current = database.readFriends()
                val next = ContactMerger.merge(current, records, addNew = contacts.importsNewContacts)
                database.replaceFriends(current, next)
                next
            }
            backfillContacts(merged)
        }
        return records.size
    }

    override suspend fun readContacts(withPhotos: Boolean): List<ContactRecord> = contacts.readContacts(withPhotos)

    override suspend fun contactPhotos(contactIds: Set<String>): Map<String, ContactPhoto> =
        if (contactIds.isEmpty()) emptyMap() else contacts.readPhotos(contactIds)

    private suspend fun mirrorToContact(previous: Friend?, friend: Friend) {
        val contactId = friend.contactId ?: return
        val card = ContactCard.from(friend)
        val before = previous?.takeIf { it.contactId == contactId }?.let(ContactCard::from)
        if (card == before) return
        if (!card.isEmpty) safely { writer.write(contactId, friend.id, card) }
    }

    /** See [FriendRepositoryImpl]: details saved before the contact was writable still need to reach it. */
    private suspend fun backfillContacts(friends: List<Friend>) {
        friends.forEach { friend ->
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

/**
 * Makes the stored friends equal [next] (in its order), writing only friends that changed or moved and deleting those
 * that are gone. Call inside a transaction.
 */
internal suspend fun GoodCompanyDatabase.replaceFriends(current: List<Friend>, next: List<Friend>) {
    val before = current.withIndex().associate { (i, f) -> f.id to (i to f) }
    next.forEachIndexed { i, friend ->
        if (before[friend.id] != (i to friend)) writeFriend(friend, i.toLong())
    }
    val keep = next.mapTo(HashSet()) { it.id }
    current.filterNot { it.id in keep }.forEach { friendQueries.delete(it.id.value) }
}

class SqlHangoutRepository(private val db: AppDatabase) : HangoutRepository {
    override val hangouts: Flow<List<Hangout>> = db.observe(*HANGOUT_TABLES) { it.readHangouts() }

    override suspend fun add(hangout: Hangout) = db.transaction { it.writeHangout(hangout) }

    override suspend fun delete(id: String) {
        db.access { it.hangoutQueries.delete(id) }
    }
}

class SqlReminderRepository(private val db: AppDatabase) : ReminderRepository {
    override val reminders: Flow<List<Reminder>> = db.observe(*REMINDER_TABLES) { it.readReminders() }

    override suspend fun upsert(reminder: Reminder) = db.transaction { it.writeReminder(reminder) }

    override suspend fun delete(id: String) {
        db.access { it.reminderQueries.delete(id) }
    }
}

class SqlInteractionRepository(
    private val db: AppDatabase,
    private val logs: CommunicationLogSource,
    private val clock: AppClock,
) : InteractionRepository {
    override val interactions: Flow<List<Interaction>> = db.observe(*INTERACTION_TABLES) { it.readInteractions() }
    override val messages: Flow<List<ConversationMessage>> = db.observe(*MESSAGE_TABLES) { it.readMessages() }
    override val journal: Flow<List<JournalEntry>> = db.observe(*JOURNAL_TABLES) { it.readJournal() }
    override val attachments: Flow<List<Attachment>> = db.observe(*ATTACHMENT_TABLES) { it.readAttachments() }

    override suspend fun addAll(interactions: List<Interaction>) = db.transaction { database ->
        interactions.forEach { database.writeInteraction(it) }
        database.interactionQueries.trimInteractions(MAX_INTERACTIONS)
        Unit
    }

    override suspend fun delete(id: String) {
        db.access { it.interactionQueries.deleteInteraction(id) }
    }

    override suspend fun upsertJournal(entry: JournalEntry) {
        db.access { it.writeJournal(entry) }
    }

    override suspend fun deleteJournal(id: String) {
        db.access { it.interactionQueries.deleteJournal(id) }
    }

    override suspend fun upsertAttachment(attachment: Attachment) {
        db.access { it.writeAttachment(attachment) }
    }

    override suspend fun deleteAttachment(id: String) {
        db.access { it.attachmentQueries.deleteAttachment(id) }
    }

    override suspend fun addMessages(messages: List<ConversationMessage>) = db.transaction { database ->
        messages.forEach { database.writeMessage(it) }
        messages.map { it.friendId }.distinct().forEach { database.interactionQueries.trimMessages(it.value, MAX_MESSAGES_PER_FRIEND) }
    }

    override suspend fun syncCommunicationLogs(friends: List<Friend>): Int {
        val entries = logs.read(since = clock.now() - LOG_WINDOW)
        if (entries.isEmpty()) return 0
        val byPhone = PhoneMatcher.index(friends) { it.phoneNumbers }
        val matched = entries.mapNotNull { e ->
            val friend = PhoneMatcher.key(e.phoneNumber)?.let(byPhone::get) ?: return@mapNotNull null
            Interaction(
                friendId = friend.id,
                type = e.type,
                at = e.at,
                id = Interaction.automaticId(friend.id, e.type, e.at),
                source = InteractionSource.CALL_LOG,
                durationSeconds = e.durationSeconds,
            )
        }
        addAll(matched)
        return matched.size
    }

    private companion object {
        const val MAX_INTERACTIONS = 5_000L
        const val MAX_MESSAGES_PER_FRIEND = 60L
        val LOG_WINDOW = 90.days
    }
}

class SqlIdeaRepository(private val db: AppDatabase) : IdeaRepository {
    override val ideas: Flow<List<Idea>> = db.observe(*IDEA_TABLES) { it.readIdeas() }

    override suspend fun addAll(ideas: List<Idea>) = db.transaction { database ->
        ideas.forEach { database.writeIdea(it) }
        database.ideaQueries.trim(MAX_IDEAS)
        Unit
    }

    override suspend fun dismiss(id: String) {
        db.access { it.ideaQueries.dismiss(id) }
    }

    override suspend fun markProposed(id: String, at: Instant) {
        db.access { it.ideaQueries.markProposed(at.toColumn(), id) }
    }

    private companion object {
        const val MAX_IDEAS = 200L
    }
}

class SqlGroupRepository(private val db: AppDatabase) : GroupRepository {
    override val groups: Flow<List<FriendGroup>> = db.observe(*GROUP_TABLES) { it.readGroups() }

    override suspend fun upsert(group: FriendGroup) = db.transaction { it.writeGroup(group) }

    override suspend fun delete(id: String) {
        db.access { it.friendGroupQueries.delete(id) }
    }
}

class SqlPlanRepository(private val db: AppDatabase) : PlanRepository {
    override val plans: Flow<List<Plan>> = db.observe(*PLAN_TABLES) { it.readPlans() }

    // Keeps open plans; trims old answered ones.
    override suspend fun upsert(plan: Plan) = db.transaction { database ->
        database.writePlan(plan)
        database.planQueries.trimClosed(MAX_CLOSED_PLANS)
        Unit
    }

    override suspend fun delete(id: String) {
        db.access { it.planQueries.delete(id) }
    }

    private companion object {
        const val MAX_CLOSED_PLANS = 200L
    }
}

/**
 * The part of [AppSettings] that belongs to this device or is needed before the first screen (theme, app lock,
 * onboarding), plus device timestamps and the API key. Stored in key-value storage under [KEY]; the rest of
 * [AppSettings] lives in the database (`user_preferences`).
 */
@Serializable
internal data class DeviceSettings(
    val aiApiKey: String? = null,
    val lastIdeasRunAt: Instant? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val onboardingDone: Boolean = false,
    val appLock: Boolean = false,
    val lastOpenedAt: Instant? = null,
    val lastDriveBackupAt: Instant? = null,
) {
    fun applyTo(settings: AppSettings) = settings.copy(
        aiApiKey = aiApiKey,
        lastIdeasRunAt = lastIdeasRunAt,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        onboardingDone = onboardingDone,
        appLock = appLock,
        lastOpenedAt = lastOpenedAt,
        lastDriveBackupAt = lastDriveBackupAt,
    )

    companion object {
        const val KEY = "device_settings.v1"

        fun of(settings: AppSettings) = DeviceSettings(
            aiApiKey = settings.aiApiKey,
            lastIdeasRunAt = settings.lastIdeasRunAt,
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            onboardingDone = settings.onboardingDone,
            appLock = settings.appLock,
            lastOpenedAt = settings.lastOpenedAt,
            lastDriveBackupAt = settings.lastDriveBackupAt,
        )
    }
}

/** One [AppSettings] made of two stores: user preferences in the database, device settings in key-value storage. */
class SqlSettingsRepository(private val db: AppDatabase, settings: Settings) : SettingsRepository {
    init {
        LegacyJsonImport.takeOverDeviceSettings(settings)
    }

    private val device = PersistentState(settings, DeviceSettings.KEY, DeviceSettings.serializer(), DeviceSettings())
    private val lock = Mutex()

    override val settings: Flow<AppSettings> =
        combine(db.observe(*PREFERENCE_TABLES) { it.readPreferences(AppSettings()) }, device.flow) { preferences, device ->
            device.applyTo(preferences)
        }

    override suspend fun update(transform: (AppSettings) -> AppSettings) = lock.withLock {
        val current = device.value.applyTo(db.access { it.readPreferences(AppSettings()) })
        val next = transform(current)
        if (next != current) {
            db.access { it.writePreferences(next) }
            device.update { DeviceSettings.of(next) }
        }
    }

    internal suspend fun writeDevice(settings: AppSettings) = device.update { DeviceSettings.of(settings) }

    internal suspend fun current(): AppSettings = device.value.applyTo(db.access { it.readPreferences(AppSettings()) })
}

/**
 * Export / restore across every table. The snapshot is read in one transaction; a restore replaces everything in one
 * transaction (all or nothing), then the device settings. The [Backup] JSON format is unchanged.
 */
class SqlBackupRepository(private val db: AppDatabase, private val settings: SqlSettingsRepository) : BackupRepository {
    override suspend fun snapshot(): Backup {
        val backup = db.transaction { database ->
            Backup(
                friends = database.readFriends(),
                hangouts = database.readHangouts(),
                reminders = database.readReminders(),
                interactions = database.readInteractions(),
                messages = database.readMessages(),
                journal = database.readJournal(),
                attachments = database.readAttachments(),
                ideas = database.readIdeas(),
                groups = database.readGroups(),
                plans = database.readPlans(),
            )
        }
        return backup.copy(settings = settings.current())
    }

    override suspend fun restore(backup: Backup) {
        db.transaction { it.replaceAllData(backup) }
        settings.writeDevice(backup.settings)
    }
}

/**
 * Replaces every table with [backup], including the user preferences from its settings (device settings are the
 * caller's job). Call inside a transaction, so a failure leaves the old data in place.
 */
internal suspend fun GoodCompanyDatabase.replaceAllData(backup: Backup) {
    // Friends first: deleting them clears every join table, and everything else refers to them.
    friendQueries.deleteAll()
    hangoutQueries.deleteAll()
    reminderQueries.deleteAll()
    planQueries.deleteAll()
    ideaQueries.deleteAll()
    friendGroupQueries.deleteAll()
    interactionQueries.deleteAllInteractions()
    interactionQueries.deleteAllMessages()
    interactionQueries.deleteAllJournal()
    attachmentQueries.deleteAllAttachments()

    backup.friends.forEachIndexed { i, friend -> writeFriend(friend, i.toLong()) }
    backup.ideas.forEach { writeIdea(it) } // before plans, which may point at an idea
    backup.hangouts.forEach { writeHangout(it) }
    backup.reminders.forEach { writeReminder(it) }
    backup.plans.forEach { writePlan(it) }
    backup.groups.forEach { writeGroup(it) }
    backup.interactions.forEach { writeInteraction(it) }
    backup.messages.forEach { writeMessage(it) }
    backup.journal.forEach { writeJournal(it) }
    backup.attachments.forEach { writeAttachment(it) } // after friends and circles, which they belong to
    writePreferences(backup.settings)
}
