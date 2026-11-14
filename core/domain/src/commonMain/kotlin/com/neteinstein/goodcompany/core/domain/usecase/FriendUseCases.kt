package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
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
    /** Their parents among your people (F9): a kid's row says whose kid they are. */
    val parents: List<Friend> = emptyList(),
)

/** Friends sorted close-friends-first, then alphabetically, with last-seen/contact info attached. */
class ObserveFriendsUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val links: PersonLinkRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<FriendSummary>> = combine(
        friends.friends,
        hangouts.hangouts,
        interactions.interactions,
        settings.settings,
        links.links,
    ) { all, hs, ints, s, ls ->
        val parents = KidsAsPeople.parentsById(ls, all)
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
                parents = parents[f.id].orEmpty(),
            )
        }.sortedWith(compareByDescending<FriendSummary> { it.friend.isCloseFriend }.thenBy { it.friend.displayName.lowercase() })
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
    /** The circles they're in, the built-in Family circle first when they're family. */
    val groups: List<FriendGroup> = emptyList(),
    /** Every circle that can be ticked for them (issue #70): the stored ones, never the built-in Family circle. */
    val allCircles: List<FriendGroup> = emptyList(),
    /** Every circle, Family included: the people picker's filter row (issue #89). */
    val circles: List<FriendGroup> = emptyList(),
    /** Things to bring up, from notes and dates you already have (works without an AI key). */
    val topics: List<TopicHint> = emptyList(),
    /** Planned meetups with this friend (or a circle they're in) that haven't happened yet. */
    val plans: List<Plan> = emptyList(),
    /** Everyone else, A–Z: who could have introduced you (the "introduced by" picker and link). */
    val others: List<Friend> = emptyList(),
    /** "More about" labels already used on other friends, most used first: suggestions for a new field. */
    val customFieldLabels: List<String> = emptyList(),
    /** Their whole history, newest first (the friend page shows the first few). */
    val timeline: List<TimelineEntry> = emptyList(),
    /** The newest journal entry: "last time you talked about…". */
    val lastJournal: JournalEntry? = null,
    /** Their kids (F9): people linked to them as their child, in the order they were added. */
    val kids: List<LinkedPerson> = emptyList(),
    /** Their parents among your people (F9): set for a kid, whose page then offers "Show in my list". */
    val parents: List<Friend> = emptyList(),
    /** What they may be to you (F7), worked out from your relatives' links; null when nothing is proposed. */
    val kinship: KinshipProposal? = null,
)

class ObserveFriendDetailUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val ideas: IdeaRepository,
    private val settings: SettingsRepository,
    private val groups: GroupRepository,
    private val plans: PlanRepository,
    private val links: PersonLinkRepository,
    private val clock: AppClock,
) {
    operator fun invoke(id: FriendId): Flow<FriendDetail?> = combine(
        friends.friends,
        hangouts.hangouts,
        interactions.interactions,
        interactions.messages,
        combine(
            ideas.ideas,
            settings.settings,
            groups.groups,
            plans.plans,
            combine(interactions.journal, interactions.attachments, links.links, ::Triple),
        ) { i, s, g, p, jal -> Extras(i, s, g, p, jal.first, jal.second, jal.third) },
    ) { all, hs, ints, msgs, (ideaList, s, gs, ps, journal, attachments, ls) ->
        val friend = all.firstOrNull { it.id == id } ?: return@combine null
        val byId = all.associateBy { it.id }
        val kids = KidsAsPeople.kidsOf(id, ls, byId)
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
            allCircles = gs.filterNot { it.isBuiltIn },
            circles = gs,
            topics = OfflineTopics.build(friend, hs, clock.today(), clock.timeZone, kids.map { it.person }),
            plans = ps.filter { p ->
                p.status == PlanStatus.PLANNED && (id in p.friendIds || gs.any { it.id == p.circleId && id in it.memberIds })
            }.sortedBy { it.date },
            others = all.filter { it.id != id }.sortedBy { it.displayName.lowercase() },
            customFieldLabels = customFieldLabels(all.filter { it.id != id }),
            timeline = Timeline.build(id, all, hs, ints, journal, clock.timeZone, attachments),
            lastJournal = journal.filter { it.friendId == id }.maxByOrNull { it.at },
            kids = kids,
            parents = KidsAsPeople.parentsOf(id, ls, byId),
            kinship = if (friend.relationToMe == null) KinshipResolver.proposals(all, ls).firstOrNull { it.person.id == id } else null,
        )
    }

    /** Labels used on [friends]' custom fields, most used first (ties A–Z), case-insensitively distinct. */
    fun customFieldLabels(friends: List<Friend>): List<String> = friends.flatMap { f -> f.customFields.map { it.label.trim() } }
        .filter { it.isNotEmpty() }
        .groupBy { it.lowercase() }
        .values
        .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { it.first().lowercase() })
        .map { it.first() }

    private data class Extras(
        val ideas: List<Idea>,
        val settings: AppSettings,
        val groups: List<FriendGroup>,
        val plans: List<Plan>,
        val journal: List<JournalEntry>,
        val attachments: List<Attachment>,
        val links: List<PersonLink>,
    )
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
    /** New people start as Acquaintances (issue #86). */
    suspend operator fun invoke(name: String): FriendId {
        require(name.isNotBlank()) { "Name required" }
        val friend = Friend(id = FriendId("manual-${ids.next()}"), name = name.trim())
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
 * Imports a WhatsApp chat export for one friend: one WHATSAPP interaction per day with messages (and how many),
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
        val perDay = parsed.groupBy { it.at.toLocalDate(clock.timeZone) }.values.map { day ->
            val at = day.maxOf { it.at }
            Interaction(
                friendId = id,
                type = InteractionType.WHATSAPP,
                at = at,
                id = Interaction.automaticId(id, InteractionType.WHATSAPP, at),
                source = InteractionSource.CHAT_IMPORT,
                messageCount = day.size,
            )
        }
        interactions.addAll(perDay)
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
