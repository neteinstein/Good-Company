package com.neteinstein.goodcompany.core.data.repository

import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import kotlinx.coroutines.CancellationException

/**
 * Mirrors friends onto their contacts through [writer] (sideload build only), shared by the friend and link
 * repositories: a friend's card includes their links (issue #39), so a link change rewrites both people's cards and a
 * rename rewrites the cards that show the name. A card is written only when it changed, and mirroring is best effort:
 * a failing address book never blocks saving.
 */
internal class ContactMirror(private val writer: ContactsWriter) {
    /** Everything a card is built from. */
    data class Snapshot(val friends: Map<FriendId, Friend>, val links: List<PersonLink>) {
        fun card(friend: Friend): ContactCard = ContactCard.from(friend, links, friends)
    }

    /** Writes the cards of [ids] that differ between [before] and [after]. */
    suspend fun sync(ids: Set<FriendId>, before: Snapshot, after: Snapshot) {
        ids.forEach { id ->
            val friend = after.friends[id] ?: return@forEach
            val contactId = friend.contactId ?: return@forEach
            val card = after.card(friend)
            val previous = before.friends[id]?.takeIf { it.contactId == contactId }?.let(before::card)
            if (card != previous && !card.isEmpty) safely { writer.write(contactId, id, card) }
        }
    }

    /** Details saved before the contact was writable still need to reach it (after a contacts sync). */
    suspend fun backfill(friends: List<Friend>, links: List<PersonLink>) {
        val snapshot = Snapshot(friends.associateBy { it.id }, links)
        friends.forEach { friend ->
            val contactId = friend.contactId ?: return@forEach
            val card = snapshot.card(friend)
            if (!friend.isOrphaned && card.hasExtras) safely { writer.write(contactId, friend.id, card) }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Mirroring is best effort: the app's own copy is already saved.
        }
    }
}
