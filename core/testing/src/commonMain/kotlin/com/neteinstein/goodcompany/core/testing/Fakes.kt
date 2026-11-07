package com.neteinstein.goodcompany.core.testing

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaGenerator
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
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
) : FriendRepository {
    private val state = MutableStateFlow(initial)
    override val friends: StateFlow<List<Friend>> = state

    override suspend fun get(id: FriendId) = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(friend: Friend) = state.update { list ->
        if (list.any { it.id == friend.id }) list.map { if (it.id == friend.id) friend else it } else list + friend
    }

    override suspend fun delete(id: FriendId) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun syncContacts(): Int {
        state.update { ContactMerger.merge(it, contacts) }
        return contacts.size
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
    private val state = MutableStateFlow(initial)
    private val messageState = MutableStateFlow(emptyList<ConversationMessage>())
    override val interactions: StateFlow<List<Interaction>> = state
    override val messages: StateFlow<List<ConversationMessage>> = messageState

    override suspend fun addAll(interactions: List<Interaction>) = state.update { (it + interactions).distinct() }

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
    val scheduled = mutableListOf<List<PlannedNotification>>()

    override suspend fun replaceAll(notifications: List<PlannedNotification>) {
        scheduled += notifications
    }
}

/** Terse builders for tests. */
object Fixtures {
    fun friend(id: String, name: String = id.replaceFirstChar { it.uppercase() }, block: Friend.() -> Friend = { this }) =
        Friend(id = FriendId(id), name = name).block()

    fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Instant = LocalDateTime(y, m, d, h, min).toInstant(TimeZone.UTC)
}
