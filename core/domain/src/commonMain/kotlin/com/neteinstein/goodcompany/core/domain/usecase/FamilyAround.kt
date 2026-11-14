package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Relation

/**
 * Someone shown around a person's photo (issue #90): a person with a page of their own ([person]), or a relative who
 * is only a name inside the page's friend ([relativeId]) or a legacy kid ([Friend.kids]).
 *
 * [role] is what they are **to the page's friend**, already read from that side ([LinkedPerson.role], gotcha 30).
 */
data class FamilyMember(
    val name: String,
    val role: LinkType,
    val person: Friend? = null,
    val relativeId: String? = null,
    val birthday: Birthday? = null,
    val pronouns: Pronouns? = null,
) {
    /** The first name shown under the small avatar. */
    val firstName: String get() = name.trim().substringBefore(' ').ifEmpty { name }
}

/**
 * The small family network on a person's page (decision "Person page · family around the photo", 2026-11-14):
 * [parents] above the photo, current [partners] beside it, and up to [FamilyAround.MAX_KIDS] [kids] below the name,
 * with [moreKids] behind "+N". Siblings and everyone else stay in the Family card.
 */
data class FamilyAround(
    val parents: List<FamilyMember> = emptyList(),
    val partners: List<FamilyMember> = emptyList(),
    val kids: List<FamilyMember> = emptyList(),
    val moreKids: Int = 0,
) {
    /** No family to show: the page shows just the photo, as before. */
    val isEmpty: Boolean get() = parents.isEmpty() && partners.isEmpty() && kids.isEmpty()

    companion object {
        const val MAX_KIDS = 4

        private val PARENT_TYPES = setOf(LinkType.PARENT, LinkType.STEP_PARENT)
        private val KID_TYPES = setOf(LinkType.CHILD, LinkType.STEP_CHILD)

        /**
         * Splits [links] (seen from [friend], as on their page) and the relatives named inside [friend] into parents,
         * partners and kids. Exes and ended romances are left out; step-parents sit with the parents and step-kids
         * with the kids; relatives who aren't people yet come after the people of the same kind.
         */
        fun of(friend: Friend, links: List<LinkedPerson>, maxKids: Int = MAX_KIDS): FamilyAround {
            fun linked(linkedPerson: LinkedPerson) = FamilyMember(
                name = linkedPerson.person.displayName,
                role = linkedPerson.role,
                person = linkedPerson.person,
                birthday = linkedPerson.person.birthday,
                pronouns = linkedPerson.person.pronouns,
            )
            fun named(relation: Relation, role: LinkType) = friend.relatives.filter { it.relation == relation }
                .map { FamilyMember(name = it.name, role = role, relativeId = it.id, birthday = it.birthday) }

            val parents = links.filter { it.role in PARENT_TYPES }.map(::linked) + named(Relation.PARENT, LinkType.PARENT)
            val partners = links.filter { it.role.isRomantic && it.link.isCurrentRomance }.map(::linked) +
                named(Relation.PARTNER, LinkType.PARTNER)
            val kids = links.filter { it.role in KID_TYPES }.map(::linked) +
                friend.kids.map { FamilyMember(name = it.name, role = LinkType.CHILD, relativeId = it.id, birthday = it.birthday) }
            val shown = kids.take(maxKids.coerceAtLeast(0))
            return FamilyAround(parents, partners, shown, moreKids = kids.size - shown.size)
        }
    }
}
