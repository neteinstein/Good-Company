package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant

@Serializable
@JvmInline
value class FriendId(val value: String)

/**
 * A person in your circle. Seeded from the device address book ([contactId] set) or created
 * manually in the app ([contactId] null — the only option on the web).
 *
 * Contact-owned fields (name, phones, birthday, photo) are refreshed on every contacts sync; everything
 * else is app-owned and never overwritten by a sync. A name or photo set in the app ([customName], [customPhoto], F19
 * and F14) wins over the contact's: show [displayName] and [avatar], never [name] or [photo] directly.
 */
@Serializable
data class Friend(
    val id: FriendId,
    val name: String,
    val contactId: String? = null,
    val phoneNumbers: List<String> = emptyList(),
    val birthday: Birthday? = null,
    /** "Real friend" — promoted out of the address-book noise. */
    val isCloseFriend: Boolean = false,
    /** On by default (brief decision F10); it can be turned off for one person. */
    val notifyBirthday: Boolean = true,
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val notes: String = "",
    /**
     * Only read from old backups and the old JSON store: kids are people now (F9, issue #40), linked to their parent
     * as [LinkType.CHILD]. `KidsAsPeople.convert` turns these into people; nothing stores them any more.
     */
    val kids: List<Kid> = emptyList(),
    val importantEvents: List<ImportantEvent> = emptyList(),
    /** "If I don't meet them in this many days, remind me." Null = no rule. */
    val meetEveryDays: Int? = null,
    /** Opt-in for the weekly AI event suggestions. */
    val suggestEvents: Boolean = false,
    /** Contact disappeared from the address book; kept because it has app data. */
    val isOrphaned: Boolean = false,
    /**
     * How close a real friend is (Inner / Close / Good friends) — gives a default "meet every" cadence without
     * per-friend setup. Stored as "circle" (its name before decision D1), so backups and the database are unchanged.
     */
    @SerialName("circle")
    val closeness: Closeness? = null,
    /** Catch-up nudges are paused until this date (inclusive). */
    val snoozedUntil: LocalDate? = null,
    /** Gift ideas (not yet given) and gift history (given). A kid's gifts are on the kid's own page. */
    val gifts: List<Gift> = emptyList(),
    /** Catch-up nudges paused with no end date ("on hold"). History and birthdays are kept. */
    val catchUpPaused: Boolean = false,
    /** Calls and chats also count as catching up (for friends who live far away), not only meetups. */
    val contactCountsAsCatchUp: Boolean = false,
    /** What they're good company for ("weekday lunch", "a hike"…), used by "Who's free for…?". */
    val occasions: List<Occasion> = emptyList(),
    /** What's new in their life (new job, a move, a new hobby…), newest last. Leads their catch-up story. */
    val lifeUpdates: List<LifeUpdate> = emptyList(),
    /** When each of [likes] was added (likes added before this existed have no entry). */
    val likesAddedAt: Map<String, Instant> = emptyMap(),
    /** Blogs / Substack / Letterboxd feeds you follow for them (RSS or Atom), with their latest posts. */
    val feeds: List<FriendFeed> = emptyList(),
    /** Their contact photo (a thumbnail). A contact without one never clears it. */
    val photo: ContactPhoto? = null,
    /** What they do (free text). */
    val job: String = "",
    /** Where they live (free text, not a map location). */
    val livesIn: String = "",
    /** How and where you met. */
    val met: MetInfo? = null,
    /** Who introduced you: one of your friends or just a name. */
    val introducedBy: Introducer? = null,
    /** How they like to be reached: that quick action comes first and message sheets open on it. */
    val preferredChannel: ContactChannel? = null,
    /** Email addresses: [FriendEmail.fromContact] ones follow the address book, typed ones are kept. Never written back. */
    val emails: List<FriendEmail> = emptyList(),
    /** Partner, parents, siblings, pets… named inside this friend. Kids are people, linked as [LinkType.CHILD]. */
    val relatives: List<Relative> = emptyList(),
    /** Tastes by kind. [likes] holds "Other likes" and [dislikes] "Doesn't like". */
    val food: List<String> = emptyList(),
    val drinks: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    /** When each entry of [food], [drinks] and [interests] was added (like [likesAddedAt]). */
    val foodAddedAt: Map<String, Instant> = emptyMap(),
    val drinksAddedAt: Map<String, Instant> = emptyMap(),
    val interestsAddedAt: Map<String, Instant> = emptyMap(),
    /** "More about <name>": free label/value pairs ("Shoe size" → "38"). */
    val customFields: List<CustomField> = emptyList(),
    /** What they are to you ("your sister", F6), separate from links between people. A family one puts them in Family. */
    val relationToMe: LinkType? = null,
    /** Which words describe them ("sister" / "brother" / "sibling"); null = the neutral word (decision F18). */
    val pronouns: Pronouns? = null,
    /** F19: the name given in the app. Wins over [name] (the contact's) and is never touched by a contacts sync. */
    val customName: String? = null,
    /** F14: a photo set in the app (cropped thumbnail). Wins over [photo] (the contact's), which a sync keeps updating. */
    val customPhoto: ContactPhoto? = null,
    /**
     * Shown in the main A–Z list (F9). On for everyone added as a friend; for a kid it starts on when they or their
     * parent are family, and off for friends' kids, who still show on their parent's page and in birthdays.
     */
    val showInMyList: Boolean = true,
    /**
     * Kinship suggestions answered "Not right" / "Not family" (F7): the relations offered then, never proposed again for
     * this person (see [com.neteinstein.goodcompany.core.domain.usecase.KinshipResolver]).
     */
    val dismissedKinship: Set<LinkType> = emptySet(),
) {
    /** In the built-in Family circle (F6): their [relationToMe] is a family one. */
    val isFamily: Boolean get() = relationToMe?.isFamily == true

    /** The name shown everywhere: [customName] when set, else the contact's [name]. */
    val displayName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: name

    /** The photo shown everywhere: [customPhoto] when set, else the contact's [photo]. */
    val avatar: ContactPhoto?
        get() = customPhoto ?: photo

    val initials: String
        get() = displayName.split(' ', '-')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

/** A kid as the app stored them before kids became people (F9). Kept only to read old backups. */
@Serializable
data class Kid(
    val id: String,
    val name: String,
    val birthday: Birthday? = null,
    val likes: List<String> = emptyList(),
    /** When the kid was added to the app (null for older data). */
    val addedAt: Instant? = null,
)

/** Something new in a friend's life ("Started a new job", "Moved to Porto") worth asking about next time. */
@Serializable
data class LifeUpdate(
    val id: String,
    val text: String,
    val addedAt: Instant,
)

/** A friend's blog or feed (RSS/Atom) and the few latest posts we fetched from it. */
@Serializable
data class FriendFeed(
    /** The feed's own URL (not the page it was discovered on). */
    val url: String,
    val title: String = "",
    val siteUrl: String? = null,
    val checkedAt: Instant? = null,
    /** Newest first, at most a handful. */
    val items: List<FeedItem> = emptyList(),
)

@Serializable
data class FeedItem(
    val id: String,
    val title: String,
    val link: String? = null,
    val publishedAt: Instant? = null,
)

/** Something big in a friend's life (surgery, exam, interview…) worth a ping. */
@Serializable
data class ImportantEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    /** Also remind this many days before (0 = only on the day). */
    val remindDaysBefore: Int = 0,
    /** When it was added to the app (null for older data). */
    val addedAt: Instant? = null,
)

/**
 * Dunbar-style tiers for real friends. Each has a default cadence (configurable in Settings) so reminders work
 * the moment someone is marked as a real friend. A per-friend "meet every" value still overrides it.
 */
enum class Closeness { INNER, CLOSE, GOOD }

/**
 * A gift idea ([givenOn] null) or a gift already given. [forKidId] is only read from old backups (a gift for one of
 * the friend's kids); `KidsAsPeople.convert` moves such a gift to the kid's own page.
 */
@Serializable
data class Gift(
    val id: String,
    val title: String,
    val note: String = "",
    val forKidId: String? = null,
    val givenOn: LocalDate? = null,
    val occasion: GiftOccasion? = null,
    /** F17: the shop link a gift idea was shared from (never fetched). */
    val url: String? = null,
    /** F17: an optional price the user typed, as written ("€35"). */
    val price: String? = null,
) {
    val isIdea: Boolean get() = givenOn == null
}

enum class GiftOccasion { BIRTHDAY, CHRISTMAS, THANK_YOU, JUST_BECAUSE }

/** Kinds of plans a friend is good company for. Rendered by `:core:ui`. */
enum class Occasion { WEEKDAY_LUNCH, COFFEE, DINNER, DRINKS, OUTDOORS, SPORTS, CULTURE, FAMILY_TIME }

/**
 * How you met. [on] is null when you don't remember; with [dayKnown] false only its month and year count
 * (shown as "Sep 2011") and its day is 1.
 */
@Serializable
data class MetInfo(
    val place: String = "",
    val on: LocalDate? = null,
    val dayKnown: Boolean = true,
) {
    val isEmpty: Boolean get() = place.isBlank() && on == null
}

/** Who introduced you: [friendId] when it's one of your friends (the page links to them), always a [name]. */
@Serializable
data class Introducer(
    val friendId: FriendId? = null,
    val name: String,
)

/** The ways the quick actions can reach someone. */
enum class ContactChannel { WHATSAPP, CALL, SMS, EMAIL }

/** An email address; [fromContact] ones are refreshed by a contacts sync, typed ones are never touched by it. */
@Serializable
data class FriendEmail(
    val address: String,
    val fromContact: Boolean = false,
)

/** Who a [Relative] is to the friend. Kids are people of their own, not relatives. */
enum class Relation { PARTNER, PARENT, SIBLING, PET, OTHER }

/** Someone in a friend's family other than their kids (a pet counts). */
@Serializable
data class Relative(
    val id: String,
    val relation: Relation,
    val name: String,
    val birthday: Birthday? = null,
    val likes: List<String> = emptyList(),
    /** A few words ("beagle", "my sister-in-law"). */
    val note: String = "",
    /** On by default (brief decision F10); it can be turned off for one relative. */
    val notifyBirthday: Boolean = true,
    val addedAt: Instant? = null,
)

/** The taste groups beyond "Other likes" ([Friend.likes]) and "Doesn't like" ([Friend.dislikes]). */
enum class TasteKind { FOOD, DRINK, INTEREST }

/** A free "More about <name>" line. */
@Serializable
data class CustomField(
    val id: String,
    val label: String,
    val value: String,
)
