package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.CardRelative
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.NoteLabels
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.model.TasteKind
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpStoryBuilder
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger.hasAppData
import com.neteinstein.goodcompany.core.domain.usecase.FreshItem
import com.neteinstein.goodcompany.core.domain.usecase.HomeWidget
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.OfflineTopics
import com.neteinstein.goodcompany.core.domain.usecase.StorySlide
import com.neteinstein.goodcompany.core.domain.usecase.TopicHint
import com.neteinstein.goodcompany.core.domain.usecase.VCardParser
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #20: the full friend profile across merging, contact cards, notifications, the story, topics and imports. */
class FullProfileTest {
    private val zone = TimeZone.UTC

    // --- Emails from contacts --------------------------------------------------------------------------------------

    @Test
    fun aSync_replacesContactEmails_andKeepsTypedOnes() {
        val ana = friend("contact-1", "Ana") {
            copy(contactId = "1", emails = listOf(FriendEmail("old@work.example", fromContact = true), FriendEmail("me@home.example")))
        }
        val merged = ContactMerger.merge(listOf(ana), listOf(ContactRecord("1", "Ana", emptyList(), null, emails = listOf("new@work.example"))))
        assertEquals(listOf(FriendEmail("new@work.example", true), FriendEmail("me@home.example")), merged.single().emails)
    }

    @Test
    fun aTypedEmailTheContactNowHas_becomesAContactOne_withoutDuplicates() {
        val emails = ContactMerger.mergeEmails(listOf(FriendEmail("Ana@Example.com")), listOf("ana@example.com", " ana@example.com ", ""))
        assertEquals(listOf(FriendEmail("ana@example.com", fromContact = true)), emails)
    }

    @Test
    fun newContactFriends_getTheirEmailsAsContactOnes() {
        val merged = ContactMerger.merge(emptyList(), listOf(ContactRecord("1", "Ana", emptyList(), null, emails = listOf("a@x.example"))))
        assertEquals(listOf(FriendEmail("a@x.example", true)), merged.single().emails)
    }

    @Test
    fun anOrphanHoldingOnlyNewProfileData_isKept() {
        val cases = listOf(
            friend("a") { copy(job = "Designer") },
            friend("a") { copy(livesIn = "Porto") },
            friend("a") { copy(met = MetInfo("Uni")) },
            friend("a") { copy(introducedBy = Introducer(null, "Rui")) },
            friend("a") { copy(preferredChannel = ContactChannel.CALL) },
            friend("a") { copy(emails = listOf(FriendEmail("typed@x.example"))) },
            friend("a") { copy(relatives = listOf(Relative("r", Relation.PET, "Bolacha"))) },
            friend("a") { copy(food = listOf("ramen")) },
            friend("a") { copy(drinks = listOf("tea")) },
            friend("a") { copy(interests = listOf("chess")) },
            friend("a") { copy(customFields = listOf(CustomField("f", "Shoe size", "38"))) },
        )
        cases.forEach { f ->
            assertTrue(f.hasAppData(), "$f")
            val merged = ContactMerger.merge(listOf(f.copy(contactId = "1")), emptyList())
            assertTrue(merged.single().isOrphaned)
        }
        // Emails that only came from the contact aren't the user's data.
        assertFalse(friend("a") { copy(emails = listOf(FriendEmail("c@x.example", fromContact = true))) }.hasAppData())
        assertTrue(ContactMerger.merge(listOf(friend("a") { copy(contactId = "1") }), emptyList()).isEmpty())
    }

    @Test
    fun picking_addsEmailsWithoutDroppingKnownOnes() = runTest {
        val g = TestGraph(listOf(friend("contact-1", "Ana") { copy(contactId = "1", emails = listOf(FriendEmail("typed@x.example"))) }))
        g.importPickedContacts(listOf(ContactRecord("1", "Ana", emptyList(), null, emails = listOf("ana@x.example", "TYPED@x.example"))))
        assertEquals(listOf(FriendEmail("typed@x.example"), FriendEmail("ana@x.example", true)), g.friendRepo.friends.value.single().emails)
        // A picker that couldn't read emails leaves them as they are.
        g.importPickedContacts(listOf(ContactRecord("1", "Ana", emptyList(), null)))
        assertEquals(2, g.friendRepo.friends.value.single().emails.size)
        g.importPickedContacts(listOf(ContactRecord("2", "Rui", emptyList(), null, emails = listOf("rui@x.example"))))
        assertEquals(listOf(FriendEmail("rui@x.example", true)), g.friendRepo.friends.value.first { it.name == "Rui" }.emails)
    }

    @Test
    fun vcf_readsEmails_andImportsThemAsTypedOnes() = runTest {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ana Silva
            EMAIL;TYPE=INTERNET:ana@example.com
            item1.EMAIL:mailto:Ana@Example.com
            EMAIL:not-an-email
            END:VCARD
        """.trimIndent()
        assertEquals(listOf("ana@example.com"), VCardParser.parse(vcf).single().emails)
        val g = TestGraph(emptyList())
        g.importContactsFile(vcf)
        assertEquals(listOf(FriendEmail("ana@example.com")), g.friendRepo.friends.value.single().emails)
        // Importing again only adds what's missing.
        g.importContactsFile(vcf.replace("EMAIL:not-an-email", "EMAIL:second@example.com"))
        assertEquals(listOf("ana@example.com", "second@example.com"), g.friendRepo.friends.value.single().emails.map { it.address })
    }

    // --- Contact card (write-back) -----------------------------------------------------------------------------------

    @Test
    fun contactCard_carriesRelatives_referredBy_andProfileNoteLines_butNeverEmails() {
        val ana = friend("ana", "Ana") {
            copy(
                notes = "Met at uni",
                likes = listOf("jazz"),
                job = "Designer",
                livesIn = "Porto",
                met = MetInfo("Erasmus in Ghent", LocalDate(2011, 9, 1), dayKnown = false),
                introducedBy = Introducer(FriendId("rui"), " Rui "),
                emails = listOf(FriendEmail("a@x.example")),
                relatives = listOf(
                    Relative("r1", Relation.PARTNER, "Tiago"),
                    Relative("r2", Relation.PET, " "),
                    Relative("r3", Relation.SIBLING, "Rita"),
                ),
                food = listOf("ramen"),
                drinks = listOf("tea"),
                interests = listOf("chess"),
                customFields = listOf(CustomField("f", "Shoe size", "38"), CustomField("g", "Empty", " ")),
            )
        }
        val card = ContactCard.from(ana)
        assertEquals(listOf(CardRelative("Tiago", Relation.PARTNER), CardRelative("Rita", Relation.SIBLING)), card.relatives)
        assertEquals("Rui", card.referredBy)
        assertTrue(card.hasExtras)
        val labels = NoteLabels("Loves", "Not a fan of", "Work", "Lives in", "How we met", "Food", "Drinks", "Interests")
        assertEquals(
            "Met at uni\nLoves: jazz\nWork: Designer\nLives in: Porto\nHow we met: Erasmus in Ghent, 2011-09\n" +
                "Food: ramen\nDrinks: tea\nInterests: chess\nShoe size: 38",
            card.noteText(labels),
        )
        assertFalse("a@x.example" in card.toString())
        // A full date is written in full.
        assertEquals(
            "How we met: 2011-09-04",
            ContactCard.from(friend("b") { copy(met = MetInfo("", LocalDate(2011, 9, 4))) }).noteText(labels),
        )
    }

    @Test
    fun contactCard_withOnlyAProfileDetail_isWorthWriting() {
        assertTrue(ContactCard.from(friend("a") { copy(job = "Nurse") }).hasExtras)
        assertTrue(ContactCard.from(friend("a") { copy(relatives = listOf(Relative("r", Relation.PARENT, "Rosa"))) }).hasExtras)
        assertFalse(ContactCard.from(friend("a") { copy(emails = listOf(FriendEmail("a@x.example"))) }).hasExtras)
    }

    // --- Relatives' birthdays ----------------------------------------------------------------------------------------

    private val family = friend("ana", "Ana") {
        copy(
            relatives = listOf(
                Relative("on", Relation.PARTNER, "Tiago", Birthday(3, 20, 1988), notifyBirthday = true),
                Relative("off", Relation.PARENT, "Rosa", Birthday(3, 21), notifyBirthday = false),
            ),
        )
    }

    @Test
    fun aRelativesBirthday_isNotifiedOnlyWhenSwitchedOn_evenIfTheFriendsIsOff() {
        val planned = NotificationPlanner.plan(listOf(family), emptyList(), emptyList(), at(2026, 3, 10), zone, reminderHour = 9)
        assertEquals(listOf("relative:ana:on:2026-03-20"), planned.map { it.id })
        assertEquals(NotificationContent.RelativeBirthday("Tiago", "Ana", Relation.PARTNER, 38), planned.single().content)
    }

    @Test
    fun aRelativesBirthday_showsInComingUp_onlyWhenSwitchedOn_butNotOnTheWidget() {
        val today = LocalDate(2026, 3, 10)
        val items = AgendaBuilder.build(listOf(family), emptyList(), emptyList(), today, zone)
        val item = assertIs<AgendaItem.RelativeBirthday>(items.single())
        assertEquals("Tiago", item.relative.name)
        assertEquals(38, item.turning)
        assertNull(HomeWidget.summarize(items, emptyList(), today, isPrivate = false).next)
    }

    // --- Story and topics ----------------------------------------------------------------------------------------------

    @Test
    fun theStory_hasAnAboutSlide_familyWithRelatives_tasteGroups_andNewThingsCountAsFresh() = runTest {
        val ana = friend("ana", "Ana") {
            copy(
                job = "Designer",
                livesIn = "Porto",
                met = MetInfo("Uni", LocalDate(2010, 10, 1), dayKnown = false),
                introducedBy = Introducer(null, "Rui"),
                relatives = listOf(Relative("r", Relation.PET, "Bolacha", note = "beagle", addedAt = at(2026, 3, 9))),
                food = listOf("ramen"),
                interests = listOf("chess"),
                interestsAddedAt = mapOf("chess" to at(2026, 3, 8)),
                foodAddedAt = mapOf("ramen" to at(2025, 1, 1)),
            )
        }
        val g = TestGraph(listOf(ana))
        g.hangoutRepo.add(Hangout("h", listOf(ana.id), at(2026, 3, 1)))
        val story = g.observeCatchUpStory(ana.id).first()!!
        val fresh = story.slides.filterIsInstance<StorySlide.Fresh>().map { it.item }
        assertEquals(
            listOf(FreshItem.NewRelative("Bolacha", Relation.PET, at(2026, 3, 9)), FreshItem.NewTaste("chess", TasteKind.INTEREST, at(2026, 3, 8))),
            fresh,
        )
        assertEquals(
            StorySlide.About("Designer", "Porto", MetInfo("Uni", LocalDate(2010, 10, 1), dayKnown = false), "Rui"),
            story.slides.filterIsInstance<StorySlide.About>().single(),
        )
        val familySlide = story.slides.filterIsInstance<StorySlide.Kids>().single()
        assertTrue(familySlide.kids.isEmpty())
        assertEquals(listOf("Bolacha"), familySlide.relatives.map { it.name })
        val tastes = story.slides.filterIsInstance<StorySlide.Tastes>().single()
        assertEquals(listOf("ramen"), tastes.food)
        assertEquals(listOf("chess"), tastes.interests)
        assertTrue("Bolacha" in story.slides.filterIsInstance<StorySlide.Outro>().single().askAbout)
        assertEquals(2, CatchUpStoryBuilder.freshItems(ana).count { it.addedAt > at(2026, 3, 1) })
    }

    @Test
    fun aFriendWithNoAboutDetails_hasNoAboutSlide() = runTest {
        val g = TestGraph(listOf(friend("ana") { copy(met = MetInfo("  ")) }))
        assertTrue(g.observeCatchUpStory(FriendId("ana")).first()!!.slides.none { it is StorySlide.About })
    }

    @Test
    fun topics_includeARelativesBirthday_andAnInterestAddedSinceYouMet() {
        val today = LocalDate(2026, 3, 10)
        val ana = friend("ana") {
            copy(
                relatives = listOf(
                    Relative("r", Relation.SIBLING, "Rita", Birthday(3, 30, 1995)),
                    Relative("x", Relation.PARENT, "Rosa", Birthday(9, 1)),
                ),
                interests = listOf("chess", "old"),
                interestsAddedAt = mapOf("chess" to at(2026, 3, 5), "old" to at(2026, 2, 1)),
            )
        }
        val hangouts = listOf(Hangout("h", listOf(ana.id), at(2026, 3, 1)))
        val topics = OfflineTopics.build(ana, hangouts, today, zone)
        assertEquals(listOf(TopicHint.RelativeBirthday("Rita", Relation.SIBLING, LocalDate(2026, 3, 30), 31), TopicHint.NewInterest("chess")), topics)
        // Never met: the fallback window decides.
        assertEquals(
            listOf(TopicHint.NewInterest("chess"), TopicHint.NewInterest("old")),
            OfflineTopics.build(ana.copy(relatives = emptyList()), emptyList(), today, zone),
        )
    }

    // --- Detail extras -------------------------------------------------------------------------------------------------

    @Test
    fun customFieldLabels_suggestTheMostUsedFirst_caseInsensitively() {
        val friends = listOf(
            friend("a") { copy(customFields = listOf(CustomField("1", "Shoe size", "38"), CustomField("2", "Club", "FCP"))) },
            friend("b") { copy(customFields = listOf(CustomField("3", "shoe size", "41"), CustomField("4", " ", "x"))) },
            friend("c") { copy(customFields = listOf(CustomField("5", "Allergies", "cats"))) },
        )
        assertEquals(listOf("Shoe size", "Allergies", "Club"), TestGraph(emptyList()).observeFriendDetail.customFieldLabels(friends))
    }

    @Test
    fun theDetail_listsOthers_andLabelsFromOtherFriendsOnly() = runTest {
        val g = TestGraph(
            listOf(
                friend("ana") { copy(customFields = listOf(CustomField("1", "Mine", "x"))) },
                friend("rui") { copy(customFields = listOf(CustomField("2", "Theirs", "y"))) },
                friend("bea"),
            ),
        )
        val detail = g.observeFriendDetail(FriendId("ana")).first()!!
        assertEquals(listOf("Bea", "Rui"), detail.others.map { it.name })
        assertEquals(listOf("Theirs"), detail.customFieldLabels)
    }
}
