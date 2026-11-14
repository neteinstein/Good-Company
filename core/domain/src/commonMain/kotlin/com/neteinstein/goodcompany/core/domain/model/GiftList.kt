package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A gift list for one occasion ("Christmas 2026", a Secret Santa) (F8, issue #42). Each [GiftListEntry] is a person on
 * the list with an optional budget and the gifts planned for them. The gifts themselves are the person's own [Gift]s
 * (ideas until given), so a gift marked given here shows in their gift history.
 */
@Serializable
data class GiftList(
    val id: String,
    val title: String,
    val occasionDate: LocalDate? = null,
    val createdAt: Instant,
    /** What a gift given from this list is recorded as ([Gift.occasion]); also picks the list's icon. */
    val occasion: GiftOccasion? = null,
    val entries: List<GiftListEntry> = emptyList(),
) {
    fun entryFor(friendId: FriendId): GiftListEntry? = entries.firstOrNull { it.friendId == friendId }

    /** Replaces (or adds) [friendId]'s entry. */
    fun withEntry(friendId: FriendId, transform: (GiftListEntry) -> GiftListEntry): GiftList {
        val current = entryFor(friendId) ?: GiftListEntry(friendId)
        val next = transform(current)
        return copy(
            entries = if (entries.any { it.friendId == friendId }) {
                entries.map {
                    if (it.friendId ==
                        friendId
                    ) {
                        next
                    } else {
                        it
                    }
                }
            } else {
                entries + next
            },
        )
    }
}

/** A person on a [GiftList]. [budget] is in the same (unnamed) currency as the gift prices, null when none was set. */
@Serializable
data class GiftListEntry(
    val friendId: FriendId,
    val budget: Double? = null,
    val items: List<GiftListItem> = emptyList(),
)

/** One of the person's [Gift]s (by [giftId]) on the list, and how far along it is. */
@Serializable
data class GiftListItem(val giftId: String, val status: GiftStatus = GiftStatus.TO_BUY)

/** To buy → bought → given. Tapping the chip moves it along; after given it starts again. */
@Serializable
enum class GiftStatus {
    TO_BUY,
    BOUGHT,
    GIVEN,
    ;

    val next: GiftStatus get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): GiftStatus? = entries.firstOrNull { it.name == name }
    }
}
