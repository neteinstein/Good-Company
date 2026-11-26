package org.goodcompany.app.feature.friends.ui.screens.detail

import org.goodcompany.app.core.domain.model.ContactChannel
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.LinkCategory
import org.goodcompany.app.core.domain.model.LinkedPerson

// Pure rules behind a person's page (issues #90, #103): the Message sheet, the actions, the tabs and which cards show.
// No Compose here, so they are tested on the JVM.

/** The two tabs under the top of a person's page (issue #103); the page always opens on [TOGETHER]. */
enum class PersonTab { TOGETHER, ABOUT }

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

/** Which of the actions under the photo stands out. */
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

    /**
     * The one contact button's sheet (issue #118): Call, WhatsApp, Text (with a number), Email (with an address), the
     * [preferred] channel first with its badge. Empty when there is neither: the button is then disabled.
     */
    fun contact(phone: String?, email: String?, preferred: ContactChannel?): List<MessageOption> {
        val number = phone?.takeIf { it.isNotBlank() }
        val call = number?.let { MessageOption(ContactChannel.CALL, it, preferred == ContactChannel.CALL) }
        return (listOfNotNull(call) + of(phone, email, preferred)).sortedBy { if (it.preferred) 0 else 1 }
    }

    fun contact(friend: Friend): List<MessageOption> =
        contact(friend.phoneNumbers.firstOrNull(), friend.emails.firstOrNull()?.address, friend.preferredChannel)

    /** The contact button shows the phone when Call is preferred, the chat bubble otherwise (issue #118). */
    fun showsPhone(preferred: ContactChannel?): Boolean = preferred == ContactChannel.CALL

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
 * Every card of a person's page and the tab it lives on, in page order (issue #103). A card with nothing in it doesn't
 * show; on About, "Complete <name>'s profile" ([COMPLETE], [ProfileSections.missing]) offers a chip for each such card
 * instead, and each chip opens that card's own popup. There is no edit mode: the About cards edit in place, and
 * the person's own settings are in the Edit person popup.
 */
enum class PersonCard(val tab: PersonTab) {
    /**
     * First on Together (issue #117, the Coming up card is gone): the birthday banner, "+ Make plans" with the catch-up
     * line, upcoming plans, reminders, birthday and moments, "Today", then the last 12 past entries and "Show all".
     * "+" logs and ⋮ opens the full timeline; every entry edits and deletes (issue #102).
     */
    TIMELINE(PersonTab.TOGETHER),
    CONVERSATIONS(PersonTab.TOGETHER),

    /** News, topics from notes and AI ideas. */
    TO_TALK_ABOUT(PersonTab.TOGETHER),
    COMPLETE(PersonTab.ABOUT),
    ABOUT(PersonTab.ABOUT),
    MORE_ABOUT(PersonTab.ABOUT),
    TASTES(PersonTab.ABOUT),

    /** Dated notes: one journal entry per note (issue #103). */
    NOTES(PersonTab.ABOUT),
    OCCASIONS(PersonTab.ABOUT),
    FAMILY(PersonTab.ABOUT),
    OTHER_LINKS(PersonTab.ABOUT),
    MOMENTS(PersonTab.ABOUT),
    GIFTS(PersonTab.ABOUT),
    SAVED(PersonTab.ABOUT),

    /** Their profiles (issue #118); the buttons themselves are beside the photo. */
    SOCIALS(PersonTab.ABOUT),

    /** Their websites, before Blogs & feeds (issue #118). */
    WEBSITES(PersonTab.ABOUT),
    FEEDS(PersonTab.ABOUT),
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
        PersonCard.SOCIALS,
        PersonCard.WEBSITES,
        PersonCard.FEEDS,
    )

    /** True when [card] has nothing in it for [friend]. */
    fun isEmpty(card: PersonCard, friend: Friend, facts: PageFacts): Boolean = when (card) {
        PersonCard.TASTES, PersonCard.NOTES, PersonCard.CONVERSATIONS, PersonCard.TIMELINE -> false
        PersonCard.TO_TALK_ABOUT -> friend.lifeUpdates.isEmpty() && !facts.hasTopics && !facts.aiEnabled
        PersonCard.COMPLETE -> missing(friend, facts).isEmpty()
        PersonCard.ABOUT -> friend.job.isBlank() && friend.livesIn.isBlank() && friend.met?.isEmpty != false &&
            friend.introducedBy == null && friend.preferredChannel == null && friend.emails.isEmpty() &&
            friend.addresses.isEmpty()
        PersonCard.MORE_ABOUT -> friend.customFields.isEmpty()
        PersonCard.OCCASIONS -> friend.occasions.isEmpty()
        PersonCard.FAMILY -> friend.relatives.isEmpty() && facts.links.none { it.role.category != LinkCategory.OTHER }
        PersonCard.OTHER_LINKS -> facts.links.none { it.role.category == LinkCategory.OTHER }
        PersonCard.MOMENTS -> friend.importantEvents.isEmpty()
        PersonCard.GIFTS -> friend.gifts.isEmpty()
        PersonCard.SAVED -> facts.savedCount == 0
        PersonCard.SOCIALS -> friend.socials.isEmpty()
        PersonCard.WEBSITES -> friend.websites.isEmpty()
        PersonCard.FEEDS -> friend.feeds.isEmpty()
    }

    /** The empty About cards "Complete <name>'s profile" offers a chip for, in page order; the card goes when none are. */
    fun missing(friend: Friend, facts: PageFacts): List<PersonCard> = COMPLETABLE.filter { isEmpty(it, friend, facts) }

    /** The cards [tab] shows, in order: empty ones are hidden (Tastes and Notes always show, to add to). */
    fun shown(tab: PersonTab, friend: Friend, facts: PageFacts): List<PersonCard> =
        PersonCard.entries.filter { card -> card.tab == tab && !isEmpty(card, friend, facts) }
}
