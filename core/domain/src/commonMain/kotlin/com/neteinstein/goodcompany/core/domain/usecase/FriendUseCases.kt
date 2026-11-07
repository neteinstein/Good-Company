package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** A friend plus the derived facts the list screen shows. */
data class FriendSummary(
    val friend: Friend,
    val lastSeen: Instant?,
    val lastContact: Instant?,
    val nextBirthday: LocalDate?,
    val meetupOverdue: Boolean,
    val warmth: Warmth.Score = Warmth.Score(0, Warmth.Level.FADING),
    val cadenceDays: Int? = null,
)

/** Friends sorted close-friends-first, then alphabetically, with last-seen/contact info attached. */
class ObserveFriendsUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<FriendSummary>> = combine(
        friends.friends,
        hangouts.hangouts,
        interactions.interactions,
        settings.settings,
    ) { all, hs, ints, s ->
        val today = clock.today()
        val rules = CadenceRules.from(s)
        val seen = Relationships.lastSeen(hs)
        val contact = Relationships.lastContact(hs, ints)
        val overdue = Relationships.meetupStatuses(all, hs, today, clock.timeZone, rules, ints)
            .filter { it.isOverdue(today) }.map { it.friend.id }.toSet()
        all.map { f ->
            val cadence = rules.cadenceFor(f)
            FriendSummary(
                friend = f,
                lastSeen = seen[f.id],
                lastContact = contact[f.id],
                nextBirthday = f.birthday?.nextOccurrence(today),
                meetupOverdue = f.id in overdue,
                warmth = Warmth.score(f, hs, ints, cadence, today, clock.timeZone),
                cadenceDays = cadence,
            )
        }.sortedWith(compareByDescending<FriendSummary> { it.friend.isCloseFriend }.thenBy { it.friend.name.lowercase() })
    }
}

data class HangoutWithFriends(val hangout: Hangout, val friends: List<Friend>)

data class FriendDetail(
    val friend: Friend,
    val hangouts: List<HangoutWithFriends>,
    val lastSeen: Instant?,
    val lastContactByType: Map<InteractionType, Instant>,
    val messageCount: Int,
    val ideas: List<Idea>,
    val warmth: Warmth.Score = Warmth.Score(0, Warmth.Level.FADING),
    val cadenceDays: Int? = null,
    val cadenceSource: CadenceRules.Source? = null,
    val groups: List<FriendGroup> = emptyList(),
    /** Things to bring up, from notes and dates you already have (works without an AI key). */
    val topics: List<TopicHint> = emptyList(),
    /** Planned meetups with this friend that haven't happened yet. */
    val plans: List<Plan> = emptyList(),
)

class ObserveFriendDetailUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val ideas: IdeaRepository,
    private val settings: SettingsRepository,
    private val groups: GroupRepository,
    private val plans: PlanRepository,
    private val clock: AppClock,
) {
    operator fun invoke(id: FriendId): Flow<FriendDetail?> = combine(
        friends.friends,
        hangouts.hangouts,
        interactions.interactions,
        interactions.messages,
        combine(ideas.ideas, settings.settings, groups.groups, plans.plans) { i, s, g, p -> Extras(i, s, g, p) },
    ) { all, hs, ints, msgs, (ideaList, s, gs, ps) ->
        val friend = all.firstOrNull { it.id == id } ?: return@combine null
        val byId = all.associateBy { it.id }
        val mine = hs.filter { id in it.friendIds }.sortedByDescending { it.at }
        val byType = ints.filter { it.friendId == id }.groupBy { it.type }.mapValues { (_, v) -> v.maxOf { it.at } }
            .toMutableMap()
        mine.firstOrNull()?.let { byType[InteractionType.MEETUP] = it.at }
        FriendDetail(
            friend = friend,
            hangouts = mine.map { h -> HangoutWithFriends(h, h.friendIds.mapNotNull(byId::get)) },
            lastSeen = mine.firstOrNull()?.at,
            lastContactByType = byType,
            messageCount = msgs.count { it.friendId == id },
            ideas = ideaList.filter { id in it.friendIds && !it.dismissed }.sortedByDescending { it.createdAt },
            warmth = Warmth.score(friend, hs, ints, CadenceRules.from(s).cadenceFor(friend), clock.today(), clock.timeZone),
            cadenceDays = CadenceRules.from(s).cadenceFor(friend),
            cadenceSource = CadenceRules.from(s).sourceFor(friend),
            groups = gs.filter { id in it.memberIds },
            topics = OfflineTopics.build(friend, hs, clock.today(), clock.timeZone),
            plans = ps.filter { id in it.friendIds && it.status == PlanStatus.PLANNED }.sortedBy { it.date },
        )
    }

    private data class Extras(val ideas: List<Idea>, val settings: AppSettings, val groups: List<FriendGroup>, val plans: List<Plan>)
}

/** Applies an edit to one friend (toggles, likes, kids, rules…). No-op if the friend is gone. */
class UpdateFriendUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, transform: (Friend) -> Friend) {
        val current = friends.get(id) ?: return
        friends.upsert(transform(current))
    }
}

/** Creates a friend that isn't in the address book (and the only way on the web). */
class AddFriendUseCase(private val friends: FriendRepository, private val ids: IdGenerator) {
    suspend operator fun invoke(name: String, isCloseFriend: Boolean = true): FriendId {
        require(name.isNotBlank()) { "Name required" }
        val friend = Friend(id = FriendId("manual-${ids.next()}"), name = name.trim(), isCloseFriend = isCloseFriend)
        friends.upsert(friend)
        return friend.id
    }
}

class DeleteFriendUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId) = friends.delete(id)
}

data class SyncResult(val contacts: Int, val logEntries: Int)

/** Address book → friends, then call log → interactions (Android sideload build only; elsewhere returns 0). */
class SyncContactsUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
) {
    suspend operator fun invoke(): SyncResult {
        val contacts = friends.syncContacts()
        val logs = interactions.syncCommunicationLogs(friends.friends.first())
        return SyncResult(contacts, logs)
    }
}

data class ChatImportResult(val messages: Int, val friendSender: String?, val lastMessageAt: Instant?)

/**
 * Imports a WhatsApp chat export for one friend: one WHATSAPP interaction per day with messages,
 * plus the last [KEEP_MESSAGES] lines as AI context.
 */
class ImportChatUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(id: FriendId, raw: String): ChatImportResult {
        val friend = friends.get(id) ?: return ChatImportResult(0, null, null)
        val parsed = WhatsAppChatParser.parse(raw, clock.timeZone)
        if (parsed.isEmpty()) return ChatImportResult(0, null, null)
        val friendSender = WhatsAppChatParser.pickFriendSender(parsed, friend.name)
        val perDay = parsed.groupBy { it.at.toLocalDate(clock.timeZone) }.values.map { day -> day.maxOf { it.at } }
        interactions.addAll(perDay.map { Interaction(id, InteractionType.WHATSAPP, it) })
        interactions.addMessages(
            parsed.takeLast(KEEP_MESSAGES).map {
                ConversationMessage(id, it.at, fromMe = it.sender != friendSender, text = it.text.take(MAX_MESSAGE_CHARS))
            },
        )
        return ChatImportResult(parsed.size, friendSender, parsed.maxOf { it.at })
    }

    companion object {
        const val KEEP_MESSAGES = 60
        const val MAX_MESSAGE_CHARS = 400
    }
}
