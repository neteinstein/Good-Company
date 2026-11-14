package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.CardKid
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.KidsAsPeople
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.domain.usecase.StorySlide
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Kids are people (F9, issue #40): a kid is a friend linked to their parent as their child. */
class KidsAsPeopleTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")

    // --- Converting old kids (backups, the old JSON store, sample data) ---

    @Test
    fun convert_makesEachKidAPerson_keepingIdsLikesBirthdaysAndTheirGifts() {
        val sister = friend("ana") {
            copy(
                relationToMe = LinkType.SIBLING,
                notifyBirthday = true,
                kids = listOf(Kid("leo", "Leo", Birthday(5, 1, 2018), listOf("dinosaurs"), at(2026, 1, 2))),
                gifts = listOf(Gift("g1", "Lego", forKidId = "leo"), Gift("g2", "Vinyl"), Gift("g3", "Lost", forKidId = "nobody")),
            )
        }
        // Rui is a friend, and his kid's id is already someone else's.
        val friendWithKid = friend("rui") { copy(kids = listOf(Kid("dup", "Mia"))) }
        val (people, links) = KidsAsPeople.convert(listOf(sister, friendWithKid, friend("dup")), emptyList())

        val byId = people.associateBy { it.id.value }
        assertEquals(listOf("ana", "rui", "dup", "leo", "rui-dup"), people.map { it.id.value })
        val leo = byId.getValue("leo")
        assertEquals(Birthday(5, 1, 2018), leo.birthday)
        assertEquals(listOf("dinosaurs"), leo.likes)
        assertTrue(leo.notifyBirthday) // the parent's reminder
        assertTrue(leo.showInMyList) // a family member's kid
        assertEquals(listOf(Gift("g1", "Lego")), leo.gifts)
        assertFalse(byId.getValue("rui-dup").showInMyList) // a friend's kid
        assertTrue(people.all { it.kids.isEmpty() })
        assertEquals(listOf("g2", "g3"), byId.getValue("ana").gifts.map { it.id })
        assertTrue(byId.getValue("ana").gifts.all { it.forKidId == null })
        assertEquals(
            listOf(
                PersonLink("kid-link-leo", ana, FriendId("leo"), LinkType.CHILD, addedAt = at(2026, 1, 2)),
                PersonLink("kid-link-rui-dup", rui, FriendId("rui-dup"), LinkType.CHILD),
            ),
            links,
        )
    }

    @Test
    fun convert_leavesDataWithoutOldKidsAlone() {
        val friends = listOf(friend("ana"))
        assertSame(friends, KidsAsPeople.convert(friends, emptyList()).first)
        val backup = Backup(friends = friends)
        assertSame(backup, KidsAsPeople.convert(backup))
    }

    @Test
    fun anOldBackup_restoresItsKidsAsPeople() = runTest {
        val json = """
            {"format":"good-company-backup","version":1,"exportedAt":"2026-03-01T10:00:00Z","friends":[
              {"id":"ana","name":"Ana","notifyBirthday":true,"kids":[{"id":"k","name":"Leo","likes":["lego"]}],
               "gifts":[{"id":"g","title":"Lego","forKidId":"k"}]}
            ]}
        """.trimIndent()
        val g = TestGraph()
        assertIs<RestoreResult.Restored>(g.restoreBackup(json))
        val people = g.friendRepo.friends.value.associateBy { it.id.value }
        assertEquals(setOf("ana", "k"), people.keys)
        assertEquals(listOf("lego"), people.getValue("k").likes)
        assertEquals(listOf("Lego"), people.getValue("k").gifts.map { it.title })
        assertTrue(people.getValue("ana").gifts.isEmpty())
        assertEquals(LinkType.CHILD, g.linkRepo.links.first().single().roleSeenBy(ana))
    }

    @Test
    fun aNewBackup_carriesShowInMyListOnlyWhenOff() {
        val json = Json { encodeDefaults = false }
        assertFalse("showInMyList" in json.encodeToString(friend("ana")))
        assertTrue("\"showInMyList\":false" in json.encodeToString(friend("leo") { copy(showInMyList = false) }))
    }

    @Test
    fun sampleKids_arePeopleLinkedToTheirParents() = runTest {
        val g = TestGraph()
        g.loadSampleData()
        val people = g.friendRepo.friends.value.associateBy { it.id }
        val leo = people.getValue(FriendId(SampleData.PREFIX + "leo"))
        assertFalse(leo.showInMyList)
        val parent = KidsAsPeople.parentsOf(leo.id, g.linkRepo.links.first(), people).single()
        assertTrue(parent.gifts.none { it.forKidId != null })
        assertTrue(leo.gifts.isNotEmpty()) // the dino set moved to Leo's page
    }

    // --- Adding a kid ---

    @Test
    fun addingAKid_createsAPersonWithTheirOwnReminder_linkedToTheParent() = runTest {
        val g = TestGraph(listOf(friend("rui", "Rui Costa")))
        val id = g.addKid(rui, KidDraft(" Leonor Costa ", Birthday(3, 2, 2019), likes = listOf("drawing", " ", "drawing")))!!
        val kid = g.friendRepo.get(id)!!
        assertEquals("Leonor Costa", kid.name)
        assertEquals(listOf("drawing"), kid.likes)
        assertTrue(kid.notifyBirthday)
        assertFalse(kid.showInMyList) // off unless the sheet turned it on
        assertEquals(listOf("Leonor Costa"), g.observeFriendDetail(rui).first()!!.kids.map { it.person.name })
        assertEquals(listOf("Rui Costa"), g.observeFriendDetail(id).first()!!.parents.map { it.name })

        assertNull(g.addKid(rui, KidDraft("  ")))
        assertNull(g.addKid(FriendId("gone"), KidDraft("Leo")))
    }

    @Test
    fun showInMyList_isOnForFamily_andCanBeSwitched() = runTest {
        val brother = friend("rui") { copy(relationToMe = LinkType.SIBLING) }
        val friendOfMine = friend("ana")
        assertTrue(KidsAsPeople.showsByDefault(brother))
        assertFalse(KidsAsPeople.showsByDefault(friendOfMine))

        val g = TestGraph(listOf(friendOfMine))
        val id = g.addKid(ana, KidDraft("Vasco"))!!
        assertFalse(g.friendRepo.get(id)!!.showInMyList)
        g.setShowInMyList(id, true)
        assertTrue(g.friendRepo.get(id)!!.showInMyList)
    }

    @Test
    fun theFriendsList_knowsWhoseKidSomeoneIs() = runTest {
        val g = TestGraph(listOf(friend("ana", "Ana Silva")))
        val id = g.addKid(ana, KidDraft("Vasco"))!!
        val summaries = g.observeFriends().first().associateBy { it.friend.id }
        assertEquals(listOf("Ana Silva"), summaries.getValue(id).parents.map { it.name })
        assertTrue(summaries.getValue(ana).parents.isEmpty())
    }

    // --- Birthdays, agenda, story and contact card off the links ---

    @Test
    fun aKidsBirthday_isOnTheAgendaWithTheirParent_andPingsWithTheirOwnReminder() = runTest {
        // Ana's own reminder is off, so only the kid's own reminder brings their birthday up.
        val g = TestGraph(listOf(friend("ana", "Ana Silva") { copy(notifyBirthday = false) }))
        val id = g.addKid(ana, KidDraft("Vasco", Birthday(3, 20, 2020), likes = listOf("lego")))!!
        val item = g.observeAgenda().first().filterIsInstance<AgendaItem.KidBirthday>().single()
        assertEquals("Ana Silva", item.parent.name)
        assertEquals(id, item.kid.id)
        assertEquals(6, item.turning)

        val planned = NotificationPlanner.plan(
            g.friendRepo.friends.value,
            emptyList(),
            emptyList(),
            at(2026, 3, 10, 12),
            TimeZone.UTC,
            reminderHour = 9,
            links = g.linkRepo.links.first(),
        )
        assertEquals(
            NotificationContent.KidBirthday("Vasco", "Ana Silva", 6, listOf("lego")),
            planned.map {
                it.content
            }.filterIsInstance<NotificationContent.KidBirthday>().single(),
        )

        // Their own reminder off and a parent you don't follow: no ping and no agenda row.
        g.friendRepo.upsert(g.friendRepo.get(id)!!.copy(notifyBirthday = false))
        assertTrue(g.observeAgenda().first().none { it is AgendaItem.KidBirthday })
    }

    @Test
    fun theCatchUpStory_asksAboutTheKids() = runTest {
        val g = TestGraph(listOf(friend("ana", "Ana Silva")))
        g.addKid(ana, KidDraft("Vasco", Birthday(3, 25, 2020), likes = listOf("lego")))
        val story = g.observeCatchUpStory(ana).first()!!
        val kids = assertIs<StorySlide.Kids>(story.slides.single { it is StorySlide.Kids }).kids
        assertEquals(listOf("Vasco"), kids.map { it.name })
        assertTrue(kids.single().birthdaySoon)
        // Added today, so Vasco is also something new to ask about.
        assertTrue("Vasco" in assertIs<StorySlide.Outro>(story.slides.last()).askAbout)
    }

    @Test
    fun theContactCard_writesKidsFromTheLinks_once() {
        val parent = friend("ana")
        val kid = friend("leo", "Leo") { copy(birthday = Birthday(5, 1, 2018)) }
        val link = PersonLink("l", ana, kid.id, LinkType.CHILD)
        val card = ContactCard.from(parent, listOf(link), mapOf(kid.id to kid))
        assertEquals(listOf(CardKid("Leo", Birthday(5, 1, 2018))), card.kids)
        assertTrue(card.links.isEmpty())
        // On the kid's own card the parent is a link as usual.
        assertEquals(LinkType.PARENT, ContactCard.from(kid, listOf(link), mapOf(ana to parent)).links.single().role)
    }
}
