package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId

/**
 * Merges an address-book snapshot into the app's friend list.
 *
 * - Contact-owned fields (name, phones, birthday) follow the address book.
 * - App-owned fields (close friend, likes, kids, rules…) are never touched.
 * - A contact birthday never erases one the user entered in-app.
 * - Friends whose contact vanished are dropped unless they carry app data — then they're kept and flagged orphaned.
 * - Manual friends (no contactId) are left alone.
 */
object ContactMerger {
    fun merge(existing: List<Friend>, contacts: List<ContactRecord>): List<Friend> {
        val byContactId = existing.filter { it.contactId != null }.associateBy { it.contactId!! }
        val seen = HashSet<String>()
        val merged = contacts.mapNotNull { contact ->
            if (!seen.add(contact.contactId) || contact.name.isBlank()) return@mapNotNull null
            val current = byContactId[contact.contactId]
            current?.copy(
                name = contact.name.trim(),
                phoneNumbers = contact.phoneNumbers.distinct(),
                birthday = contact.birthday ?: current.birthday,
                isOrphaned = false,
            ) ?: Friend(
                id = FriendId("contact-${contact.contactId}"),
                contactId = contact.contactId,
                name = contact.name.trim(),
                phoneNumbers = contact.phoneNumbers.distinct(),
                birthday = contact.birthday,
            )
        }
        val vanished = existing.filter { it.contactId != null && it.contactId !in seen && it.hasAppData() }
            .map { it.copy(isOrphaned = true) }
        val manual = existing.filter { it.contactId == null }
        return merged + vanished + manual
    }

    private fun Friend.hasAppData(): Boolean = isCloseFriend || notifyBirthday || likes.isNotEmpty() ||
        dislikes.isNotEmpty() || notes.isNotBlank() || kids.isNotEmpty() || importantEvents.isNotEmpty() ||
        meetEveryDays != null || suggestEvents || circle != null || gifts.isNotEmpty() || catchUpPaused ||
        contactCountsAsCatchUp || occasions.isNotEmpty() || lifeUpdates.isNotEmpty() ||
        feeds.isNotEmpty()
}
