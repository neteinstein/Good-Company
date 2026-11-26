package org.goodcompany.app.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.goodcompany.app.core.domain.model.AddressKind
import org.goodcompany.app.core.domain.model.Attachment
import org.goodcompany.app.core.domain.model.AttachmentKind
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.GeoPoint
import org.goodcompany.app.core.domain.model.Gift
import org.goodcompany.app.core.domain.model.InteractionSource
import org.goodcompany.app.core.domain.model.LifeUpdate
import org.goodcompany.app.core.domain.model.NamedPlace
import org.goodcompany.app.core.domain.model.PostalAddress
import org.goodcompany.app.core.domain.model.SharedContent
import org.goodcompany.app.core.domain.repository.AiAssistant
import org.goodcompany.app.core.domain.repository.FriendRepository
import org.goodcompany.app.core.domain.repository.GroupRepository
import org.goodcompany.app.core.domain.repository.HangoutRepository
import org.goodcompany.app.core.domain.repository.InteractionRepository
import org.goodcompany.app.core.domain.repository.LanguageProvider
import org.goodcompany.app.core.domain.repository.PersonLinkRepository
import org.goodcompany.app.core.domain.repository.PhotoShrinker
import org.goodcompany.app.core.domain.repository.SettingsRepository
import org.goodcompany.app.core.domain.repository.photoOf
import org.goodcompany.app.core.domain.util.AppClock
import org.goodcompany.app.core.domain.util.IdGenerator
import org.goodcompany.app.core.domain.util.LocationLinks
import org.goodcompany.app.core.domain.util.PhoneMatcher
import org.goodcompany.app.core.domain.util.SharedLocation
import org.goodcompany.app.core.domain.util.toLocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Shares waiting for the user to pick a friend. Platforms [offer] what the share sheet handed them (Android intent,
 * iOS share extension inbox, web share target) or what was picked with "Add a photo"; the app shows the "Save to a
 * friend" screen while [pending] is set. In memory only: an unsaved share is simply dropped.
 */
class ShareInbox {
    private val state = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = state.asStateFlow()

    /** Who the share is for when it was started from a friend's page ("Add a photo"); the who step is skipped. */
    private val forFriend = MutableStateFlow<FriendId?>(null)
    val preselected: StateFlow<FriendId?> = forFriend.asStateFlow()

    fun offer(content: SharedContent, friend: FriendId? = null) {
        if (!content.isEmpty) {
            forFriend.value = friend
            state.value = content
        }
    }

    fun clear() {
        state.value = null
        forFriend.value = null
    }
}

/** Why a friend is ranked first on the share screen (C1: never face recognition). */
enum class ShareReason { MENTIONED, NUMBER, SEEN_TODAY, RECENT }

data class RankedFriend(val friend: Friend, val reason: ShareReason?)

/**
 * Ranks friends for a share (decision C1): people named in the shared text (full name, "firstlast" handle or first
 * name), then people whose phone number is in it, then people in touch in the last [RECENT_DAYS] days (latest first),
 * then real friends, then A–Z. The user always confirms.
 */
object ShareMatcher {
    const val RECENT_DAYS = 14

    fun rank(
        friends: List<Friend>,
        content: SharedContent,
        lastContact: Map<FriendId, Instant> = emptyMap(),
        now: Instant? = null,
        zone: TimeZone = TimeZone.UTC,
    ): List<RankedFriend> {
        val haystack = haystack(content)
        val numbers = PhoneMatcher.keysIn(listOfNotNull(content.text, content.title).joinToString(" "))
        val ranked = friends.map { f -> RankedFriend(f, reason(f, haystack, numbers, lastContact[f.id], now, zone)) }
        return ranked.sortedWith(
            compareBy<RankedFriend> { it.reason?.ordinal?.let { o -> minOf(o, ShareReason.SEEN_TODAY.ordinal) } ?: Int.MAX_VALUE }
                .thenByDescending { if (it.reason == ShareReason.SEEN_TODAY || it.reason == ShareReason.RECENT) lastContact[it.friend.id] else null }
                .thenByDescending { it.friend.isCloseFriend }
                .thenBy { it.friend.displayName.lowercase() },
        )
    }

    fun mentions(friends: List<Friend>, content: SharedContent): Set<FriendId> {
        val haystack = haystack(content)
        return friends.filter { isMentioned(it, haystack) }.map { it.id }.toSet()
    }

    private fun reason(f: Friend, haystack: String, numbers: Set<String>, last: Instant?, now: Instant?, zone: TimeZone): ShareReason? = when {
        isMentioned(f, haystack) -> ShareReason.MENTIONED
        numbers.isNotEmpty() && f.phoneNumbers.any { PhoneMatcher.key(it) in numbers } -> ShareReason.NUMBER
        last != null && now != null && last.toLocalDate(zone) == now.toLocalDate(zone) -> ShareReason.SEEN_TODAY
        last != null && now != null && now - last <= RECENT_DAYS.days -> ShareReason.RECENT
        else -> null
    }

    private fun haystack(content: SharedContent) = listOfNotNull(content.text, content.title, content.url).joinToString(" ").lowercase()

    /** Whether [text] names [f] (full name, "firstlast" handle or a first name of 3+ letters). Also used by calendar matching. */
    fun isNamedIn(f: Friend, text: String): Boolean = isMentioned(f, text.lowercase())

    /** By the name given in the app or the contact's (F19). */
    private fun isMentioned(f: Friend, haystack: String): Boolean =
        listOf(f.displayName, f.name).distinct().any { isMentioned(it, haystack) }

    private fun isMentioned(name: String, haystack: String): Boolean {
        if (haystack.isBlank()) return false
        val full = name.lowercase().trim()
        val first = full.substringBefore(' ')
        return full in haystack ||
            (full.contains(' ') && full.replace(" ", "") in haystack) ||
            (first.length >= MIN_FIRST_NAME && Regex("\\b${Regex.escape(first)}\\b").containsMatchIn(haystack))
    }

    private const val MIN_FIRST_NAME = 3
}

/** Everyone a share can be saved for: friends with when you were last in touch, and circles (F12). */
data class ShareTargets(
    val friends: List<Friend> = emptyList(),
    val lastContact: Map<FriendId, Instant> = emptyMap(),
    val circles: List<FriendGroup> = emptyList(),
    /** Every circle with members, Family included: the people picker's filter row (issue #89), not a target. */
    val filterCircles: List<FriendGroup> = emptyList(),
)

class ObserveShareTargetsUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val groups: GroupRepository,
) {
    operator fun invoke(): Flow<ShareTargets> = combine(
        friends.friends,
        hangouts.hangouts,
        interactions.interactions,
        groups.groups,
    ) { fs, hs, ints, gs ->
        // Not the built-in Family circle: it isn't stored, so nothing can be kept on it (share to its people instead).
        val withMembers = gs.filter { it.memberIds.isNotEmpty() }
        ShareTargets(fs, Relationships.lastContact(hs, ints), withMembers.filterNot { it.isBuiltIn }, withMembers)
    }
}

/** What a share is saved as on the confirm step (D6, F17). */
enum class ShareChoice {
    /** Logged (timeline, last contact) and kept as an attachment. */
    INTERACTION,

    /** Kept on the page only; last contact stays as it is. */
    NOTE,

    /** F17: a gift idea for the person (link, title, optional price). Not offered for a circle. */
    GIFT_IDEA,

    /** Issue #170, a location: opens the plan editor with the place filled in; the day and time are still to pick. */
    PLAN_HERE,

    /** Issue #170, a location: a meetup there (through [LogInteractionUseCase]), kept with the link as an attachment. */
    MET_HERE,

    /** Issue #170, a location: the person's home address (#169). Not offered for a circle. */
    HOME_ADDRESS,

    /** Issue #170, a location: the person's work address (#169). Not offered for a circle. */
    WORK_ADDRESS,

    /** Issue #170, a location with coordinates: a [NamedPlace] (F11), e.g. "Inês’s house". */
    NAMED_PLACE,
    ;

    /** The choices only a location offers. */
    val isLocation: Boolean get() = this in LOCATION

    /** Only for one person, never a circle. */
    val isPersonOnly: Boolean get() = this == GIFT_IDEA || this == HOME_ADDRESS || this == WORK_ADDRESS

    companion object {
        val LOCATION = setOf(PLAN_HERE, MET_HERE, HOME_ADDRESS, WORK_ADDRESS, NAMED_PLACE)
    }
}

/**
 * The place on the confirm step (issue #170), as the user edited it: [name] and [address] start from what the share
 * carried; [point] and [link] are the link's and can't be typed.
 */
data class SharePlace(val name: String = "", val address: String = "", val point: GeoPoint? = null, val link: String? = null) {
    /** What a plan or a meetup calls the place: the name, else the address. */
    val label: String? get() = name.trim().ifEmpty { address.trim() }.ifEmpty { null }

    /** What a home or work address holds: the address, else the name. */
    val addressText: String? get() = address.trim().ifEmpty { name.trim() }.ifEmpty { null }

    companion object {
        fun of(location: SharedLocation) = SharePlace(location.name.orEmpty(), location.address.orEmpty(), location.point, location.link)
    }
}

/** The guess the confirm step opens with; the user can always change it. [location] is set for a maps link (issue #170). */
data class ShareGuess(val choice: ShareChoice, val kind: LogKind, val date: LocalDate?, val location: SharedLocation? = null)

/** Who a share is saved for (F12: a person or a circle). */
sealed interface ShareTarget {
    data class Person(val id: FriendId) : ShareTarget

    data class Circle(val id: String) : ShareTarget
}

/**
 * The confirm step's starting point: a photo was probably taken together (Met, on its own date), chat-like text is a
 * message, a link is just an idea; other text is a note.
 */
object ShareGuesser {
    /** WhatsApp-style "[13/11/26, 13:02] Rui:" or "13/11/2026 13:02 - Rui:" lines, or a "Rui: …" speaker line. */
    private val TIMESTAMP = Regex("""^\[?\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4},? \d{1,2}:\d{2}""", RegexOption.MULTILINE)
    private val SPEAKER = Regex("""^[\p{L}][\p{L} .'’\-]{0,30}: \S""", RegexOption.MULTILINE)

    /** Short text (a message or two) reads as chat even without a speaker. */
    private const val SHORT_TEXT = 500
    private const val SHORT_LINES = 4

    fun guess(content: SharedContent): ShareGuess {
        // Issue #170: a maps link (even a bare short link with no place text) is a location. Nothing is saved or
        // logged until the user picks what it becomes, so it starts on "just keep it", as any link does.
        val location = if (content.image == null) LocationLinks.parse(content) else null
        if (location != null) return ShareGuess(ShareChoice.NOTE, LogKind.MEETUP, null, location)
        return guessOther(content)
    }

    private fun guessOther(content: SharedContent): ShareGuess = when (content.kind) {
        AttachmentKind.PHOTO -> ShareGuess(ShareChoice.INTERACTION, LogKind.MEETUP, content.takenOn)
        AttachmentKind.LINK -> ShareGuess(ShareChoice.NOTE, LogKind.MESSAGE, null)
        AttachmentKind.TEXT ->
            if (isChatLike(content.text.orEmpty())) {
                ShareGuess(ShareChoice.INTERACTION, LogKind.MESSAGE, null)
            } else {
                ShareGuess(ShareChoice.NOTE, LogKind.MESSAGE, null)
            }
    }

    fun isChatLike(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        return TIMESTAMP.containsMatchIn(t) || SPEAKER.containsMatchIn(t) || (t.length <= SHORT_TEXT && t.lines().size <= SHORT_LINES)
    }
}

/** One confirm-step save. [kind] is used only with [ShareChoice.INTERACTION]; [price] only for a gift idea. */
data class SaveShareRequest(
    val target: ShareTarget,
    val content: SharedContent,
    val choice: ShareChoice,
    val kind: LogKind = LogKind.MESSAGE,
    val date: LocalDate? = null,
    val caption: String = "",
    val price: String? = null,
    /** Issue #170: the place, as edited, for the location choices. */
    val place: SharePlace? = null,
)

/** What a save wrote, so it can be shown or undone. */
data class SaveShareResult(
    val attachment: Attachment? = null,
    val log: LogResult? = null,
    val gift: Pair<FriendId, Gift>? = null,
    /** Issue #170: the named place made from a location. */
    val namedPlace: NamedPlace? = null,
    /** Issue #170: the person's addresses as they were before a home or work address was set, for Undo. */
    val addressesBefore: Pair<FriendId, List<PostalAddress>>? = null,
)

/**
 * Saves a share (D6, F12, F17): an [Attachment] on the person or circle; with [ShareChoice.INTERACTION] it is also
 * logged through [LogInteractionUseCase] with `source = SHARE` (a hangout for Met, else one interaction per person),
 * which updates last contact; with [ShareChoice.GIFT_IDEA] it becomes a gift idea instead. A photo keeps only a
 * thumbnail (through [PhotoShrinker]) and its gallery reference; text is capped at [Attachment.MAX_TEXT].
 */
class SaveSharedUseCase(
    private val friends: FriendRepository,
    private val groups: GroupRepository,
    private val interactions: InteractionRepository,
    private val log: LogInteractionUseCase,
    private val shrinker: PhotoShrinker,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val hangouts: HangoutRepository,
) {
    suspend operator fun invoke(request: SaveShareRequest): SaveShareResult {
        val content = request.content
        require(!content.isEmpty) { "Nothing to save" }
        val people: List<FriendId> = when (val t = request.target) {
            is ShareTarget.Person -> listOfNotNull(friends.get(t.id)?.id)
            is ShareTarget.Circle -> groups.groups.first().firstOrNull { it.id == t.id }?.memberIds.orEmpty()
        }
        require(people.isNotEmpty()) { "Nobody to save it for" }
        val caption = request.caption.trim()
        require(request.choice != ShareChoice.PLAN_HERE) { "A plan is saved from the plan editor" }

        if (request.choice == ShareChoice.HOME_ADDRESS || request.choice == ShareChoice.WORK_ADDRESS) {
            val target = request.target as? ShareTarget.Person ?: error("Addresses are for one person")
            val friend = friends.get(target.id) ?: error("No such friend")
            val text = request.place?.addressText ?: error("No address")
            val kind = if (request.choice == ShareChoice.HOME_ADDRESS) AddressKind.HOME else AddressKind.WORK
            friends.upsert(friend.copy(addresses = PostalAddresses.edit(friend.addresses, mapOf(kind to text))))
            return SaveShareResult(addressesBefore = friend.id to friend.addresses)
        }

        if (request.choice == ShareChoice.NAMED_PLACE) {
            val place = request.place ?: error("No place")
            val point = place.point ?: error("A named place needs coordinates")
            val name = place.label ?: error("A named place needs a name")
            val named = NamedPlace(ids.next(), name, point.latitude, point.longitude)
            hangouts.upsertPlace(named)
            return SaveShareResult(namedPlace = named)
        }

        if (request.choice == ShareChoice.GIFT_IDEA) {
            val target = request.target as? ShareTarget.Person ?: error("Gift ideas are for one person")
            val friend = friends.get(target.id) ?: error("No such friend")
            val title = caption.ifEmpty { content.title?.trim().orEmpty() }.ifEmpty { content.draft() }
            val gift = Gift(
                id = ids.next(),
                title = title,
                url = content.url?.trim()?.takeIf { it.isNotEmpty() },
                price = request.price?.trim()?.takeIf { it.isNotEmpty() },
            )
            friends.upsert(friend.copy(gifts = friend.gifts + gift))
            return SaveShareResult(gift = friend.id to gift)
        }

        val metHere = request.choice == ShareChoice.MET_HERE
        val counts = request.choice == ShareChoice.INTERACTION || metHere
        val kindLogged = if (metHere) LogKind.MEETUP else request.kind
        // Issue #170: like the Log popup, a spot inside a named place takes that place's name, so CheckInUseCase links
        // the meetup to it; any other name at a spot is a new named place, as a check-in makes one.
        val point = request.place?.point.takeIf { metHere }
        val placeName = point?.let { NamedPlaces.nearest(hangouts.places.first(), it)?.place?.name } ?: request.place?.label
        val logResult = if (counts) {
            log(
                LogRequest(
                    people,
                    kindLogged,
                    request.date,
                    caption,
                    placeName = placeName.takeIf { metHere },
                    location = point,
                    source = InteractionSource.SHARE,
                ),
            )
        } else {
            null
        }
        val at = when (logResult) {
            is LogResult.Met -> logResult.hangout.at
            is LogResult.Contacts -> logResult.interactions.first().at
            else -> instantFor(request.date)
        }
        val logId = when (logResult) {
            is LogResult.Met -> logResult.hangout.id
            is LogResult.Contacts -> logResult.interactions.first().let { it.batchId ?: it.id }
            else -> null
        }
        val kind = content.kind
        val attachment = Attachment(
            id = ids.next(),
            friendId = (request.target as? ShareTarget.Person)?.id,
            circleId = (request.target as? ShareTarget.Circle)?.id,
            kind = kind,
            text = content.text?.trim()?.takeIf { it.isNotEmpty() && kind != AttachmentKind.LINK }?.take(Attachment.MAX_TEXT),
            url = content.url?.trim()?.takeIf { it.isNotEmpty() },
            title = content.title?.trim()?.takeIf { it.isNotEmpty() },
            photoRef = content.photoRef,
            thumbnail = content.image?.let { shrinker.photoOf(it.bytes) },
            caption = caption,
            at = at,
            countsAsInteraction = counts,
            interactionType = if (counts) kindLogged.interactionType else null,
            logId = logId,
            createdAt = clock.now(),
        )
        interactions.upsertAttachment(attachment)
        return SaveShareResult(attachment = attachment, log = logResult)
    }

    private fun instantFor(date: LocalDate?): Instant {
        val today = clock.today()
        return if (date == null || date >= today) clock.now() else date.atTime(NOON).toInstant(clock.timeZone)
    }

    private companion object {
        val NOON = LocalTime(12, 0)
    }
}

/** Puts back what a share save wrote (snackbar "Undo"). */
class UndoSaveShareUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val undoLog: UndoLogUseCase,
    private val hangouts: HangoutRepository,
) {
    suspend operator fun invoke(result: SaveShareResult) {
        result.attachment?.let { interactions.deleteAttachment(it.id) }
        result.log?.let { undoLog(it) }
        result.gift?.let { (id, gift) ->
            friends.get(id)?.let { f -> friends.upsert(f.copy(gifts = f.gifts.filterNot { it.id == gift.id })) }
        }
        result.addressesBefore?.let { (id, before) -> friends.get(id)?.let { f -> friends.upsert(f.copy(addresses = before)) } }
        result.namedPlace?.let { hangouts.deletePlace(it.id) }
    }
}

/** A friend's attachments, their own and their circles' (F12), newest first. */
class ObserveAttachmentsUseCase(
    private val interactions: InteractionRepository,
    private val groups: GroupRepository,
) {
    operator fun invoke(id: FriendId): Flow<List<Attachment>> = combine(interactions.attachments, groups.groups) { all, circles ->
        forFriend(all, circles, id)
    }

    companion object {
        fun forFriend(all: List<Attachment>, circles: List<FriendGroup>, id: FriendId): List<Attachment> =
            all.filter { it.isFor(id, circles) }.sortedWith(compareByDescending<Attachment> { it.at }.thenByDescending { it.createdAt })
    }
}

/**
 * Removes an attachment from the page (with Undo through [RestoreAttachmentUseCase]). What it logged stays on the
 * timeline, where it can be removed on its own.
 */
class DeleteAttachmentUseCase(private val interactions: InteractionRepository) {
    suspend operator fun invoke(attachment: Attachment) = interactions.deleteAttachment(attachment.id)
}

class RestoreAttachmentUseCase(private val interactions: InteractionRepository) {
    suspend operator fun invoke(attachment: Attachment) = interactions.upsertAttachment(attachment)
}

/** Adds a "What's new" line to a friend. Blank text is ignored. */
class AddLifeUpdateUseCase(private val friends: FriendRepository, private val clock: AppClock, private val ids: IdGenerator) {
    suspend operator fun invoke(id: FriendId, text: String): Boolean {
        val clean = text.trim().takeIf { it.isNotEmpty() } ?: return false
        val friend = friends.get(id) ?: return false
        friends.upsert(friend.copy(lifeUpdates = friend.lifeUpdates + LifeUpdate(ids.next(), clean, clock.now())))
        return true
    }
}

/** AI: turns a shared post / screenshot into a one-line caption about the friend. Needs the user's key. */
class SuggestUpdateFromSharedUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val hangouts: HangoutRepository,
    private val settings: SettingsRepository,
    private val assistant: AiAssistant,
    private val language: LanguageProvider,
    private val links: PersonLinkRepository = PersonLinkRepository.None,
) {
    /** The suggested line, or null when the AI found nothing about them in what was shared. */
    suspend operator fun invoke(id: FriendId, content: SharedContent): Result<String?> {
        val key = settings.settings.first().usableAiKey ?: return Result.failure(MissingApiKeyException())
        val friend = friends.get(id) ?: return Result.failure(NoSuchElementException("friend"))
        val text = listOfNotNull(
            content.title?.trim()?.takeIf { it.isNotEmpty() },
            content.text?.trim()?.takeIf { it.isNotEmpty() },
            content.url?.trim()?.takeIf { it.isNotEmpty() },
        )
            .distinct()
            .joinToString("\n")
            .take(MAX_TEXT)
            .ifEmpty { null }
        return assistant.lifeUpdateFromShared(
            key,
            IdeaMapper.profile(friend, interactions, hangouts, links, friends),
            text,
            content.image,
            language.languageTag(),
        )
            .map { it?.trim()?.takeIf(String::isNotEmpty) }
    }

    private companion object {
        const val MAX_TEXT = 4_000
    }
}
