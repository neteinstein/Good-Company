package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The three kinds of [LinkType]: family, romantic and the rest (friend, colleague). */
enum class LinkCategory { FAMILY, ROMANTIC, OTHER }

/**
 * How one person is related to another (issue #39, F6, F18). Every type reads from one side: in a [PersonLink] it is
 * what [PersonLink.to] is **to** [PersonLink.from] ("Ana is Rui's [DATING]"), and [inverse] is the word from the other
 * side ("Rui is Ana's [DATING]"; "Leonor is Rui's [CHILD]" ↔ "Rui is Leonor's [PARENT]").
 *
 * The types are gender-neutral: the word shown ("girlfriend" / "boyfriend") comes from the described person's
 * [Pronouns], with a neutral word when they have none (decision F18 in the product brief). Also used for
 * [Friend.relationToMe], where it says what the friend is to *you*.
 */
enum class LinkType(val category: LinkCategory) {
    PARENT(LinkCategory.FAMILY),

    /** A parent's partner who isn't your parent (F7). */
    STEP_PARENT(LinkCategory.FAMILY),
    CHILD(LinkCategory.FAMILY),

    /** A partner's child who isn't yours (F7). */
    STEP_CHILD(LinkCategory.FAMILY),
    SIBLING(LinkCategory.FAMILY),

    /** A sibling through one shared parent (F7). */
    HALF_SIBLING(LinkCategory.FAMILY),

    /** A step-parent's child (F7). */
    STEP_SIBLING(LinkCategory.FAMILY),
    GRANDPARENT(LinkCategory.FAMILY),
    GRANDCHILD(LinkCategory.FAMILY),

    /** An aunt or uncle. */
    AUNT_UNCLE(LinkCategory.FAMILY),

    /** A niece or nephew. */
    NIECE_NEPHEW(LinkCategory.FAMILY),
    COUSIN(LinkCategory.FAMILY),
    IN_LAW(LinkCategory.FAMILY),

    /** Boyfriend / girlfriend. */
    DATING(LinkCategory.ROMANTIC),

    /** Husband / wife. */
    SPOUSE(LinkCategory.ROMANTIC),
    PARTNER(LinkCategory.ROMANTIC),

    /** A romantic link that ended (it has [PersonLink.endedOn]). */
    EX(LinkCategory.ROMANTIC),
    FRIEND(LinkCategory.OTHER),
    COLLEAGUE(LinkCategory.OTHER),
    ;

    val isFamily: Boolean get() = category == LinkCategory.FAMILY
    val isRomantic: Boolean get() = category == LinkCategory.ROMANTIC

    /** The same link seen from the other person. Symmetric types are their own inverse. */
    val inverse: LinkType
        get() = when (this) {
            PARENT -> CHILD
            CHILD -> PARENT
            STEP_PARENT -> STEP_CHILD
            STEP_CHILD -> STEP_PARENT
            GRANDPARENT -> GRANDCHILD
            GRANDCHILD -> GRANDPARENT
            AUNT_UNCLE -> NIECE_NEPHEW
            NIECE_NEPHEW -> AUNT_UNCLE
            else -> this
        }

    /** Romantic links can carry a start date (an anniversary) and, for an [EX], an end date. */
    val hasDates: Boolean get() = isRomantic

    companion object {
        /** The stored name, or null for anything unknown (a newer app's type, a hand-edited file). */
        fun parse(name: String?): LinkType? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Which words to use for someone ("sister" / "brother" / "sibling"). Optional: a person without pronouns gets the
 * neutral word, which is never wrong. Set from the link sheets (decision F18).
 */
enum class Pronouns {
    SHE,
    HE,
    THEY,
    ;

    companion object {
        fun parse(name: String?): Pronouns? = entries.firstOrNull { it.name == name }
    }
}

/**
 * A link between two people (issue #39), stored **once** and shown on both pages. [type] is what [to] is to [from]
 * ("Ana is Rui's girlfriend": from = Rui, to = Ana, type = [LinkType.DATING]).
 *
 * Several romantic links may be on file at once. [startedOn] (romantic links only) is when it began, and with
 * [remindAnniversary] it becomes a yearly reminder, like a birthday. An [LinkType.EX] has [endedOn].
 */
@Serializable
data class PersonLink(
    val id: String,
    val from: FriendId,
    val to: FriendId,
    val type: LinkType,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
    val note: String = "",
    val remindAnniversary: Boolean = false,
    val addedAt: Instant? = null,
) {
    /** True when the link touches [id] (on either side). */
    fun involves(id: FriendId): Boolean = from == id || to == id

    /** The other person, seen from [id]; null when the link isn't about [id]. */
    fun otherThan(id: FriendId): FriendId? = when (id) {
        from -> to
        to -> from
        else -> null
    }

    /** What the other person is to [viewer] ("Ana's page: Rui is her boyfriend"); null when the link isn't about [viewer]. */
    fun roleSeenBy(viewer: FriendId): LinkType? = when (viewer) {
        from -> type
        to -> type.inverse
        else -> null
    }

    /** True for a romantic link that is still going (an ex isn't). */
    val isCurrentRomance: Boolean get() = type.isRomantic && type != LinkType.EX && endedOn == null

    /** The next anniversary on or after [today] (Feb 29 → Feb 28 in other years), or null without a start date. */
    fun nextAnniversary(today: LocalDate): LocalDate? {
        val start = startedOn ?: return null
        val month = start.month.ordinal + 1
        fun on(year: Int) = LocalDate(year, Month(month), minOf(start.day, Birthday.daysInMonth(year, month)))
        val thisYear = on(today.year)
        return if (thisYear >= today) thisYear else on(today.year + 1)
    }

    /** How many years together on [on] (0 on the day it began). */
    fun yearsOn(on: LocalDate): Int? = startedOn?.let { on.year - it.year }
}

/** Someone linked to the friend whose page you're on, with what they are to that friend. */
data class LinkedPerson(
    val link: PersonLink,
    val person: Friend,
    /** What [person] is to the page's friend (a [PersonLink] seen from that side). */
    val role: LinkType,
)
