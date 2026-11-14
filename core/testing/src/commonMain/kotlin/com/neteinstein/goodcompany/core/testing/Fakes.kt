package com.neteinstein.goodcompany.core.testing

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.RenderedAction
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.domain.repository.AiAssistant
import com.neteinstein.goodcompany.core.domain.repository.AppUpdateSource
import com.neteinstein.goodcompany.core.domain.repository.BackupRepository
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupInfo
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupStore
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.FeedFetcher
import com.neteinstein.goodcompany.core.domain.repository.FriendProfile
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaGenerator
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationRenderer
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Instant

/** A clock pinned to [instant] in UTC unless told otherwise. Advance it with [advanceBy]. */
class FixedClock(
    var instant: Instant = LocalDateTime(2026, 3, 10, 12, 0).toInstant(TimeZone.UTC),
    override val timeZone: TimeZone = TimeZone.UTC,
) : AppClock {
    override fun now(): Instant = instant

    fun advanceBy(d: Duration) {
        instant += d
    }
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var n = 0

    override fun next(): String = "$prefix-${++n}"
}

class FakeFriendRepository(
    initial: List<Friend> = emptyList(),
    var contacts: List<ContactRecord> = emptyList(),
    /** Like `ContactsSource.importsNewContacts`: false where a sync only refreshes friends already linked to a contact. */
    var importsNewContacts: Boolean = true,
) : FriendRepository {
    private val state = MutableStateFlow(initial)
    override val friends: StateFlow<List<Friend>> = state

    override suspend fun get(id: FriendId) = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(friend: Friend) = state.update { list ->
        if (list.any { it.id == friend.id }) list.map { if (it.id == friend.id) friend else it } else list + friend
    }

    override suspend fun delete(id: FriendId) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun syncContacts(): Int {
        state.update { ContactMerger.merge(it, contacts, addNew = importsNewContacts) }
        return contacts.size
    }

    /** Every [readContacts] call, with its `withPhotos` flag. */
    val contactReads = mutableListOf<Boolean>()

    /** The contact ids asked for in [contactPhotos]. */
    val photoRequests = mutableListOf<Set<String>>()

    override suspend fun readContacts(withPhotos: Boolean): List<ContactRecord> {
        contactReads += withPhotos
        return if (withPhotos) contacts else contacts.map { it.copy(photo = null) }
    }

    override suspend fun contactPhotos(contactIds: Set<String>): Map<String, ContactPhoto> {
        photoRequests += contactIds
        return contacts.filter { it.contactId in contactIds }.mapNotNull { r -> r.photo?.let { r.contactId to it } }.toMap()
    }
}

/** Records what would have been mirrored onto contacts. [written] is the latest card per friend. */
class FakeContactsWriter : ContactsWriter {
    val written = mutableMapOf<FriendId, ContactCard>()
    val contactIds = mutableMapOf<FriendId, String>()
    var writes = 0
        private set

    override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) {
        writes++
        written[friendId] = card
        contactIds[friendId] = contactId
    }
}

class FakeHangoutRepository(initial: List<Hangout> = emptyList()) : HangoutRepository {
    private val state = MutableStateFlow(initial)
    override val hangouts: StateFlow<List<Hangout>> = state

    override suspend fun add(hangout: Hangout) = state.update { it + hangout }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }
}

class FakeReminderRepository(initial: List<Reminder> = emptyList()) : ReminderRepository {
    private val state = MutableStateFlow(initial)
    override val reminders: StateFlow<List<Reminder>> = state

    override suspend fun upsert(reminder: Reminder) = state.update { list ->
        if (list.any { it.id == reminder.id }) list.map { if (it.id == reminder.id) reminder else it } else list + reminder
    }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }
}

class FakeInteractionRepository(
    initial: List<Interaction> = emptyList(),
    var logEntries: List<Interaction> = emptyList(),
) : InteractionRepository {
    private val state = MutableStateFlow(initial.map { it.withId() })
    private val messageState = MutableStateFlow(emptyList<ConversationMessage>())
    override val interactions: StateFlow<List<Interaction>> = state
    override val messages: StateFlow<List<ConversationMessage>> = messageState
    private val journalState = MutableStateFlow(emptyList<JournalEntry>())
    override val journal: StateFlow<List<JournalEntry>> = journalState

    override suspend fun addAll(interactions: List<Interaction>) = state.update { current ->
        (current + interactions.map { it.withId() }).distinctBy { it.id }
    }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun upsertJournal(entry: JournalEntry) = journalState.update { list -> list.filterNot { it.id == entry.id } + entry }

    override suspend fun deleteJournal(id: String) = journalState.update { list -> list.filterNot { it.id == id } }

    private val attachmentState = MutableStateFlow(emptyList<Attachment>())
    override val attachments: StateFlow<List<Attachment>> = attachmentState

    override suspend fun upsertAttachment(attachment: Attachment) =
        attachmentState.update { list -> list.filterNot { it.id == attachment.id } + attachment }

    override suspend fun deleteAttachment(id: String) = attachmentState.update { list -> list.filterNot { it.id == id } }

    override suspend fun addMessages(messages: List<ConversationMessage>) = messageState.update { (it + messages).distinct() }

    override suspend fun syncCommunicationLogs(friends: List<Friend>): Int {
        addAll(logEntries)
        return logEntries.size
    }
}

class FakeIdeaRepository : IdeaRepository {
    private val state = MutableStateFlow(emptyList<Idea>())
    override val ideas: StateFlow<List<Idea>> = state

    override suspend fun addAll(ideas: List<Idea>) = state.update { it + ideas }

    override suspend fun dismiss(id: String) = state.update { list -> list.map { if (it.id == id) it.copy(dismissed = true) else it } }

    override suspend fun markProposed(id: String, at: Instant) = state.update { list ->
        list.map {
            if (it.id ==
                id
            ) {
                it.copy(proposedAt = at)
            } else {
                it
            }
        }
    }
}

class FakeGroupRepository(initial: List<FriendGroup> = emptyList()) : GroupRepository {
    private val state = MutableStateFlow(initial)
    override val groups: StateFlow<List<FriendGroup>> = state

    override suspend fun upsert(group: FriendGroup) = state.update { list ->
        if (list.any { it.id == group.id }) list.map { if (it.id == group.id) group else it } else list + group
    }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }
}

class FakePlanRepository(initial: List<Plan> = emptyList()) : PlanRepository {
    private val state = MutableStateFlow(initial)
    override val plans: StateFlow<List<Plan>> = state

    override suspend fun upsert(plan: Plan) = state.update { list ->
        if (list.any { it.id == plan.id }) list.map { if (it.id == plan.id) plan else it } else list + plan
    }

    override suspend fun delete(id: String) = state.update { list -> list.filterNot { it.id == id } }
}

/** Backup over the other fakes, like the real one over the real stores. */
class FakeBackupRepository(
    private val friends: FakeFriendRepository,
    private val hangouts: FakeHangoutRepository,
    private val reminders: FakeReminderRepository,
    private val interactions: FakeInteractionRepository,
    private val ideas: FakeIdeaRepository,
    private val groups: FakeGroupRepository,
    private val plans: FakePlanRepository,
    private val settings: FakeSettingsRepository,
) : BackupRepository {
    var restored: Backup? = null

    override suspend fun snapshot() = Backup(
        friends = friends.friends.value,
        hangouts = hangouts.hangouts.value,
        reminders = reminders.reminders.value,
        interactions = interactions.interactions.value,
        messages = interactions.messages.value,
        journal = interactions.journal.value,
        attachments = interactions.attachments.value,
        ideas = ideas.ideas.value,
        groups = groups.groups.value,
        plans = plans.plans.value,
        settings = settings.settings.value,
    )

    override suspend fun restore(backup: Backup) {
        restored = backup
        friends.friends.value.forEach { friends.delete(it.id) }
        backup.friends.forEach { friends.upsert(it) }
        hangouts.hangouts.value.forEach { hangouts.delete(it.id) }
        backup.hangouts.forEach { hangouts.add(it) }
        plans.plans.value.forEach { plans.delete(it.id) }
        backup.plans.forEach { plans.upsert(it) }
        settings.update { backup.settings }
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

class FakeIdeaGenerator(var result: Result<List<GeneratedIdea>> = Result.success(emptyList())) : IdeaGenerator {
    val requests = mutableListOf<IdeaRequest>()

    override suspend fun generate(request: IdeaRequest): Result<List<GeneratedIdea>> {
        requests += request
        return result
    }
}

class FakeLocationProvider(var point: GeoPoint? = null) : LocationProvider {
    override suspend fun currentLocation(): GeoPoint? = point
}

class FakeNotificationScheduler : NotificationScheduler {
    val scheduled = mutableListOf<List<RenderedNotification>>()

    override suspend fun replaceAll(notifications: List<RenderedNotification>) {
        scheduled += notifications
    }
}

/** Renders the content's class name as the title and its toString() as the body; meetups get the four actions. */
object FakeNotificationRenderer : NotificationRenderer {
    override suspend fun render(notification: PlannedNotification): RenderedNotification {
        val c = notification.content
        val actions = when (c) {
            is NotificationContent.MeetupDue -> NotificationAction.Kind.entries.map {
                RenderedAction(NotificationAction(it, c.friendId.value), it.name)
            }
            is NotificationContent.GroupMeetupDue -> listOf(
                RenderedAction(NotificationAction(NotificationAction.Kind.MET, c.groupId, isGroup = true), "MET"),
            )
            else -> emptyList()
        }
        return RenderedNotification(notification.id, notification.at, c::class.simpleName.orEmpty(), c.toString(), actions)
    }

    override suspend fun privateText(): String = "PRIVATE"
}

class FakeAiAssistant(
    var message: Result<String> = Result.success("Happy birthday!"),
    var gifts: Result<List<String>> = Result.success(listOf("Vinyl", "Concert tickets")),
    var lifeUpdate: Result<String?> = Result.success("Started a new job"),
) : AiAssistant {
    val calls = mutableListOf<String>()

    override suspend fun birthdayMessage(apiKey: String, friend: FriendProfile, languageTag: String): Result<String> {
        calls += "message:${friend.id}:$languageTag"
        return message
    }

    override suspend fun giftIdeas(
        apiKey: String,
        friend: FriendProfile,
        kidName: String?,
        kidLikes: List<String>,
        languageTag: String,
    ): Result<List<String>> {
        calls += "gifts:${friend.id}:${kidName ?: "-"}:$languageTag"
        return gifts
    }

    override suspend fun lifeUpdateFromShared(
        apiKey: String,
        friend: FriendProfile,
        text: String?,
        image: SharedImage?,
        languageTag: String,
    ): Result<String?> {
        calls += "update:${friend.id}:${text ?: "-"}:${image?.mediaType ?: "-"}:$languageTag"
        return lifeUpdate
    }
}

/** Terse builders for tests. */
object Fixtures {
    fun friend(id: String, name: String = id.replaceFirstChar { it.uppercase() }, block: Friend.() -> Friend = { this }) =
        Friend(id = FriendId(id), name = name).block()

    fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Instant = LocalDateTime(y, m, d, h, min).toInstant(TimeZone.UTC)
}

/** Serves canned bodies by URL; anything else fails like a 404. Records every request. */
class FakeFeedFetcher(val pages: MutableMap<String, String> = mutableMapOf()) : FeedFetcher {
    val requests = mutableListOf<String>()

    override suspend fun fetch(url: String): String {
        requests += url
        return pages[url] ?: error("HTTP 404")
    }
}

/** In-memory Google Drive: one map of files; [failure] makes every call throw. */
class FakeCloudBackupStore : CloudBackupStore {
    data class File(val id: String, val name: String, val content: String, val modifiedAt: Instant?)

    val files = mutableListOf<File>()
    val tokens = mutableListOf<String>()
    var failure: CloudBackupFailure? = null
    var now: Instant? = null

    override suspend fun save(accessToken: String, fileName: String, content: String): CloudBackupInfo {
        tokens += accessToken
        failure?.let { throw it }
        val existing = files.lastOrNull { it.name == fileName }
        val file = File(existing?.id ?: "drive-${files.size + 1}", fileName, content, now)
        if (existing != null) files[files.indexOf(existing)] = file else files += file
        return CloudBackupInfo(file.id, file.modifiedAt)
    }

    override suspend fun latest(accessToken: String, fileName: String): CloudBackupInfo? {
        tokens += accessToken
        failure?.let { throw it }
        return files.lastOrNull { it.name == fileName }?.let { CloudBackupInfo(it.id, it.modifiedAt) }
    }

    override suspend fun download(accessToken: String, id: String): String {
        tokens += accessToken
        failure?.let { throw it }
        return files.first { it.id == id }.content
    }
}

/** The update source: [newest] is what it reports; [failure] makes it throw. */
class FakeAppUpdateSource(var newest: AppUpdate? = null) : AppUpdateSource {
    var failure: Exception? = null
    var calls = 0

    override suspend fun latest(): AppUpdate? {
        calls++
        failure?.let { throw it }
        return newest
    }
}
