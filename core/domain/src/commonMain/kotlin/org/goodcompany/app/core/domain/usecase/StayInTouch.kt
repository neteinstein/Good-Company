package org.goodcompany.app.core.domain.usecase

import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.LinkType

/**
 * Issue #132: when "Stay in touch" ([Friend.tracksKeepInTouch]) starts off. People added as a reference (a kid added
 * to someone, a relative turned into a person) start off, and so do your own kids and partner (their
 * [Friend.relationToMe]). People added as friends start on, and linking an existing person never changes it. It can
 * always be switched back on in Edit person.
 *
 * Migration `28.sqm` applies the same rules once to the people stored before this change.
 */
object StayInTouch {
    /** What someone is to you that starts them off: your kids and your current partner. Parents and siblings stay on. */
    val offForRelationToMe: Set<LinkType> = setOf(
        LinkType.CHILD,
        LinkType.STEP_CHILD,
        LinkType.DATING,
        LinkType.SPOUSE,
        LinkType.PARTNER,
    )

    fun startsOffFor(relationToMe: LinkType?): Boolean = relationToMe in offForRelationToMe

    /**
     * [friend] after "Related to you?" becomes [relation]: tracking turns off only when they *become* your kid or
     * partner (so it never turns back off once switched on again, and other changes leave it alone).
     */
    fun afterRelationToMe(friend: Friend, relation: LinkType?): Friend =
        if (startsOffFor(relation) && !startsOffFor(friend.relationToMe)) {
            friend.copy(relationToMe = relation, tracksKeepInTouch = false)
        } else {
            friend.copy(relationToMe = relation)
        }
}
