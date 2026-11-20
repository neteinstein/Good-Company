package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.usecase.StoryRing
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.LeadAction
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.MessageOptions
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.PageFacts
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.PersonCard
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.PersonTab
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.ProfileSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonPageTest {
    private val phone = "+351 912 345 678"
    private val mail = "ana@example.com"

    // --- Message sheet ---

    @Test
    fun phoneOnly_offersWhatsAppAndText() {
        val options = MessageOptions.of(phone, null, null)
        assertEquals(listOf(ContactChannel.WHATSAPP, ContactChannel.SMS), options.map { it.channel })
        assertEquals("https://wa.me/351912345678", options[0].link)
        assertEquals("sms:+351912345678", options[1].link)
        assertTrue(options.none { it.preferred })
    }

    @Test
    fun emailOnly_offersEmail() {
        val options = MessageOptions.of(null, " $mail ", null)
        assertEquals(listOf(ContactChannel.EMAIL), options.map { it.channel })
        assertEquals("mailto:$mail", options.single().link)
    }

    @Test
    fun both_offersAllThree_andTheirPreferredComesFirst() {
        assertEquals(
            listOf(ContactChannel.WHATSAPP, ContactChannel.SMS, ContactChannel.EMAIL),
            MessageOptions.of(phone, mail, null).map { it.channel },
        )
        val emailFirst = MessageOptions.of(phone, mail, ContactChannel.EMAIL)
        assertEquals(listOf(ContactChannel.EMAIL, ContactChannel.WHATSAPP, ContactChannel.SMS), emailFirst.map { it.channel })
        assertTrue(emailFirst.first().preferred)
        assertEquals(1, emailFirst.count { it.preferred })
        assertEquals(ContactChannel.SMS, MessageOptions.of(phone, mail, ContactChannel.SMS).first().channel)
    }

    @Test
    fun neither_offersNothing_andAPreferenceThatCantBeUsedIsNotShown() {
        assertTrue(MessageOptions.of(null, null, ContactChannel.WHATSAPP).isEmpty())
        assertTrue(MessageOptions.of(" ", "", null).isEmpty())
        // Email preferred but no address: the phone options, none marked preferred.
        assertTrue(MessageOptions.of(phone, null, ContactChannel.EMAIL).none { it.preferred })
        // Call is preferred: no message option carries the badge.
        assertTrue(MessageOptions.of(phone, mail, ContactChannel.CALL).none { it.preferred })
    }

    @Test
    fun fromAFriend_usesTheirFirstNumberAndAddress() {
        val ana = friend("ana") {
            copy(phoneNumbers = listOf(phone, "+1 555"), emails = listOf(FriendEmail(mail)), preferredChannel = ContactChannel.SMS)
        }
        val options = MessageOptions.of(ana)
        assertEquals(ContactChannel.SMS, options.first().channel)
        assertEquals(phone, options.first().address)
        assertEquals(mail, options.last().address)
    }

    @Test
    fun leadAction_followsThePreferredChannel() {
        assertEquals(LeadAction.MESSAGE, MessageOptions.lead(ContactChannel.WHATSAPP))
        assertEquals(LeadAction.MESSAGE, MessageOptions.lead(ContactChannel.EMAIL))
        assertEquals(LeadAction.CALL, MessageOptions.lead(ContactChannel.CALL))
        assertEquals(LeadAction.WE_MET, MessageOptions.lead(null))
    }

    // --- Tabs and empty cards (issue #103: Together · About, no edit mode) ---

    @Test
    fun twoTabs_togetherFirst_andEveryCardBelongsToOne() {
        assertEquals(listOf(PersonTab.TOGETHER, PersonTab.ABOUT), PersonTab.entries)
        // Coming up is the first entry, then the timeline; To talk about comes after.
        assertEquals(
            listOf(PersonCard.COMING_UP, PersonCard.TIMELINE, PersonCard.CONVERSATIONS, PersonCard.TO_TALK_ABOUT),
            PersonCard.entries.filter { it.tab == PersonTab.TOGETHER },
        )
        // Tastes and Notes moved to About.
        assertEquals(PersonTab.ABOUT, PersonCard.TASTES.tab)
        assertEquals(PersonTab.ABOUT, PersonCard.NOTES.tab)
        assertEquals(PersonTab.ABOUT, PersonCard.FAMILY.tab)
        assertEquals(PersonTab.ABOUT, PersonCard.COMPLETE.tab)
    }

    @Test
    fun emptyProfile_aboutShowsTheCompleteCard_tastesAndNotes_withAChipPerEmptySection() {
        val ana = friend("ana")
        val facts = PageFacts()
        assertEquals(
            listOf(PersonCard.COMPLETE, PersonCard.TASTES, PersonCard.NOTES),
            ProfileSections.shown(PersonTab.ABOUT, ana, facts),
        )
        assertEquals(
            listOf(
                PersonCard.ABOUT,
                PersonCard.MORE_ABOUT,
                PersonCard.OCCASIONS,
                PersonCard.FAMILY,
                PersonCard.MOMENTS,
                PersonCard.GIFTS,
                PersonCard.SAVED,
                PersonCard.FEEDS,
            ),
            ProfileSections.missing(ana, facts),
        )
    }

    @Test
    fun filledSections_showAndLeaveTheCompleteChips_andTheCardGoesWhenNothingIsMissing() {
        val partner = LinkedPerson(PersonLink("l", FriendId("ana"), FriendId("rui"), LinkType.SPOUSE), friend("rui"), LinkType.SPOUSE)
        val ana = friend("ana") {
            copy(job = "Designer", gifts = listOf(Gift("g", "Book")), customFields = listOf(CustomField("c", "Shoe size", "38")))
        }
        val facts = PageFacts(links = listOf(partner))
        val shown = ProfileSections.shown(PersonTab.ABOUT, ana, facts)
        assertEquals(
            listOf(
                PersonCard.COMPLETE,
                PersonCard.ABOUT,
                PersonCard.MORE_ABOUT,
                PersonCard.TASTES,
                PersonCard.NOTES,
                PersonCard.FAMILY,
                PersonCard.GIFTS,
            ),
            shown,
        )
        assertFalse(PersonCard.FAMILY in ProfileSections.missing(ana, facts))

        val full = ana.copy(
            occasions = listOf(com.neteinstein.goodcompany.core.domain.model.Occasion.entries.first()),
            importantEvents = listOf(
                com.neteinstein.goodcompany.core.domain.model.ImportantEvent("e", "Wedding", kotlinx.datetime.LocalDate(2027, 5, 1)),
            ),
            feeds = listOf(com.neteinstein.goodcompany.core.domain.model.FriendFeed(url = "https://blog.example")),
        )
        val fullFacts = facts.copy(savedCount = 2)
        assertTrue(ProfileSections.missing(full, fullFacts).isEmpty())
        assertFalse(PersonCard.COMPLETE in ProfileSections.shown(PersonTab.ABOUT, full, fullFacts))
        // Occasions has a popup of its own now, so a filled Occasions card shows like any other.
        assertTrue(PersonCard.OCCASIONS in ProfileSections.shown(PersonTab.ABOUT, full, fullFacts))
    }

    @Test
    fun aRelativeWhoIsOnlyAName_fillsTheFamilyCard_andOtherLinksHaveNoChip() {
        val withSister = friend("ana") { copy(relatives = listOf(Relative("r", Relation.SIBLING, "Bia"))) }
        assertFalse(PersonCard.FAMILY in ProfileSections.missing(withSister, PageFacts()))

        val colleague = LinkedPerson(PersonLink("l", FriendId("ana"), FriendId("joao"), LinkType.COLLEAGUE), friend("joao"), LinkType.COLLEAGUE)
        val facts = PageFacts(links = listOf(colleague))
        val ana = friend("ana")
        assertTrue(PersonCard.FAMILY in ProfileSections.missing(ana, facts))
        assertFalse(PersonCard.OTHER_LINKS in ProfileSections.missing(ana, facts))
        assertTrue(PersonCard.OTHER_LINKS in ProfileSections.shown(PersonTab.ABOUT, ana, facts))
        assertFalse(PersonCard.OTHER_LINKS in ProfileSections.shown(PersonTab.ABOUT, ana, PageFacts()))
    }

    @Test
    fun together_startsWithComingUpAndTheTimeline_andHidesToTalkAboutWhenEmpty() {
        val ana = friend("ana")
        assertEquals(
            listOf(PersonCard.COMING_UP, PersonCard.TIMELINE, PersonCard.CONVERSATIONS),
            ProfileSections.shown(PersonTab.TOGETHER, ana, PageFacts()),
        )
        assertEquals(PersonCard.TO_TALK_ABOUT, ProfileSections.shown(PersonTab.TOGETHER, ana, PageFacts(hasTopics = true)).last())
        assertTrue(PersonCard.TO_TALK_ABOUT in ProfileSections.shown(PersonTab.TOGETHER, ana, PageFacts(aiEnabled = true)))
        val withNews = ana.copy(
            lifeUpdates = listOf(com.neteinstein.goodcompany.core.domain.model.LifeUpdate("u", "New job", kotlin.time.Instant.DISTANT_PAST)),
        )
        assertTrue(PersonCard.TO_TALK_ABOUT in ProfileSections.shown(PersonTab.TOGETHER, withNews, PageFacts()))
    }

    // --- Story ring (issue #103) ---

    @Test
    fun storyRing_brightWhileSomethingFreshIsUnseen_greyOnceSeen_noneWithNothingFresh() {
        val t1 = kotlin.time.Instant.fromEpochMilliseconds(1_000)
        val t2 = kotlin.time.Instant.fromEpochMilliseconds(2_000)
        assertEquals(StoryRing.NONE, StoryRing.of(emptyList(), null))
        assertEquals(StoryRing.NONE, StoryRing.of(emptyList(), t2))
        assertEquals(StoryRing.FRESH, StoryRing.of(listOf(t1), null))
        assertEquals(StoryRing.SEEN, StoryRing.of(listOf(t1), t1))
        assertEquals(StoryRing.SEEN, StoryRing.of(listOf(t1), t2))
        // Something added after the story was opened makes it bright again.
        assertEquals(StoryRing.FRESH, StoryRing.of(listOf(t1, t2), t1))
    }
}
