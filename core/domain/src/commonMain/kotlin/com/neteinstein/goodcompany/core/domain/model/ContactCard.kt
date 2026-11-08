package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate

/**
 * The part of a [Friend] that an address-book entry can also hold: a snapshot used to mirror app data onto the
 * contact (Android sideload build only), so you can open the contact and see birthday, kids, events and notes.
 *
 * Deliberately smaller than [Friend]: fields the address book can't represent (reminders, cadence, gifts, feeds…)
 * stay app-only, and details with no slot on a contact (a kid's likes, an event's reminder lead time) are left out.
 * Being a plain value type, two cards are equal exactly when the contact would look the same, which is what lets
 * callers skip redundant writes.
 */
data class ContactCard(
    val name: String,
    val birthday: Birthday? = null,
    val kids: List<CardKid> = emptyList(),
    val events: List<CardEvent> = emptyList(),
    val notes: String = "",
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
) {
    /** True when only a name remains (nothing worth putting on the contact). */
    val isEmpty: Boolean
        get() = birthday == null && !hasExtras

    /** Anything beyond name and birthday: those two normally come *from* the contact, not the other way round. */
    val hasExtras: Boolean
        get() = kids.isNotEmpty() || events.isNotEmpty() || notes.isNotBlank() || likes.isNotEmpty() || dislikes.isNotEmpty()

    /**
     * The contact's note: free-form notes first, then tastes. Labels come from the caller because the domain never
     * formats user-facing text. Null when there is nothing to say.
     */
    fun noteText(likesLabel: String, dislikesLabel: String): String? = listOfNotNull(
        notes.trim().takeIf { it.isNotEmpty() },
        likes.takeIf { it.isNotEmpty() }?.let { "$likesLabel: ${it.joinToString(", ")}" },
        dislikes.takeIf { it.isNotEmpty() }?.let { "$dislikesLabel: ${it.joinToString(", ")}" },
    ).takeIf { it.isNotEmpty() }?.joinToString("\n")

    companion object {
        fun from(friend: Friend): ContactCard = ContactCard(
            name = friend.name,
            birthday = friend.birthday,
            kids = friend.kids.mapNotNull { kid -> kid.name.trim().takeIf { it.isNotEmpty() }?.let { CardKid(it, kid.birthday) } },
            events = friend.importantEvents.mapNotNull { e -> e.title.trim().takeIf { it.isNotEmpty() }?.let { CardEvent(it, e.date) } },
            notes = friend.notes,
            likes = friend.likes.map(String::trim).filter(String::isNotEmpty),
            dislikes = friend.dislikes.map(String::trim).filter(String::isNotEmpty),
        )
    }
}

/** A child, as the contact can show them: a name and (optionally) a birthday. */
data class CardKid(val name: String, val birthday: Birthday? = null)

/** A dated event, as the contact can show it. */
data class CardEvent(val title: String, val date: LocalDate)
