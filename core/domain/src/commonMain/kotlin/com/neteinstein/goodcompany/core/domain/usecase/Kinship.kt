package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkType.AUNT_UNCLE
import com.neteinstein.goodcompany.core.domain.model.LinkType.CHILD
import com.neteinstein.goodcompany.core.domain.model.LinkType.COUSIN
import com.neteinstein.goodcompany.core.domain.model.LinkType.DATING
import com.neteinstein.goodcompany.core.domain.model.LinkType.EX
import com.neteinstein.goodcompany.core.domain.model.LinkType.GRANDCHILD
import com.neteinstein.goodcompany.core.domain.model.LinkType.GRANDPARENT
import com.neteinstein.goodcompany.core.domain.model.LinkType.HALF_SIBLING
import com.neteinstein.goodcompany.core.domain.model.LinkType.IN_LAW
import com.neteinstein.goodcompany.core.domain.model.LinkType.NIECE_NEPHEW
import com.neteinstein.goodcompany.core.domain.model.LinkType.PARENT
import com.neteinstein.goodcompany.core.domain.model.LinkType.PARTNER
import com.neteinstein.goodcompany.core.domain.model.LinkType.SIBLING
import com.neteinstein.goodcompany.core.domain.model.LinkType.SPOUSE
import com.neteinstein.goodcompany.core.domain.model.LinkType.STEP_CHILD
import com.neteinstein.goodcompany.core.domain.model.LinkType.STEP_PARENT
import com.neteinstein.goodcompany.core.domain.model.LinkType.STEP_SIBLING
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.KinshipVocabulary
import com.neteinstein.goodcompany.core.domain.repository.KinshipWords
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// Automatic kinship (F7, issue #41): what someone is to you, worked out from what a relative of yours is to you
// (Friend.relationToMe) and the link between them. Proposed, never set silently: one tap confirms.

/**
 * "Leonor is your niece": a kinship worked out for [person] through [via], one of your relatives: [person] is [via]'s
 * [roleToVia], and [via] is your `via.relationToMe`.
 *
 * With one of [options] and [ambiguous] false it is a plain "Yes, niece / Not right" question. [ambiguous] proposals
 * (half-siblings, step-family, in-laws through an ex, or paths that disagree) keep the choice open.
 */
data class KinshipProposal(
    val person: Friend,
    val options: List<LinkType>,
    val ambiguous: Boolean,
    val via: Friend,
    val roleToVia: LinkType,
) {
    /** The only relation offered, for a plain proposal. */
    val proposed: LinkType? get() = options.singleOrNull()?.takeIf { !ambiguous }
}

/** Works out [KinshipProposal]s from your relatives and the links between people. Pure and deterministic. */
object KinshipResolver {
    /** What someone may be to you: one relation for sure, or a choice to leave open. */
    internal data class Step(val options: List<LinkType>, val ambiguous: Boolean)

    private fun sure(type: LinkType) = Step(listOf(type), ambiguous = false)

    private fun open(vararg types: LinkType) = Step(types.toList(), ambiguous = true)

    /**
     * The kinship table: [viaToMe] is what your relative is to you, [personToVia] what the other person is to them.
     * Null when nothing sensible follows (a friend's friend, your partner's ex, your nephew's kid…).
     */
    internal fun step(viaToMe: LinkType, personToVia: LinkType): Step? = when (viaToMe) {
        SIBLING -> when (personToVia) {
            CHILD -> sure(NIECE_NEPHEW)
            PARENT -> sure(PARENT)
            SIBLING -> sure(SIBLING)
            HALF_SIBLING -> open(SIBLING, HALF_SIBLING)
            SPOUSE, PARTNER -> sure(IN_LAW)
            EX -> open(IN_LAW)
            GRANDPARENT -> sure(GRANDPARENT)
            AUNT_UNCLE -> sure(AUNT_UNCLE)
            COUSIN -> sure(COUSIN)
            else -> null
        }
        HALF_SIBLING -> when (personToVia) {
            CHILD -> sure(NIECE_NEPHEW)
            PARENT -> open(PARENT, STEP_PARENT)
            SIBLING -> open(SIBLING, HALF_SIBLING, STEP_SIBLING)
            SPOUSE, PARTNER -> sure(IN_LAW)
            else -> null
        }
        STEP_SIBLING -> when (personToVia) {
            PARENT -> open(STEP_PARENT, PARENT)
            SIBLING -> open(STEP_SIBLING, HALF_SIBLING)
            else -> null
        }
        PARENT -> when (personToVia) {
            PARENT -> sure(GRANDPARENT)
            SIBLING -> sure(AUNT_UNCLE)
            // Your parent's child: a sibling only when they're the child of both your parents (see proposals()).
            CHILD -> open(SIBLING, HALF_SIBLING, STEP_SIBLING)
            STEP_CHILD -> sure(STEP_SIBLING)
            SPOUSE, PARTNER, DATING -> open(PARENT, STEP_PARENT)
            else -> null
        }
        STEP_PARENT -> when (personToVia) {
            CHILD -> open(STEP_SIBLING, HALF_SIBLING)
            SPOUSE, PARTNER -> open(PARENT, STEP_PARENT)
            else -> null
        }
        CHILD -> when (personToVia) {
            CHILD -> sure(GRANDCHILD)
            SIBLING -> open(CHILD, STEP_CHILD)
            SPOUSE, PARTNER -> sure(IN_LAW)
            EX -> open(IN_LAW)
            else -> null
        }
        GRANDPARENT -> when (personToVia) {
            CHILD -> open(PARENT, AUNT_UNCLE)
            SPOUSE, PARTNER -> open(GRANDPARENT)
            else -> null
        }
        GRANDCHILD -> when (personToVia) {
            PARENT -> open(CHILD, STEP_CHILD, IN_LAW)
            SIBLING -> sure(GRANDCHILD)
            else -> null
        }
        AUNT_UNCLE -> when (personToVia) {
            CHILD -> sure(COUSIN)
            PARENT -> sure(GRANDPARENT)
            SIBLING -> open(AUNT_UNCLE, PARENT)
            SPOUSE, PARTNER -> sure(AUNT_UNCLE)
            else -> null
        }
        NIECE_NEPHEW -> when (personToVia) {
            PARENT -> open(SIBLING, HALF_SIBLING, IN_LAW)
            SIBLING -> sure(NIECE_NEPHEW)
            else -> null
        }
        COUSIN -> when (personToVia) {
            PARENT -> sure(AUNT_UNCLE)
            SIBLING -> sure(COUSIN)
            else -> null
        }
        SPOUSE, PARTNER -> when (personToVia) {
            PARENT, SIBLING -> sure(IN_LAW)
            CHILD -> open(CHILD, STEP_CHILD)
            else -> null
        }
        DATING -> when (personToVia) {
            CHILD -> open(CHILD, STEP_CHILD)
            else -> null
        }
        EX -> when (personToVia) {
            PARENT, SIBLING -> open(IN_LAW)
            CHILD -> open(CHILD, STEP_CHILD)
            else -> null
        }
        else -> null
    }

    /**
     * Every proposal for people whose relationship to you isn't set yet, A–Z. Several paths to the same person are
     * merged: when they agree it stays a plain proposal, otherwise the choice is open. A child of two of your parents
     * is your sibling. Relations already answered "Not right" ([Friend.dismissedKinship]) are never offered again.
     */
    fun proposals(friends: List<Friend>, links: List<PersonLink>): List<KinshipProposal> {
        val byId = friends.associateBy { it.id }
        val paths = linkedMapOf<FriendId, MutableList<Triple<Friend, LinkType, Step>>>()
        for (link in links) {
            for ((viaId, personId) in listOf(link.from to link.to, link.to to link.from)) {
                val via = byId[viaId] ?: continue
                val person = byId[personId] ?: continue
                if (person.id == via.id || person.relationToMe != null) continue
                val viaToMe = via.relationToMe ?: continue
                val personToVia = link.roleSeenBy(viaId) ?: continue
                val step = step(viaToMe, personToVia) ?: continue
                paths.getOrPut(person.id) { mutableListOf() } += Triple(via, personToVia, step)
            }
        }
        return paths.mapNotNull { (id, found) ->
            val person = byId.getValue(id)
            val parentsOfBoth = found.filter { (via, role, _) -> via.relationToMe == PARENT && role == CHILD }.map { it.first.id }.distinct()
            val merged = if (parentsOfBoth.size >= 2) {
                sure(SIBLING)
            } else {
                val options = found.flatMap { it.third.options }.distinct()
                Step(options, ambiguous = found.any { it.third.ambiguous } || options.size > 1)
            }
            val options = merged.options.filterNot { it in person.dismissedKinship }
            if (options.isEmpty()) return@mapNotNull null
            val (via, role, _) = found.first()
            KinshipProposal(person, options, merged.ambiguous, via, role)
        }.sortedBy { it.person.displayName.lowercase() }
    }
}

/**
 * "My nieces and nephews", "os meus primos", "verii mei" (F7): a Home search for kinship. The words come from
 * [KinshipWords] (UI resources, every language); this only matches them. Accents and case are ignored, and the
 * possessive may come before or after each word.
 */
object KinshipQuery {
    /** The relations [query] asks for, or null when it isn't a kinship search (then it's an ordinary name search). */
    fun parse(query: String, words: KinshipWords): Set<LinkType>? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val possessives = words.possessives.map(::normalize).filter { it.isNotEmpty() }.sortedByDescending { it.length }
        val connectors = words.connectors.map(::normalize).filter { it.isNotEmpty() }
        val phrases = words.words.flatMap { (type, list) -> list.map { normalize(it) to type } }.filter { it.first.isNotEmpty() }.toMap()
        val splitter = if (connectors.isEmpty()) {
            Regex(",")
        } else {
            Regex(",|\\s(?:" + connectors.joinToString("|") { Regex.escape(it) } + ")\\s")
        }
        val types = mutableSetOf<LinkType>()
        for (raw in q.split(splitter)) {
            var part = raw.trim()
            if (part.isEmpty()) continue
            // "my cousins", and "verii mei" where the possessive comes after the word.
            possessives.firstOrNull { part.startsWith("$it ") }?.let { part = part.removePrefix(it).trim() }
            possessives.firstOrNull { part.endsWith(" $it") }?.let { part = part.removeSuffix(it).trim() }
            types += phrases[part] ?: return null
        }
        return types.ifEmpty { null }
    }

    private val folds = mapOf(
        'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'ă' to "a", 'ç' to "c", 'è' to "e", 'é' to "e",
        'ê' to "e", 'ë' to "e", 'ì' to "i", 'í' to "i", 'î' to "i", 'ï' to "i", 'ñ' to "n", 'ò' to "o", 'ó' to "o",
        'ô' to "o", 'õ' to "o", 'ö' to "o", 'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u", 'ș' to "s", 'ş' to "s",
        'ț' to "t", 'ţ' to "t", 'œ' to "oe", '’' to "'",
    )

    /** Lower case, no accents, single spaces. */
    fun normalize(text: String): String = buildString {
        text.trim().lowercase().forEach { c -> append(folds[c] ?: c.toString()) }
    }.replace(Regex("\\s+"), " ")
}

/** Kinship proposals for everyone (the "Suggested family" list and its count on Home). */
class ObserveKinshipProposalsUseCase(private val friends: FriendRepository, private val links: PersonLinkRepository) {
    operator fun invoke(): Flow<List<KinshipProposal>> = combine(friends.friends, links.links, KinshipResolver::proposals)
}

/** "Yes, niece": sets what they are to you (their words stay as they are). */
class ConfirmKinshipUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, relation: LinkType) {
        val friend = friends.get(id) ?: return
        friends.upsert(friend.copy(relationToMe = relation))
    }
}

/** "Not right" / "Not family": remembers the relations offered so they aren't proposed again. */
class DismissKinshipUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(id: FriendId, options: Collection<LinkType>) {
        val friend = friends.get(id) ?: return
        val dismissed = friend.dismissedKinship + options
        if (dismissed != friend.dismissedKinship) friends.upsert(friend.copy(dismissedKinship = dismissed))
    }
}

/** Turns a Home search into the relations it asks for ("my cousins" → cousin), in the UI language. */
class ParseKinshipQueryUseCase(private val vocabulary: KinshipVocabulary) {
    suspend operator fun invoke(query: String): Set<LinkType>? = if (query.isBlank()) null else KinshipQuery.parse(query, vocabulary.words())
}
