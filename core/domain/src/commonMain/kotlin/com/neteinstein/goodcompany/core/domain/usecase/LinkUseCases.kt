package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

// Links between people, relationship to me and romantic relationships (issue #39, F6, F18).

/** Pure rules for links: who is linked to whom, in which order, and what a draft may hold. */
object PersonLinks {
    /**
     * The people linked to [id], each with what they are to [id]: current partners first (F18), then exes, then
     * family (closest first), then everyone else; A–Z inside each.
     */
    fun of(id: FriendId, links: List<PersonLink>, friends: List<Friend>): List<LinkedPerson> {
        val byId = friends.associateBy { it.id }
        return links.mapNotNull { link ->
            val other = link.otherThan(id)?.let(byId::get) ?: return@mapNotNull null
            val role = link.roleSeenBy(id) ?: return@mapNotNull null
            LinkedPerson(link, other, role)
        }.sortedWith(compareBy<LinkedPerson> { rank(it) }.thenBy { it.role.ordinal }.thenBy { it.person.displayName.lowercase() })
    }

    private fun rank(p: LinkedPerson): Int = when {
        p.link.isCurrentRomance -> 0
        p.role.isRomantic -> 1
        p.role.isFamily -> 2
        else -> 3
    }

    /** The link between [a] and [b], whichever way round it was stored. */
    fun between(a: FriendId, b: FriendId, links: List<PersonLink>): PersonLink? =
        links.firstOrNull { (it.from == a && it.to == b) || (it.from == b && it.to == a) }

    /**
     * [link] cleaned up: dates only on romantic links, an end date only on an ex, the anniversary reminder only for a
     * current romance with a start date, and a trimmed note.
     */
    fun normalized(link: PersonLink): PersonLink {
        val startedOn = link.startedOn?.takeIf { link.type.hasDates }
        val endedOn = link.endedOn?.takeIf { link.type == LinkType.EX }
        val clean = link.copy(startedOn = startedOn, endedOn = endedOn, note = link.note.trim())
        return clean.copy(remindAnniversary = link.remindAnniversary && startedOn != null && clean.isCurrentRomance)
    }

    /** Anniversaries to remember: current romances with a start date and the reminder on, between two known people. */
    fun anniversaries(links: List<PersonLink>, friends: List<Friend>): List<Triple<PersonLink, Friend, Friend>> {
        val byId = friends.associateBy { it.id }
        return links.filter { it.remindAnniversary && it.startedOn != null && it.isCurrentRomance }.mapNotNull { link ->
            val a = byId[link.from] ?: return@mapNotNull null
            val b = byId[link.to] ?: return@mapNotNull null
            Triple(link, a, b)
        }
    }

    /** The link type a [Relation] becomes when the relative is added as a person (question 1); null for pets and "other". */
    fun typeFor(relation: Relation): LinkType? = when (relation) {
        Relation.PARTNER -> LinkType.PARTNER
        Relation.PARENT -> LinkType.PARENT
        Relation.SIBLING -> LinkType.SIBLING
        Relation.PET, Relation.OTHER -> null
    }
}

/** The people linked to one friend, in page order (see [PersonLinks.of]). */
class ObservePersonLinksUseCase(private val links: PersonLinkRepository, private val friends: FriendRepository) {
    operator fun invoke(id: FriendId): Flow<List<LinkedPerson>> =
        combine(links.links, friends.friends) { ls, fs -> PersonLinks.of(id, ls, fs) }
}

/** What the link sheet saves. [type] is what [to] is to [from]; [id] null = a new link. */
data class LinkDraft(
    val from: FriendId,
    val to: FriendId,
    val type: LinkType,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
    val note: String = "",
    val remindAnniversary: Boolean = false,
    val id: String? = null,
)

/**
 * Saves a link between two people. One link per pair: saving a new one between two people who are already linked
 * replaces that link (keeping its id), whichever way round it was stored. Several romantic links per person are fine.
 */
class SaveLinkUseCase(
    private val links: PersonLinkRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(draft: LinkDraft): PersonLink {
        require(draft.from != draft.to) { "A person can't be linked to themself" }
        val all = links.links.first()
        val existing = draft.id?.let { id -> all.firstOrNull { it.id == id } } ?: PersonLinks.between(draft.from, draft.to, all)
        // Another link already joins these two people: the edited one takes its place.
        PersonLinks.between(draft.from, draft.to, all)?.takeIf { existing != null && it.id != existing.id }?.let { links.delete(it.id) }
        val link = PersonLinks.normalized(
            PersonLink(
                id = existing?.id ?: ids.next(),
                from = draft.from,
                to = draft.to,
                type = draft.type,
                startedOn = draft.startedOn,
                endedOn = draft.endedOn,
                note = draft.note,
                remindAnniversary = draft.remindAnniversary,
                addedAt = existing?.addedAt ?: clock.now(),
            ),
        )
        links.upsert(link)
        return link
    }
}

/** Removes a link; returns it for "Undo". */
class DeleteLinkUseCase(private val links: PersonLinkRepository) {
    suspend operator fun invoke(id: String): PersonLink? {
        val link = links.links.first().firstOrNull { it.id == id }
        links.delete(id)
        return link
    }
}

/** Undo for [DeleteLinkUseCase]. */
class RestoreLinkUseCase(private val links: PersonLinkRepository) {
    suspend operator fun invoke(link: PersonLink) = links.upsert(link)
}

/** "Rui is your…" (F6): what a friend is to you, and the words used for them. A family relation puts them in Family. */
class SetRelationToMeUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, relation: LinkType?, pronouns: Pronouns?) {
        val friend = friends.get(id) ?: return
        friends.upsert(friend.copy(relationToMe = relation, pronouns = pronouns))
    }
}

/** The words used for someone ("sister" / "brother" / "sibling"), set from the link sheet. */
class SetPronounsUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, pronouns: Pronouns?) {
        val friend = friends.get(id) ?: return
        if (friend.pronouns != pronouns) friends.upsert(friend.copy(pronouns = pronouns))
    }
}

/**
 * "Add as a person" (question 1): turns one of a friend's relatives into a friend of their own, linked to them, and
 * takes the relative off the friend (the link replaces it). Name, birthday and its reminder, likes and note come along.
 * Pets and "other" relatives can't be added this way (see [PersonLinks.typeFor]). Returns the new friend's id.
 */
class AddRelativeAsPersonUseCase(
    private val friends: FriendRepository,
    private val links: PersonLinkRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(friendId: FriendId, relativeId: String): FriendId? {
        val friend = friends.get(friendId) ?: return null
        val relative = friend.relatives.firstOrNull { it.id == relativeId } ?: return null
        val type = PersonLinks.typeFor(relative.relation) ?: return null
        val now = clock.now()
        val person = Friend(
            id = FriendId("manual-${ids.next()}"),
            name = relative.name.trim(),
            birthday = relative.birthday,
            notifyBirthday = relative.notifyBirthday,
            likes = relative.likes,
            likesAddedAt = relative.likes.associateWith { now },
            notes = relative.note.trim(),
        )
        friends.upsert(person)
        links.upsert(PersonLink(id = ids.next(), from = friendId, to = person.id, type = type, addedAt = now))
        friends.upsert(friend.copy(relatives = friend.relatives.filterNot { it.id == relativeId }))
        return person.id
    }
}

/** Groups [LinkType]s the way the sheets show them. */
fun LinkType.Companion.of(category: LinkCategory): List<LinkType> = LinkType.entries.filter { it.category == category }

/** The link use cases a friend page needs, in one bundle so its ViewModel takes them as one dependency. */
class PersonLinkUseCases(
    val observe: ObservePersonLinksUseCase,
    val save: SaveLinkUseCase,
    val delete: DeleteLinkUseCase,
    val restore: RestoreLinkUseCase,
    val setRelationToMe: SetRelationToMeUseCase,
    val setPronouns: SetPronounsUseCase,
    val addRelativeAsPerson: AddRelativeAsPersonUseCase,
    val addKid: AddKidUseCase,
    val setShowInMyList: SetShowInMyListUseCase,
    val confirmKinship: ConfirmKinshipUseCase,
    val dismissKinship: DismissKinshipUseCase,
)
