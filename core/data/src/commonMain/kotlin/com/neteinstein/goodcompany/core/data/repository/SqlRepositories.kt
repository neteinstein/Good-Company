package com.neteinstein.goodcompany.core.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.neteinstein.goodcompany.core.data.db.ATTACHMENT_TABLES
import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.CALENDAR_DECISION_TABLES
import com.neteinstein.goodcompany.core.data.db.FRIEND_TABLES
import com.neteinstein.goodcompany.core.data.db.GIFT_LIST_TABLES
import com.neteinstein.goodcompany.core.data.db.GROUP_TABLES
import com.neteinstein.goodcompany.core.data.db.GoodCompanyDatabase
import com.neteinstein.goodcompany.core.data.db.HANGOUT_TABLES
import com.neteinstein.goodcompany.core.data.db.IDEA_TABLES
import com.neteinstein.goodcompany.core.data.db.INTERACTION_TABLES
import com.neteinstein.goodcompany.core.data.db.JOURNAL_TABLES
import com.neteinstein.goodcompany.core.data.db.LINK_TABLES
import com.neteinstein.goodcompany.core.data.db.MESSAGE_TABLES
import com.neteinstein.goodcompany.core.data.db.PLACE_TABLES
import com.neteinstein.goodcompany.core.data.db.PLAN_TABLES
import com.neteinstein.goodcompany.core.data.db.PREFERENCE_TABLES
import com.neteinstein.goodcompany.core.data.db.REMINDER_TABLES
import com.neteinstein.goodcompany.core.data.db.nextFriendPosition
import com.neteinstein.goodcompany.core.data.db.readAttachments
import com.neteinstein.goodcompany.core.data.db.readCalendarDecisions
import com.neteinstein.goodcompany.core.data.db.readFriends
import com.neteinstein.goodcompany.core.data.db.readGiftLists
import com.neteinstein.goodcompany.core.data.db.readGroups
import com.neteinstein.goodcompany.core.data.db.readHangouts
import com.neteinstein.goodcompany.core.data.db.readIdeas
import com.neteinstein.goodcompany.core.data.db.readInteractions
import com.neteinstein.goodcompany.core.data.db.readJournal
import com.neteinstein.goodcompany.core.data.db.readLinks
import com.neteinstein.goodcompany.core.data.db.readMessages
import com.neteinstein.goodcompany.core.data.db.readPlaces
import com.neteinstein.goodcompany.core.data.db.readPlans
import com.neteinstein.goodcompany.core.data.db.readReminders
import com.neteinstein.goodcompany.core.data.db.readStoredPreferences
import com.neteinstein.goodcompany.core.data.db.toColumn
import com.neteinstein.goodcompany.core.data.db.writeAttachment
import com.neteinstein.goodcompany.core.data.db.writeCalendarDecision
import com.neteinstein.goodcompany.core.data.db.writeFriend
import com.neteinstein.goodcompany.core.data.db.writeGiftList
import com.neteinstein.goodcompany.core.data.db.writeGroup
import com.neteinstein.goodcompany.core.data.db.writeHangout
import com.neteinstein.goodcompany.core.data.db.writeIdea
import com.neteinstein.goodcompany.core.data.db.writeInteraction
import com.neteinstein.goodcompany.core.data.db.writeJournal
import com.neteinstein.goodcompany.core.data.db.writeLink
import com.neteinstein.goodcompany.core.data.db.writeMessage
import com.neteinstein.goodcompany.core.data.db.writePlace
import com.neteinstein.goodcompany.core.data.db.writePlan
import com.neteinstein.goodcompany.core.data.db.writePreferences
import com.neteinstein.goodcompany.core.data.db.writeReminder
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.data.storage.PersistentState
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
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
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.BackupRepository
import com.neteinstein.goodcompany.core.domain.repository.CalendarDecisionRepository
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GiftListRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.domain.usecase.KidsAsPeople
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.PhoneMatcher
import com.russhwolf.settings.Settings
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
    writer: ContactsWriter = ContactsWriter.None,
) : FriendRepository {
    private val mirror = ContactMirror(writer)

    override val friends: Flow<List<Friend>> = db.observe(*FRIEND_TABLES) { it.readFriends() }

    override suspend fun get(id: FriendId): Friend? = db.access { it.readFriends() }.firstOrNull { it.id == id }

    override suspend fun upsert(friend: Friend) {
        val (before, after) = db.transaction { database ->
            val friends = database.readFriends()
            val index = friends.indexOfFirst { it.id == friend.id }
            val position = if (index >= 0) index.toLong() else database.nextFriendPosition()
            database.writeFriend(friend, position)
            val links = database.readLinks()
            val people = friends.associateBy { it.id }
            ContactMirror.Snapshot(people, links) to ContactMirror.Snapshot(people + (friend.id to friend), links)
        }
        // Their own contact, and the people linked to them (whose relation rows show this friend's name).
        val linked = after.links.mapNotNullTo(mutableSetOf(friend.id)) { it.otherThan(friend.id) }
        mirror.sync(linked, before, after)
    }

    // Leaves their contact alone: removing a friend from the app never takes data off it (owner decision 2026-11-08).
    // Their hangout, plan, reminder, idea and group memberships, interactions and chat lines go with them.
    override suspend fun delete(id: FriendId) {
        db.access { it.friendQueries.delete(id.value) }
    }

    override suspend fun updateAll(transform: (Friend) -> Friend): List<Friend> {
        val (changed, snapshots) = db.transaction { database ->
            val current = database.readFriends()
            val next = current.map(transform)
            database.replaceFriends(current, next)
            val links = database.readLinks()
            current.zip(next).filter { (old, new) -> old != new } to
                (ContactMirror.Snapshot(current.associateBy { it.id }, links) to ContactMirror.Snapshot(next.associateBy { it.id }, links))
        }
        mirror.sync(changed.mapTo(mutableSetOf()) { it.first.id }, snapshots.first, snapshots.second)
        return changed.map { it.first }
    }

    override suspend fun syncContacts(): Int {
        val records = contacts.readContacts(withPhotos = true)
        // An empty read means "no permission / no address book": never wipe existing friends because of it.
        if (records.isNotEmpty()) {
            val (merged, links) = db.transaction { database ->
                val current = database.readFriends()
                val next = ContactMerger.merge(current, records, addNew = contacts.importsNewContacts)
                database.replaceFriends(current, next)
                next to database.readLinks()
            }
            mirror.backfill(merged, links)
        }
        return records.size
    }

    override suspend fun readContacts(withPhotos: Boolean): List<ContactRecord> = contacts.readContacts(withPhotos)

    override suspend fun contactPhotos(contactIds: Set<String>): Map<String, ContactPhoto> =
        if (contactIds.isEmpty()) emptyMap() else contacts.readPhotos(contactIds)
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

    override val places: Flow<List<NamedPlace>> = db.observe(*PLACE_TABLES) { it.readPlaces() }

    override suspend fun upsertPlace(place: NamedPlace) = db.transaction { it.writePlace(place) }

    override suspend fun deletePlace(id: String) {
        db.access { it.namedPlaceQueries.deletePlace(id) }
    }

    override suspend fun renamePlace(id: String, name: String) = db.transaction { database ->
        val row = database.namedPlaceQueries.placeById(id).awaitAsOneOrNull() ?: return@transaction
        database.namedPlaceQueries.upsertPlace(row.copy(name = name))
        database.hangoutQueries.relinkPlace(toId = id, name = name, fromId = id)
    }

    override suspend fun mergePlaces(fromId: String, intoId: String) = db.transaction { database ->
        val into = database.namedPlaceQueries.placeById(intoId).awaitAsOneOrNull() ?: return@transaction
        database.hangoutQueries.relinkPlace(toId = intoId, name = into.name, fromId = fromId)
        database.namedPlaceQueries.deletePlace(fromId)
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

/** Links between people (issue #39). Every change also refreshes both people's contacts (sideload build). */
class SqlPersonLinkRepository(
    private val db: AppDatabase,
    writer: ContactsWriter = ContactsWriter.None,
) : PersonLinkRepository {
    private val mirror = ContactMirror(writer)

    override val links: Flow<List<PersonLink>> = db.observe(*LINK_TABLES) { it.readLinks() }

    override suspend fun upsert(link: PersonLink) = change(link.id) { it.writeLink(link) }

    override suspend fun delete(id: String) = change(id) { it.personLinkQueries.delete(id) }

    private suspend fun change(id: String, write: suspend (GoodCompanyDatabase) -> Unit) {
        val (before, after) = db.transaction { database ->
            val people = database.readFriends().associateBy { it.id }
            val links = database.readLinks()
            write(database)
            ContactMirror.Snapshot(people, links) to ContactMirror.Snapshot(people, database.readLinks())
        }
        val touched = (before.links + after.links).filter { it.id == id }.flatMap { listOf(it.from, it.to) }.toSet()
        mirror.sync(touched, before, after)
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
    /** Calendar suggestions (D3) belong to this device: its calendars, never in the database or a backup. */
    val calendarSuggestions: Boolean = false,
    val calendarIds: List<String> = emptyList(),
    val calendarOnSince: Instant? = null,
    val keptTagSpellings: Set<String> = emptySet(),
) {
    fun applyTo(settings: AppSettings) = settings.copy(
        keptTagSpellings = keptTagSpellings,
        aiApiKey = aiApiKey,
        lastIdeasRunAt = lastIdeasRunAt,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        onboardingDone = onboardingDone,
        appLock = appLock,
        lastOpenedAt = lastOpenedAt,
        lastDriveBackupAt = lastDriveBackupAt,
        calendarSuggestions = calendarSuggestions,
        calendarIds = calendarIds,
        calendarOnSince = calendarOnSince,
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
            calendarSuggestions = settings.calendarSuggestions,
            calendarIds = settings.calendarIds,
            calendarOnSince = settings.calendarOnSince,
            keptTagSpellings = settings.keptTagSpellings,
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

    // An unset AI switch follows the saved key, which is a device setting (issue #37). Kept as one long-lived observer:
    // restarting it on every device change would cancel queries mid-flight, which the web driver doesn't survive.
    override val settings: Flow<AppSettings> =
        combine(db.observe(*PREFERENCE_TABLES) { it.readStoredPreferences() }, device.flow) { preferences, device ->
            preferences.withDevice(device::applyTo)
        }

    override suspend fun update(transform: (AppSettings) -> AppSettings) = lock.withLock {
        val current = current()
        val next = transform(current)
        if (next != current) {
            db.access { it.writePreferences(next) }
            device.update { DeviceSettings.of(next) }
        }
    }

    internal suspend fun writeDevice(settings: AppSettings) = device.update { DeviceSettings.of(settings) }

    internal suspend fun current(): AppSettings = db.access { it.readStoredPreferences() }.withDevice(device.value::applyTo)
}

/** Answers to calendar suggestions (D3), in their own table. */
class SqlCalendarDecisionRepository(private val db: AppDatabase) : CalendarDecisionRepository {
    override val decisions: Flow<List<CalendarDecision>> = db.observe(*CALENDAR_DECISION_TABLES) { it.readCalendarDecisions() }

    override suspend fun upsert(decision: CalendarDecision) {
        db.access { it.writeCalendarDecision(decision) }
    }

    override suspend fun delete(key: String) {
        db.access { it.calendarDecisionQueries.deleteDecision(key) }
    }
}

/** Gift lists per occasion (F8), saved whole. */
class SqlGiftListRepository(private val db: AppDatabase) : GiftListRepository {
    override val lists: Flow<List<GiftList>> = db.observe(*GIFT_LIST_TABLES) { it.readGiftLists() }

    override suspend fun get(id: String): GiftList? = db.access { it.readGiftLists() }.firstOrNull { it.id == id }

    override suspend fun upsert(list: GiftList) = db.transaction { it.writeGiftList(list) }

    override suspend fun delete(id: String) {
        db.access { it.giftListQueries.deleteList(id) }
    }
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
                links = database.readLinks(),
                hangouts = database.readHangouts(),
                reminders = database.readReminders(),
                interactions = database.readInteractions(),
                messages = database.readMessages(),
                journal = database.readJournal(),
                attachments = database.readAttachments(),
                ideas = database.readIdeas(),
                groups = database.readGroups(),
                plans = database.readPlans(),
                places = database.readPlaces(),
                calendarDecisions = database.readCalendarDecisions(),
                giftLists = database.readGiftLists(),
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
internal suspend fun GoodCompanyDatabase.replaceAllData(data: Backup) {
    // Kids kept the old way (an old backup, the old JSON store) come in as people (issue #40).
    val backup = KidsAsPeople.convert(data)
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
    namedPlaceQueries.deleteAllPlaces()
    personLinkQueries.deleteAll()
    calendarDecisionQueries.deleteAllDecisions()
    giftListQueries.deleteAllLists()

    backup.friends.forEachIndexed { i, friend -> writeFriend(friend, i.toLong()) }
    backup.ideas.forEach { writeIdea(it) } // before plans, which may point at an idea
    backup.places.forEach { writePlace(it) }
    backup.hangouts.forEach { writeHangout(it) }
    backup.reminders.forEach { writeReminder(it) }
    backup.plans.forEach { writePlan(it) }
    backup.groups.forEach { writeGroup(it) }
    backup.interactions.forEach { writeInteraction(it) }
    backup.messages.forEach { writeMessage(it) }
    backup.journal.forEach { writeJournal(it) }
    backup.attachments.forEach { writeAttachment(it) } // after friends and circles, which they belong to
    backup.links.forEach { writeLink(it) } // after friends, which they link
    backup.calendarDecisions.forEach { writeCalendarDecision(it) }
    backup.giftLists.forEach { writeGiftList(it) } // after friends (and their gifts)
    writePreferences(backup.settings)
}
