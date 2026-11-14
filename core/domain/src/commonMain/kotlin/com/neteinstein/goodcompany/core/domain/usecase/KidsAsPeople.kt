package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator

// Kids are people (F9, issue #40): a kid is a Friend linked to their parent as LinkType.CHILD (issue #39).

/** Who someone's kids and parents are, and how kids stored the old way become people. */
object KidsAsPeople {
    /** Id prefix of the link that joins a converted kid to their parent (the database migration uses it too). */
    const val LINK_PREFIX = "kid-link-"

    /** [id]'s kids: the people linked to them as their child, in the order they were added. */
    fun kidsOf(id: FriendId, links: List<PersonLink>, byId: Map<FriendId, Friend>): List<LinkedPerson> = links.mapNotNull { link ->
        if (link.roleSeenBy(id) != LinkType.CHILD) return@mapNotNull null
        val kid = link.otherThan(id)?.let(byId::get) ?: return@mapNotNull null
        LinkedPerson(link, kid, LinkType.CHILD)
    }

    /** [id]'s parents among your people. */
    fun parentsOf(id: FriendId, links: List<PersonLink>, byId: Map<FriendId, Friend>): List<Friend> = links.mapNotNull { link ->
        if (link.roleSeenBy(id) != LinkType.PARENT) return@mapNotNull null
        link.otherThan(id)?.let(byId::get)
    }

    /** Every person's parents, for lists that show many people at once. Only people with a known parent are keys. */
    fun parentsById(links: List<PersonLink>, friends: List<Friend>): Map<FriendId, List<Friend>> {
        val byId = friends.associateBy { it.id }
        val out = linkedMapOf<FriendId, MutableList<Friend>>()
        links.forEach { link ->
            val (parent, kid) = when (link.type) {
                LinkType.CHILD -> link.from to link.to
                LinkType.PARENT -> link.to to link.from
                else -> return@forEach
            }
            val p = byId[parent] ?: return@forEach
            if (kid in byId) out.getOrPut(kid) { mutableListOf() } += p
        }
        return out
    }

    /** Whether a new kid of [parent] is in the main list to begin with: yes for family, no for friends' kids (F9). */
    fun showsByDefault(parent: Friend): Boolean = parent.isFamily

    /**
     * Turns kids stored the old way ([Friend.kids], [com.neteinstein.goodcompany.core.domain.model.Gift.forKidId]) into
     * people, as the database migration (`10.sqm`) does: each kid keeps their id unless another person already has it (then
     * `<parent id>-<kid id>`), gets their birthday, its reminder from the parent's, their likes and a [LinkType.CHILD]
     * link from the parent, shows in the main list when the parent is family, and takes along the gifts meant for them.
     * Friends without old kids are returned unchanged, so it is safe to run on any data.
     */
    fun convert(friends: List<Friend>, links: List<PersonLink>): Pair<List<Friend>, List<PersonLink>> {
        if (friends.none { it.kids.isNotEmpty() }) return friends to links
        val taken = friends.mapTo(HashSet()) { it.id.value }
        val people = mutableListOf<Friend>()
        val newLinks = mutableListOf<PersonLink>()
        val parents = friends.map { parent ->
            if (parent.kids.isEmpty()) return@map parent
            val moved = mutableSetOf<String>()
            parent.kids.forEach { kid ->
                val id = kid.id.takeIf { taken.add(it) } ?: "${parent.id.value}-${kid.id}".also { taken += it }
                val gifts = parent.gifts.filter { it.forKidId == kid.id }
                moved += gifts.map { it.id }
                people += Friend(
                    id = FriendId(id),
                    name = kid.name,
                    birthday = kid.birthday,
                    notifyBirthday = parent.notifyBirthday,
                    likes = kid.likes,
                    gifts = gifts.map { it.copy(forKidId = null) },
                    showInMyList = showsByDefault(parent),
                )
                newLinks += PersonLink(LINK_PREFIX + id, parent.id, FriendId(id), LinkType.CHILD, addedAt = kid.addedAt)
            }
            parent.copy(
                kids = emptyList(),
                gifts = parent.gifts.filterNot { it.id in moved }.map { it.copy(forKidId = null) },
            )
        }
        return (parents + people) to (links + newLinks)
    }

    /** [convert] for a whole backup: an old backup's kids come back as people. */
    fun convert(backup: Backup): Backup {
        val (friends, links) = convert(backup.friends, backup.links)
        return if (friends === backup.friends) backup else backup.copy(friends = friends, links = links)
    }
}

/** What "Add <name>'s kid" saves (F9). */
data class KidDraft(
    val name: String,
    val birthday: Birthday? = null,
    val likes: List<String> = emptyList(),
    val pronouns: Pronouns? = null,
    val showInMyList: Boolean = false,
    val notifyBirthday: Boolean = true,
)

/**
 * "Add <name>'s kid" (F9): creates the kid as a person of their own, with their own page and birthday reminder,
 * linked to the parent as their child. Returns the new person's id, or null when the parent is gone or the name is blank.
 */
class AddKidUseCase(
    private val friends: FriendRepository,
    private val links: PersonLinkRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(parentId: FriendId, draft: KidDraft): FriendId? {
        friends.get(parentId) ?: return null
        val name = draft.name.trim().takeIf { it.isNotEmpty() } ?: return null
        val now = clock.now()
        val likes = draft.likes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val kid = Friend(
            id = FriendId("manual-${ids.next()}"),
            name = name,
            birthday = draft.birthday,
            notifyBirthday = draft.notifyBirthday,
            likes = likes,
            likesAddedAt = likes.associateWith { now },
            pronouns = draft.pronouns,
            showInMyList = draft.showInMyList,
        )
        friends.upsert(kid)
        links.upsert(PersonLink(id = ids.next(), from = parentId, to = kid.id, type = LinkType.CHILD, addedAt = now))
        return kid.id
    }
}

/** "Show in my list" (F9): whether someone appears in the main A–Z list. */
class SetShowInMyListUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, show: Boolean) {
        val friend = friends.get(id) ?: return
        if (friend.showInMyList != show) friends.upsert(friend.copy(showInMyList = show))
    }
}
