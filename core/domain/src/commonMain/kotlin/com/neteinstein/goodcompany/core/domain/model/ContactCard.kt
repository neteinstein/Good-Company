package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate

/**
 * The part of a [Friend] that an address-book entry can also hold: a snapshot used to mirror app data onto the
 * contact (Android sideload build only), so you can open the contact and see birthday, kids, family, events and notes.
 * Emails are never part of it: they belong to the contact.
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
    /** Partner, parents, siblings, pets… (written as contact relations). */
    val relatives: List<CardRelative> = emptyList(),
    /** Links to other people in the app (issue #39), written as contact relations like [relatives]. */
    val links: List<CardLink> = emptyList(),
    /** Who introduced you (a "referred by" relation). */
    val referredBy: String? = null,
    val job: String = "",
    val livesIn: String = "",
    val met: MetInfo? = null,
    val food: List<String> = emptyList(),
    val drinks: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    /** "More about" lines, as label → value. */
    val customFields: List<Pair<String, String>> = emptyList(),
) {
    /** True when only a name remains (nothing worth putting on the contact). */
    val isEmpty: Boolean
        get() = birthday == null && !hasExtras

    /** Anything beyond name and birthday: those two normally come *from* the contact, not the other way round. */
    val hasExtras: Boolean
        get() = kids.isNotEmpty() || events.isNotEmpty() || notes.isNotBlank() || likes.isNotEmpty() || dislikes.isNotEmpty() ||
            relatives.isNotEmpty() || links.isNotEmpty() || referredBy != null || job.isNotBlank() || livesIn.isNotBlank() || met != null ||
            food.isNotEmpty() || drinks.isNotEmpty() || interests.isNotEmpty() || customFields.isNotEmpty()

    /**
     * The contact's note: free-form notes first, then tastes, then the profile details (job, where they live, how you
     * met, food, drinks, interests, "More about" lines). Labels come from the caller because the domain never formats
     * user-facing text; a "how we met" date is written as ISO (`2011-09` or `2011-09-04`). Null when there is nothing
     * to say.
     */
    fun noteText(labels: NoteLabels): String? = listOfNotNull(
        notes.trim().takeIf { it.isNotEmpty() },
        likes.labelled(labels.likes),
        dislikes.labelled(labels.dislikes),
        job.trim().takeIf { it.isNotEmpty() }?.let { "${labels.job}: $it" },
        livesIn.trim().takeIf { it.isNotEmpty() }?.let { "${labels.livesIn}: $it" },
        met?.let { m ->
            val date = m.on?.let { if (m.dayKnown) it.toString() else it.toString().take(7) }
            listOfNotNull(m.place.trim().takeIf { it.isNotEmpty() }, date).takeIf { it.isNotEmpty() }
                ?.let { "${labels.met}: ${it.joinToString(", ")}" }
        },
        food.labelled(labels.food),
        drinks.labelled(labels.drinks),
        interests.labelled(labels.interests),
    ).plus(customFields.map { (label, value) -> "$label: $value" })
        .takeIf { it.isNotEmpty() }?.joinToString("\n")

    fun noteText(likesLabel: String, dislikesLabel: String): String? =
        noteText(NoteLabels(likes = likesLabel, dislikes = dislikesLabel))

    private fun List<String>.labelled(label: String): String? = takeIf { it.isNotEmpty() }?.let { "$label: ${it.joinToString(", ")}" }

    companion object {
        /**
         * The card for [friend]. [links] may be every link on file: only [friend]'s are used, each written with the other
         * person's name from [people] and what they are to [friend] (a link to someone not in [people] is left out).
         */
        fun from(friend: Friend, links: List<PersonLink> = emptyList(), people: Map<FriendId, Friend> = emptyMap()): ContactCard = ContactCard(
            name = friend.name,
            birthday = friend.birthday,
            // Kids are people linked as their child (F9): written as kids (a child relation and their birthday).
            kids = links.mapNotNull { link ->
                if (link.roleSeenBy(friend.id) != LinkType.CHILD) return@mapNotNull null
                val kid = link.otherThan(friend.id)?.let(people::get) ?: return@mapNotNull null
                kid.name.trim().takeIf { it.isNotEmpty() }?.let { CardKid(it, kid.birthday) }
            },
            events = friend.importantEvents.mapNotNull { e -> e.title.trim().takeIf { it.isNotEmpty() }?.let { CardEvent(it, e.date) } },
            notes = friend.notes,
            likes = friend.likes.map(String::trim).filter(String::isNotEmpty),
            dislikes = friend.dislikes.map(String::trim).filter(String::isNotEmpty),
            relatives = friend.relatives.mapNotNull { r -> r.name.trim().takeIf { it.isNotEmpty() }?.let { CardRelative(it, r.relation) } },
            links = links.mapNotNull { link ->
                val other = link.otherThan(friend.id)?.let(people::get) ?: return@mapNotNull null
                val role = link.roleSeenBy(friend.id)?.takeIf { it != LinkType.CHILD } ?: return@mapNotNull null
                other.name.trim().takeIf { it.isNotEmpty() }?.let { CardLink(it, role, other.pronouns) }
            },
            referredBy = friend.introducedBy?.name?.trim()?.takeIf { it.isNotEmpty() },
            job = friend.job.trim(),
            livesIn = friend.livesIn.trim(),
            met = friend.met?.takeUnless { it.isEmpty },
            food = friend.food.map(String::trim).filter(String::isNotEmpty),
            drinks = friend.drinks.map(String::trim).filter(String::isNotEmpty),
            interests = friend.interests.map(String::trim).filter(String::isNotEmpty),
            customFields = friend.customFields.mapNotNull { f ->
                val label = f.label.trim()
                val value = f.value.trim()
                if (label.isEmpty() || value.isEmpty()) null else label to value
            },
        )
    }
}

/** A linked person, as the contact can show them: a name, what they are to the friend and the words for them. */
data class CardLink(val name: String, val role: LinkType, val pronouns: Pronouns? = null)

/** A relative, as the contact can show them: a name and how they're related. */
data class CardRelative(val name: String, val relation: Relation)

/** The labels [ContactCard.noteText] puts before each line, in the user's language. */
data class NoteLabels(
    val likes: String,
    val dislikes: String,
    val job: String = "",
    val livesIn: String = "",
    val met: String = "",
    val food: String = "",
    val drinks: String = "",
    val interests: String = "",
)

/** A child, as the contact can show them: a name and (optionally) a birthday. */
data class CardKid(val name: String, val birthday: Birthday? = null)

/** A dated event, as the contact can show it. */
data class CardEvent(val title: String, val date: LocalDate)
