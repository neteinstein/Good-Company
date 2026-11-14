package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendId

/**
 * Merges an address-book snapshot into the app's friend list.
 *
 * - Contact-owned fields (name, phones, birthday) follow the address book. Emails the contact gave us
 *   ([FriendEmail.fromContact]) are replaced by the contact's; emails typed in the app are always kept.
 * - App-owned fields (close friend, likes, kids, rules…) are never touched.
 * - A contact birthday never erases one the user entered in-app.
 * - Friends whose contact vanished are dropped unless they carry app data — then they're kept and flagged orphaned.
 * - Manual friends (no contactId) are left alone.
 * - With `addNew = false`, contacts that aren't friends yet are ignored (people are added one by one with the picker).
 */
object ContactMerger {
    fun merge(existing: List<Friend>, contacts: List<ContactRecord>, addNew: Boolean = true): List<Friend> {
        val byContactId = existing.filter { it.contactId != null }.associateBy { it.contactId!! }
        val seen = HashSet<String>()
        val merged = contacts.mapNotNull { contact ->
            if (!seen.add(contact.contactId) || contact.name.isBlank()) return@mapNotNull null
            val current = byContactId[contact.contactId]
            if (current == null && !addNew) return@mapNotNull null
            current?.copy(
                name = contact.name.trim(),
                phoneNumbers = contact.phoneNumbers.distinct(),
                birthday = contact.birthday ?: current.birthday,
                photo = contact.photo ?: current.photo,
                emails = mergeEmails(current.emails, contact.emails),
                isOrphaned = false,
            ) ?: Friend(
                id = FriendId("contact-${contact.contactId}"),
                contactId = contact.contactId,
                name = contact.name.trim(),
                phoneNumbers = contact.phoneNumbers.distinct(),
                birthday = contact.birthday,
                photo = contact.photo,
                emails = mergeEmails(emptyList(), contact.emails),
            )
        }
        val vanished = existing.filter { it.contactId != null && it.contactId !in seen && it.hasAppData() }
            .map { it.copy(isOrphaned = true) }
        val manual = existing.filter { it.contactId == null }
        return merged + vanished + manual
    }

    /**
     * The contact's emails (as [FriendEmail.fromContact]) followed by the typed ones, without duplicates
     * (case-insensitive). A typed address the contact now also has becomes a contact one.
     */
    fun mergeEmails(current: List<FriendEmail>, fromContact: List<String>): List<FriendEmail> {
        val contact = fromContact.map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase)
        val keys = contact.mapTo(HashSet(), String::lowercase)
        val typed = current.filter { !it.fromContact && it.address.trim().lowercase() !in keys }
        return contact.map { FriendEmail(it, fromContact = true) } + typed
    }

    /** True when the friend holds anything typed in the app (so an orphaned friend is kept). Contact emails don't count. */
    fun Friend.hasAppData(): Boolean = isCloseFriend || notifyBirthday || likes.isNotEmpty() ||
        dislikes.isNotEmpty() || notes.isNotBlank() || kids.isNotEmpty() || importantEvents.isNotEmpty() ||
        meetEveryDays != null || suggestEvents || circle != null || gifts.isNotEmpty() || catchUpPaused ||
        contactCountsAsCatchUp || occasions.isNotEmpty() || lifeUpdates.isNotEmpty() ||
        feeds.isNotEmpty() || job.isNotBlank() || livesIn.isNotBlank() || met != null || introducedBy != null ||
        preferredChannel != null || emails.any { !it.fromContact } || relatives.isNotEmpty() || food.isNotEmpty() ||
        drinks.isNotEmpty() || interests.isNotEmpty() || customFields.isNotEmpty()
}
