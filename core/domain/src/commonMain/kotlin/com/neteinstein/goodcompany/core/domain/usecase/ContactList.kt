package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import kotlinx.coroutines.flow.first

/** One row of the "Choose friends" list: an address-book contact, and whether they're already a friend. */
data class ContactChoice(val record: ContactRecord, val alreadyFriend: Boolean)

/** The filter chips above the contact list. */
enum class ContactListFilter {
    /** Everyone, favourites in their own section at the top. The default. */
    FAVOURITES_FIRST,

    /** Only contacts with a birthday, favourites first. */
    WITH_BIRTHDAY,

    /** Everyone, plain A–Z (no favourites section). */
    ALL,
}

/**
 * Lists the device address book for the "Choose friends" screen (decision D2): favourites first, then A–Z. Contacts
 * linked to a friend are flagged so the screen shows them ticked and disabled. Reads without photos: thousands of
 * thumbnails are too heavy for a list, so [SaveChosenContactsUseCase] loads them only for the people saved.
 * Changes nothing: only the people the user ticks are ever saved.
 */
class ListContactsUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(): List<ContactChoice> {
        val records = runCatching { friends.readContacts(withPhotos = false) }.getOrDefault(emptyList())
        val linked = friends.friends.first().mapNotNullTo(HashSet()) { it.contactId }
        return records
            .filter { it.name.isNotBlank() }
            .distinctBy { it.contactId }
            .map { ContactChoice(it, alreadyFriend = it.contactId in linked) }
            .sortedWith(ContactList.favouritesFirst)
    }
}

/**
 * Saves the people ticked in the "Choose friends" list. Their thumbnails are read now, for them only, and they are
 * added exactly like people picked in the system contact picker ([ImportPickedContactsUseCase]).
 */
class SaveChosenContactsUseCase(private val friends: FriendRepository, private val importPicked: ImportPickedContactsUseCase) {
    suspend operator fun invoke(records: List<ContactRecord>): PickedContactsResult {
        if (records.isEmpty()) return PickedContactsResult(0, 0)
        val photos = runCatching { friends.contactPhotos(records.mapTo(HashSet()) { it.contactId }) }.getOrDefault(emptyMap())
        return importPicked(records.map { r -> photos[r.contactId]?.let { r.copy(photo = it) } ?: r })
    }
}

/** Ordering, search and filters for the contact list. Pure, so the screen and the tests share it. */
object ContactList {
    private const val MIN_DIGITS = 3

    /** Starred contacts first, then A–Z ignoring case and accents ("Álvaro" sorts with "Alvaro"). */
    val favouritesFirst: Comparator<ContactChoice> =
        compareByDescending<ContactChoice> { it.record.starred }.then(alphabetical)

    /** A–Z ignoring case and accents. */
    val alphabetical: Comparator<ContactChoice>
        get() = compareBy<ContactChoice> { sortKey(it.record.name) }.thenBy { it.record.name }.thenBy { it.record.contactId }

    /** Applies [filter] and [query] (name, ignoring case and accents, or 3+ digits of a phone number). */
    fun filter(choices: List<ContactChoice>, query: String, filter: ContactListFilter): List<ContactChoice> {
        val base = when (filter) {
            ContactListFilter.FAVOURITES_FIRST -> choices.sortedWith(favouritesFirst)
            ContactListFilter.WITH_BIRTHDAY -> choices.filter { it.record.birthday != null }.sortedWith(favouritesFirst)
            ContactListFilter.ALL -> choices.sortedWith(alphabetical)
        }
        val q = query.trim()
        if (q.isEmpty()) return base
        val name = sortKey(q)
        // A number search is digits plus phone punctuation only ("912 34", "+351"), never part of a name.
        val digits = q.filter(Char::isDigit).takeIf { it.length >= MIN_DIGITS && q.none(Char::isLetter) }
        return base.filter { c ->
            sortKey(c.record.name).contains(name) ||
                (digits != null && c.record.phoneNumbers.any { it.filter(Char::isDigit).contains(digits) })
        }
    }

    /** The section a contact goes in: its initial without accents, or "#" for anything that isn't a letter. */
    fun indexLetter(name: String): String {
        val first = sortKey(name).firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercase() else "#"
    }

    /** Lower case without accents, for sorting and searching. */
    fun sortKey(text: String): String = buildString(text.length) {
        text.trim().lowercase().forEach { append(FOLD[it] ?: it.toString()) }
    }

    private val FOLD: Map<Char, String> = buildMap {
        fun add(base: String, accented: String) = accented.forEach { put(it, base) }
        add("a", "àáâãäåāăąǎ")
        add("ae", "æ")
        add("c", "çćĉċč")
        add("d", "ďđ")
        add("e", "èéêëēĕėęě")
        add("g", "ĝğġģ")
        add("h", "ĥħ")
        add("i", "ìíîïĩīĭįı")
        add("j", "ĵ")
        add("k", "ķ")
        add("l", "ĺļľŀł")
        add("n", "ñńņňŉ")
        add("o", "òóôõöøōŏőǒ")
        add("oe", "œ")
        add("r", "ŕŗř")
        add("s", "śŝşšș")
        add("ss", "ß")
        add("t", "ţťŧț")
        add("u", "ùúûüũūŭůűųǔ")
        add("w", "ŵ")
        add("y", "ýÿŷ")
        add("z", "źżž")
    }
}
