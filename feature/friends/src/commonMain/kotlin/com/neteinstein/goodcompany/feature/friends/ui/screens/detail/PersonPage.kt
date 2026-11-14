package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson

// Pure rules behind a person's page (issue #90): the Message sheet, the four actions, the tabs and which About cards
// show. No Compose here, so they are tested on the JVM.

/** The three tabs under the top of a person's page; the page always opens on [SUMMARY]. */
enum class PersonTab { SUMMARY, ABOUT, HISTORY }

/** One way to message someone in the Message sheet: the app it opens, the number or address, and whether it's theirs. */
data class MessageOption(val channel: ContactChannel, val address: String, val preferred: Boolean = false) {
    /** Opens the app with nothing typed (`wa.me`, `sms:`, `mailto:`), as the separate buttons did before. */
    val link: String
        get() = when (channel) {
            ContactChannel.WHATSAPP -> "https://wa.me/" + address.filter(Char::isDigit)
            ContactChannel.SMS -> "sms:" + address.filter { it.isDigit() || it == '+' }
            ContactChannel.EMAIL -> "mailto:" + address.trim()
            ContactChannel.CALL -> "tel:" + address.filter { it.isDigit() || it == '+' }
        }
}

/** Which of the four actions under the photo stands out. */
enum class LeadAction { MESSAGE, CALL, WE_MET }

object MessageOptions {
    /**
     * WhatsApp and Text when there is a [phone] number, Email when there is an [email] address; the [preferred]
     * channel first with its badge. Empty when there is neither: Message is then disabled.
     */
    fun of(phone: String?, email: String?, preferred: ContactChannel?): List<MessageOption> {
        val number = phone?.takeIf { it.isNotBlank() }
        val address = email?.takeIf { it.isNotBlank() }
        val options = buildList {
            if (number != null) {
                add(MessageOption(ContactChannel.WHATSAPP, number, preferred == ContactChannel.WHATSAPP))
                add(MessageOption(ContactChannel.SMS, number, preferred == ContactChannel.SMS))
            }
            if (address != null) add(MessageOption(ContactChannel.EMAIL, address, preferred == ContactChannel.EMAIL))
        }
        return options.sortedBy { if (it.preferred) 0 else 1 }
    }

    fun of(friend: Friend): List<MessageOption> = of(friend.phoneNumbers.firstOrNull(), friend.emails.firstOrNull()?.address, friend.preferredChannel)

    /**
     * Message when a messaging channel is preferred, Call when Call is; with no preferred channel "We met" keeps the
     * emphasis it had before (issue #47).
     */
    fun lead(preferred: ContactChannel?): LeadAction = when (preferred) {
        null -> LeadAction.WE_MET
        ContactChannel.CALL -> LeadAction.CALL
        else -> LeadAction.MESSAGE
    }
}

/**
 * Every card of a person's page and the tab it lives on, in page order. In view mode a card with nothing in it doesn't
 * show; on About, "Complete <name>'s profile" ([COMPLETE], [ProfileSections.missing]) offers a chip for each such card
 * instead. Edit mode shows the About cards empty or not.
 */
enum class PersonCard(val tab: PersonTab) {
    /** The catch-up story, the birthday banner, plans, upcoming moments and the rhythm line. */
    COMING_UP(PersonTab.SUMMARY),

    /** News, topics from notes and AI ideas. */
    TO_TALK_ABOUT(PersonTab.SUMMARY),
    TASTES(PersonTab.SUMMARY),
    NOTES(PersonTab.SUMMARY),
    COMPLETE(PersonTab.ABOUT),
    ABOUT(PersonTab.ABOUT),
    MORE_ABOUT(PersonTab.ABOUT),
    OCCASIONS(PersonTab.ABOUT),
    FAMILY(PersonTab.ABOUT),
    OTHER_LINKS(PersonTab.ABOUT),
    MOMENTS(PersonTab.ABOUT),
    GIFTS(PersonTab.ABOUT),
    SAVED(PersonTab.ABOUT),
    FEEDS(PersonTab.ABOUT),
    CONVERSATIONS(PersonTab.HISTORY),
    TIMELINE(PersonTab.HISTORY),
}

/** What a person's page knows beyond the [Friend] itself, for [ProfileSections]. */
data class PageFacts(
    val links: List<LinkedPerson> = emptyList(),
    val savedCount: Int = 0,
    /** Topics from notes (no AI needed). */
    val hasTopics: Boolean = false,
    val aiEnabled: Boolean = false,
)

object ProfileSections {
    /** The About cards "Complete <name>'s profile" can offer; other links are added from the Family card. */
    private val COMPLETABLE = listOf(
        PersonCard.ABOUT,
        PersonCard.MORE_ABOUT,
        PersonCard.OCCASIONS,
        PersonCard.FAMILY,
        PersonCard.MOMENTS,
        PersonCard.GIFTS,
        PersonCard.SAVED,
        PersonCard.FEEDS,
    )

    /** True when [card] has nothing in it for [friend]. */
    fun isEmpty(card: PersonCard, friend: Friend, facts: PageFacts): Boolean = when (card) {
        PersonCard.COMING_UP, PersonCard.TASTES, PersonCard.NOTES, PersonCard.CONVERSATIONS, PersonCard.TIMELINE -> false
        PersonCard.TO_TALK_ABOUT -> friend.lifeUpdates.isEmpty() && !facts.hasTopics && !facts.aiEnabled
        PersonCard.COMPLETE -> missing(friend, facts).isEmpty()
        PersonCard.ABOUT -> friend.job.isBlank() && friend.livesIn.isBlank() && friend.met?.isEmpty != false &&
            friend.introducedBy == null && friend.preferredChannel == null && friend.emails.isEmpty()
        PersonCard.MORE_ABOUT -> friend.customFields.isEmpty()
        PersonCard.OCCASIONS -> friend.occasions.isEmpty()
        PersonCard.FAMILY -> friend.relatives.isEmpty() && facts.links.none { it.role.category != LinkCategory.OTHER }
        PersonCard.OTHER_LINKS -> facts.links.none { it.role.category == LinkCategory.OTHER }
        PersonCard.MOMENTS -> friend.importantEvents.isEmpty()
        PersonCard.GIFTS -> friend.gifts.isEmpty()
        PersonCard.SAVED -> facts.savedCount == 0
        PersonCard.FEEDS -> friend.feeds.isEmpty()
    }

    /** The empty About cards "Complete <name>'s profile" offers a chip for, in page order; the card goes when none are. */
    fun missing(friend: Friend, facts: PageFacts): List<PersonCard> = COMPLETABLE.filter { isEmpty(it, friend, facts) }

    /**
     * The cards [tab] shows, in order. View mode hides empty cards (Tastes and Notes always show, to type into).
     * Edit mode shows every About card (other links only when there are some: they have no editor of their own),
     * drops "Complete…" (every editor is already there) and keeps To talk about, so news can be added or removed.
     */
    fun shown(tab: PersonTab, friend: Friend, facts: PageFacts, editing: Boolean): List<PersonCard> =
        PersonCard.entries.filter { card ->
            card.tab == tab && when {
                card == PersonCard.COMPLETE -> !editing && !isEmpty(card, friend, facts)
                card == PersonCard.OTHER_LINKS -> !isEmpty(card, friend, facts)
                editing -> true
                else -> !isEmpty(card, friend, facts)
            }
        }
}
