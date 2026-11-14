package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.CardLink
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.model.friendIds
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.FamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.LinkDraft
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.PersonLinks
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.isBuiltIn
import com.neteinstein.goodcompany.core.domain.usecase.of
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonLinksTest {
    private val rui = FriendId("rui")
    private val ana = FriendId("ana")
    private val today = LocalDate(2026, 3, 10)

    private fun link(
        id: String,
        from: FriendId,
        to: FriendId,
        type: LinkType,
        startedOn: LocalDate? = null,
        endedOn: LocalDate? = null,
        remind: Boolean = false,
    ) = PersonLink(id, from, to, type, startedOn, endedOn, remindAnniversary = remind)

    // --- Reciprocity and wording ---

    @Test
    fun everyTypeHasAnInverse_andTheInverseOfTheInverseIsItself() {
        LinkType.entries.forEach { assertEquals(it, it.inverse.inverse) }
        assertEquals(LinkType.CHILD, LinkType.PARENT.inverse)
        assertEquals(LinkType.GRANDCHILD, LinkType.GRANDPARENT.inverse)
        assertEquals(LinkType.NIECE_NEPHEW, LinkType.AUNT_UNCLE.inverse)
        assertEquals(LinkType.DATING, LinkType.DATING.inverse)
        assertEquals(LinkType.SIBLING, LinkType.SIBLING.inverse)
        // The inverse never changes the category.
        LinkType.entries.forEach { assertEquals(it.category, it.inverse.category) }
    }

    @Test
    fun aLinkIsStoredOnce_andReadFromBothSides() {
        // "Leonor is Rui's daughter" ↔ "Rui is Leonor's parent".
        val leonor = FriendId("leonor")
        val l = link("l1", rui, leonor, LinkType.CHILD)
        assertEquals(LinkType.CHILD, l.roleSeenBy(rui))
        assertEquals(LinkType.PARENT, l.roleSeenBy(leonor))
        assertNull(l.roleSeenBy(ana))
        assertEquals(leonor, l.otherThan(rui))
        assertEquals(rui, l.otherThan(leonor))
        assertNull(l.otherThan(ana))
        assertTrue(l.involves(leonor) && !l.involves(ana))

        val friends = listOf(friend("rui") { copy(pronouns = Pronouns.HE) }, friend("leonor") { copy(pronouns = Pronouns.SHE) })
        val onRui = PersonLinks.of(rui, listOf(l), friends).single()
        assertEquals(leonor, onRui.person.id)
        assertEquals(LinkType.CHILD, onRui.role)
        val onLeonor = PersonLinks.of(leonor, listOf(l), friends).single()
        assertEquals(rui, onLeonor.person.id)
        assertEquals(LinkType.PARENT, onLeonor.role)
        assertEquals(Pronouns.HE, onLeonor.person.pronouns) // so the page says "Father"
    }

    @Test
    fun severalPartners_partnersFirst_thenExes_family_andOthers() {
        val friends = listOf("rui", "ana", "carla", "leonor", "pedro", "sofia").map { friend(it) }
        val links = listOf(
            link("colleague", rui, FriendId("pedro"), LinkType.COLLEAGUE),
            link("daughter", rui, FriendId("leonor"), LinkType.CHILD),
            link("ex", FriendId("carla"), rui, LinkType.EX, LocalDate(2012, 1, 1), LocalDate(2017, 1, 1)),
            link("girlfriend", rui, ana, LinkType.DATING, LocalDate(2019, 6, 1)),
            link("partner", FriendId("sofia"), rui, LinkType.PARTNER),
            link("unrelated", ana, FriendId("pedro"), LinkType.FRIEND),
        )
        val page = PersonLinks.of(rui, links, friends)
        assertEquals(listOf("ana", "sofia", "carla", "leonor", "pedro"), page.map { it.person.id.value })
        assertEquals(2, page.count { it.link.isCurrentRomance })
        // A link to someone who isn't a friend (deleted) is left out.
        assertEquals(1, PersonLinks.of(ana, links, friends.filterNot { it.id.value == "pedro" }).size)
    }

    @Test
    fun normalized_keepsDatesWhereTheyBelong() {
        val ex = PersonLinks.normalized(link("x", rui, ana, LinkType.EX, LocalDate(2012, 5, 1), LocalDate(2017, 9, 1), remind = true))
        assertEquals(LocalDate(2017, 9, 1), ex.endedOn)
        assertEquals(LocalDate(2012, 5, 1), ex.startedOn)
        assertFalse(ex.remindAnniversary) // an ex's anniversary isn't remembered

        val sister = PersonLinks.normalized(link("s", rui, ana, LinkType.SIBLING, LocalDate(2000, 1, 1), LocalDate(2001, 1, 1), remind = true))
        assertNull(sister.startedOn)
        assertNull(sister.endedOn)
        assertFalse(sister.remindAnniversary)

        val wife = PersonLinks.normalized(link("w", rui, ana, LinkType.SPOUSE, LocalDate(2015, 7, 4), LocalDate(2020, 1, 1), remind = true))
        assertNull(wife.endedOn)
        assertTrue(wife.remindAnniversary)

        val noDate = PersonLinks.normalized(link("p", rui, ana, LinkType.PARTNER, remind = true))
        assertFalse(noDate.remindAnniversary)
    }

    @Test
    fun linkTypesGroupByCategory() {
        assertEquals(9, LinkType.of(LinkCategory.FAMILY).size)
        assertEquals(listOf(LinkType.DATING, LinkType.SPOUSE, LinkType.PARTNER, LinkType.EX), LinkType.of(LinkCategory.ROMANTIC))
        assertEquals(listOf(LinkType.FRIEND, LinkType.COLLEAGUE), LinkType.of(LinkCategory.OTHER))
        assertNull(LinkType.parse("COWORKER"))
        assertEquals(LinkType.EX, LinkType.parse("EX"))
        assertNull(Pronouns.parse(null))
    }

    // --- Saving ---

    @Test
    fun saveLink_isOnePerPair_whicheverWayRound() = runTest {
        val g = TestGraph(listOf(friend("rui"), friend("ana")))
        val first = g.saveLink(LinkDraft(rui, ana, LinkType.DATING, startedOn = LocalDate(2019, 6, 1)))
        assertEquals(g.clock.now(), first.addedAt)
        // From Ana's page: they married. Same link, now stored from Ana's side.
        val second = g.saveLink(LinkDraft(ana, rui, LinkType.SPOUSE, startedOn = LocalDate(2023, 9, 9)))
        val all = g.linkRepo.links.first()
        assertEquals(1, all.size)
        assertEquals(first.id, second.id)
        assertEquals(first.addedAt, second.addedAt)
        assertEquals(LinkType.SPOUSE, all.single().roleSeenBy(rui))

        assertFailsWith<IllegalArgumentException> { g.saveLink(LinkDraft(rui, rui, LinkType.FRIEND)) }
    }

    @Test
    fun editingALinkOntoAnotherPair_replacesThatPairsLink() = runTest {
        val g = TestGraph(listOf(friend("rui"), friend("ana"), friend("carla")))
        val toAna = g.saveLink(LinkDraft(rui, ana, LinkType.FRIEND))
        val toCarla = g.saveLink(LinkDraft(rui, FriendId("carla"), LinkType.COLLEAGUE))
        g.saveLink(LinkDraft(rui, FriendId("carla"), LinkType.FRIEND, id = toAna.id))
        val all = g.linkRepo.links.first()
        assertEquals(listOf(toAna.id), all.map { it.id })
        assertTrue(all.none { it.id == toCarla.id })
    }

    @Test
    fun deleteRestore_andLinksGoWithAPerson() = runTest {
        val g = TestGraph(listOf(friend("rui"), friend("ana")))
        val l = g.saveLink(LinkDraft(rui, ana, LinkType.SIBLING))
        assertEquals(l, g.deleteLink(l.id))
        assertTrue(g.linkRepo.links.first().isEmpty())
        assertNull(g.deleteLink("nope"))
        g.restoreLink(l)
        assertEquals(1, g.linkRepo.links.first().size)
        g.deleteFriend(ana)
        assertTrue(g.linkRepo.links.first().isEmpty())
        assertTrue(g.observePersonLinks(rui).first().isEmpty())
    }

    @Test
    fun relationToMe_andPronouns() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        g.setRelationToMe(ana, LinkType.SIBLING, Pronouns.SHE)
        val f = g.friendRepo.get(ana)!!
        assertEquals(LinkType.SIBLING, f.relationToMe)
        assertTrue(f.isFamily)
        g.setPronouns(ana, null)
        assertNull(g.friendRepo.get(ana)!!.pronouns)
        g.setRelationToMe(ana, LinkType.DATING, null)
        assertFalse(g.friendRepo.get(ana)!!.isFamily) // a partner isn't in the Family circle
        g.setRelationToMe(FriendId("ghost"), LinkType.SIBLING, null) // nothing to do
    }

    @Test
    fun addRelativeAsAPerson_createsAFriendAndALink_andTakesTheRelativeOff() = runTest {
        val mother = Relative("r1", Relation.PARENT, " Graça Costa ", likes = listOf("roses"), note = "lives in Braga", notifyBirthday = true)
        val dog = Relative("r2", Relation.PET, "Bolinha")
        val g = TestGraph(listOf(friend("rui") { copy(relatives = listOf(mother, dog)) }))
        val id = g.addRelativeAsPerson(rui, "r1")!!
        val person = g.friendRepo.get(id)!!
        assertEquals("Graça Costa", person.name)
        assertEquals(listOf("roses"), person.likes)
        assertEquals("lives in Braga", person.notes)
        assertTrue(person.notifyBirthday)
        val link = g.linkRepo.links.first().single()
        assertEquals(LinkType.PARENT, link.roleSeenBy(rui))
        assertEquals(LinkType.CHILD, link.roleSeenBy(id))
        assertEquals(listOf("r2"), g.friendRepo.get(rui)!!.relatives.map { it.id })

        assertNull(g.addRelativeAsPerson(rui, "r2")) // a pet stays a relative
        assertNull(g.addRelativeAsPerson(rui, "missing"))
        assertNull(g.addRelativeAsPerson(FriendId("ghost"), "r1"))
        assertEquals(LinkType.PARTNER, PersonLinks.typeFor(Relation.PARTNER))
        assertEquals(LinkType.SIBLING, PersonLinks.typeFor(Relation.SIBLING))
        assertNull(PersonLinks.typeFor(Relation.OTHER))
    }

    // --- Anniversaries ---

    @Test
    fun nextAnniversary_andYears() {
        val l = link("l", rui, ana, LinkType.SPOUSE, LocalDate(2016, 2, 29))
        assertEquals(LocalDate(2027, 2, 28), l.nextAnniversary(today)) // not a leap year
        assertEquals(LocalDate(2028, 2, 29), l.nextAnniversary(LocalDate(2028, 1, 1)))
        assertEquals(11, l.yearsOn(LocalDate(2027, 2, 28)))
        assertEquals(LocalDate(2026, 3, 10), link("t", rui, ana, LinkType.DATING, LocalDate(2020, 3, 10)).nextAnniversary(today))
        assertNull(link("n", rui, ana, LinkType.DATING).nextAnniversary(today))
        assertNull(link("n", rui, ana, LinkType.DATING).yearsOn(today))
    }

    @Test
    fun anAnniversaryReminder_comesFromStartedOn_inTheAgendaAndNotifications() {
        val friends = listOf(friend("rui"), friend("ana"), friend("carla"))
        val links = listOf(
            link("couple", rui, ana, LinkType.DATING, LocalDate(2019, 3, 20), remind = true),
            link("quiet", rui, FriendId("carla"), LinkType.PARTNER, LocalDate(2019, 3, 21)), // reminder off
            link("ex", ana, FriendId("carla"), LinkType.EX, LocalDate(2010, 3, 22), LocalDate(2012, 1, 1), remind = true),
            link("gone", ana, FriendId("nobody"), LinkType.SPOUSE, LocalDate(2010, 3, 23), remind = true),
        )
        val items = AgendaBuilder.build(friends, emptyList(), emptyList(), today, TimeZone.UTC, links = links)
        val anniversary = assertIs<AgendaItem.Anniversary>(items.single())
        assertEquals(LocalDate(2026, 3, 20), anniversary.date)
        assertEquals(7, anniversary.years)
        assertEquals(setOf(rui, ana), anniversary.friendIds)
        assertEquals("anniversary-couple-2026-03-20", anniversary.key)

        val planned = NotificationPlanner.plan(friends, emptyList(), emptyList(), at(2026, 3, 10), TimeZone.UTC, 9, links = links)
        val n = planned.single()
        assertEquals("anniversary:couple:2026-03-20", n.id)
        assertEquals(NotificationContent.Anniversary("Rui", "Ana", 7), n.content)
    }

    @Test
    fun observeAgenda_includesAnniversaries() = runTest {
        val g = TestGraph(listOf(friend("rui"), friend("ana")))
        g.saveLink(LinkDraft(rui, ana, LinkType.SPOUSE, startedOn = LocalDate(2015, 3, 12), remindAnniversary = true))
        assertIs<AgendaItem.Anniversary>(g.observeAgenda().first().single())
    }

    // --- The Family circle ---

    @Test
    fun familyCircle_isEveryoneWithAFamilyRelationToMe() = runTest {
        val g = TestGraph(
            listOf(
                friend("rui") { copy(relationToMe = LinkType.SIBLING) },
                friend("gra", "Graça") { copy(relationToMe = LinkType.PARENT) },
                friend("ana") { copy(relationToMe = LinkType.DATING) },
                friend("pedro"),
            ),
        )
        g.groupRepo.upsert(FriendGroup("choir", "Choir", memberIds = listOf(FriendId("pedro"))))
        val circles = g.circles.groups.first()
        val family = circles.first()
        assertTrue(family.isBuiltIn)
        assertEquals(FamilyCircle.ID, family.id)
        assertEquals("Family", family.name)
        assertEquals(listOf("gra", "rui"), family.memberIds.map { it.value }) // A–Z
        assertEquals(listOf("choir"), circles.drop(1).map { it.id })

        // It can't be edited or deleted, and it isn't stored.
        g.circles.upsert(family.copy(name = "Fam"))
        g.circles.delete(FamilyCircle.ID)
        assertEquals("Family", g.circles.groups.first().first().name)
        assertEquals(listOf("choir"), g.groupRepo.groups.value.map { it.id })
        g.circles.delete("choir")
        assertEquals(listOf(FamilyCircle.ID), g.circles.groups.first().map { it.id })

        // Shown on a member's page and in the circle list.
        assertTrue(g.observeFriendDetail(FriendId("rui")).first()!!.groups.any { it.isBuiltIn })
        assertEquals(FamilyCircle.ID, g.observeGroups().first().first().group.id)
        // Nothing can be shared onto it (it has no stored row to keep an attachment on).
        assertTrue(g.observeShareTargets().first().circles.none { it.isBuiltIn })
    }

    @Test
    fun noFamily_noFamilyCircle() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        assertTrue(g.circles.groups.first().isEmpty())
        assertNull(FamilyCircle.of(emptyList(), "Family"))
    }

    // --- Contacts and backups ---

    @Test
    fun contactCard_carriesLinksFromTheFriendsSide() {
        val r = friend("rui") { copy(contactId = "c1", pronouns = Pronouns.HE) }
        val a = friend("ana", "Ana Silva") { copy(pronouns = Pronouns.SHE) }
        val leonor = friend("leonor", "Leonor")
        val people = listOf(r, a, leonor).associateBy { it.id }
        val links = listOf(
            link("1", rui, ana, LinkType.DATING),
            link("2", FriendId("leonor"), rui, LinkType.PARENT), // Rui is Leonor's parent → Leonor is his child
            link("3", ana, FriendId("ghost"), LinkType.FRIEND),
        )
        val card = ContactCard.from(r, links, people)
        // Leonor is Rui's kid: written once, as a child row, not also as a relation.
        assertEquals(listOf(CardLink("Ana Silva", LinkType.DATING, Pronouns.SHE)), card.links)
        assertEquals(listOf("Leonor"), card.kids.map { it.name })
        assertTrue(card.hasExtras)
        assertEquals(listOf(CardLink("Rui", LinkType.DATING, Pronouns.HE)), ContactCard.from(a, links, people).links)
        assertTrue(ContactCard.from(r).links.isEmpty())
    }

    @Test
    fun backupRoundTrip_keepsLinksAndRelationToMe() = runTest {
        val g = TestGraph(listOf(friend("rui") { copy(relationToMe = LinkType.SIBLING, pronouns = Pronouns.HE) }, friend("ana")))
        g.saveLink(LinkDraft(rui, ana, LinkType.DATING, startedOn = LocalDate(2019, 6, 1), remindAnniversary = true))
        val json = g.exportBackup()

        val other = TestGraph()
        assertIs<RestoreResult.Restored>(other.restoreBackup(json))
        assertEquals(g.linkRepo.links.first(), other.linkRepo.links.first())
        val restored = other.friendRepo.get(rui)!!
        assertEquals(LinkType.SIBLING, restored.relationToMe)
        assertEquals(Pronouns.HE, restored.pronouns)
    }
}
