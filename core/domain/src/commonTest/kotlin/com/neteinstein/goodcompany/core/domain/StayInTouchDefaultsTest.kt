package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.KidsAsPeople
import com.neteinstein.goodcompany.core.domain.usecase.LinkDraft
import com.neteinstein.goodcompany.core.domain.usecase.StayInTouch
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Issue #132: "Stay in touch" starts off for people added as a reference, and on for people added as friends. */
class StayInTouchDefaultsTest {
    private val g = TestGraph()
    private val rui = friend("rui")

    private suspend fun tracks(id: FriendId) = g.friendRepo.get(id)!!.tracksKeepInTouch

    @Test
    fun aKidAddedToSomeoneStartsOff() = runTest {
        g.friendRepo.upsert(rui)
        val kid = g.addKid(rui.id, KidDraft("Leo"))!!
        assertFalse(tracks(kid))
        assertTrue(tracks(rui.id), "the parent is unchanged")
    }

    @Test
    fun yourOwnKidAddedToYourPartnerStartsOffToo() = runTest {
        val partner = friend("ana") { copy(relationToMe = LinkType.SPOUSE, tracksKeepInTouch = false) }
        g.friendRepo.upsert(partner)
        assertFalse(tracks(g.addKid(partner.id, KidDraft("Rita", showInMyList = true))!!))
    }

    @Test
    fun aRelativeAddedAsAPersonStartsOff() = runTest {
        Relation.entries.filter { it != Relation.PET && it != Relation.OTHER }.forEach { relation ->
            val owner = friend("p-$relation") { copy(relatives = listOf(Relative("r", relation, "Rel $relation"))) }
            g.friendRepo.upsert(owner)
            val person = g.addRelativeAsPerson(owner.id, "r")!!
            assertFalse(tracks(person), "$relation")
            assertTrue(tracks(owner.id))
        }
    }

    @Test
    fun oldKidsBecomingPeopleStartOff() {
        val parent = friend("rui") { copy(kids = listOf(Kid("leo", "Leo"))) }
        val (people, _) = KidsAsPeople.convert(listOf(parent), emptyList())
        assertFalse(people.single { it.id == FriendId("leo") }.tracksKeepInTouch)
        assertTrue(people.single { it.id == parent.id }.tracksKeepInTouch)
    }

    @Test
    fun peopleAddedAsFriendsStartOn() = runTest {
        assertTrue(tracks(g.addFriend("Marta")))
        val picked = g.importPickedContacts(listOf(ContactRecord("c1", "Zé", emptyList(), null)))
        assertTrue(tracks(picked.picked.single()))
        assertTrue(ContactMerger.merge(emptyList(), listOf(ContactRecord("c2", "Lia", emptyList(), null))).single().tracksKeepInTouch)
    }

    @Test
    fun linkingAnExistingFriendNeverChangesTheirTracking() = runTest {
        val ana = friend("ana")
        val kid = friend("kid") { copy(tracksKeepInTouch = false) }
        listOf(rui, ana, kid).forEach { g.friendRepo.upsert(it) }
        g.saveLink(LinkDraft(rui.id, ana.id, LinkType.SPOUSE))
        g.saveLink(LinkDraft(rui.id, kid.id, LinkType.FRIEND))
        assertTrue(tracks(ana.id))
        assertFalse(tracks(kid.id))
    }

    @Test
    fun aContactSyncOrRepickKeepsTrackingAsItWas() = runTest {
        val leo = friend("leo") { copy(name = "Leo", contactId = "c9", tracksKeepInTouch = false) }
        val merged = ContactMerger.merge(listOf(leo), listOf(ContactRecord("c9", "Leo Silva", emptyList(), null)))
        assertFalse(merged.single().tracksKeepInTouch)
        g.friendRepo.upsert(leo)
        g.importPickedContacts(listOf(ContactRecord("c9", "Leo", listOf("+351 900"), null)))
        assertFalse(tracks(leo.id))
    }

    @Test
    fun becomingYourKidOrPartnerTurnsItOff_parentsAndSiblingsKeepIt() = runTest {
        LinkType.entries.forEach { relation ->
            val person = friend("x-$relation")
            g.friendRepo.upsert(person)
            g.setRelationToMe(person.id, relation, null)
            assertEquals(relation !in StayInTouch.offForRelationToMe, tracks(person.id), "$relation")
        }
        assertEquals(
            setOf(LinkType.CHILD, LinkType.STEP_CHILD, LinkType.DATING, LinkType.SPOUSE, LinkType.PARTNER),
            StayInTouch.offForRelationToMe,
        )
    }

    @Test
    fun switchedBackOn_itStaysOnWhileTheyStayYourKidOrPartner() = runTest {
        val ana = friend("ana") { copy(relationToMe = LinkType.DATING, tracksKeepInTouch = true) }
        g.friendRepo.upsert(ana)
        g.setRelationToMe(ana.id, LinkType.SPOUSE, null) // dating → married: still a partner
        assertTrue(tracks(ana.id))
        g.setRelationToMe(ana.id, null, null) // cleared: tracking is left alone
        assertTrue(tracks(ana.id))
    }

    @Test
    fun undoPutsTheEarlierTrackingBack() = runTest {
        val leo = friend("leo")
        g.friendRepo.upsert(leo)
        g.setRelationToMe(leo.id, LinkType.CHILD, null)
        assertFalse(tracks(leo.id))
        g.setRelationToMe(leo.id, null, null, tracksKeepInTouch = true)
        assertTrue(tracks(leo.id))
        assertEquals(null, g.friendRepo.get(leo.id)!!.relationToMe)
    }

    @Test
    fun confirmingKinshipAsYourKidTurnsItOff_asANieceKeepsIt() = runTest {
        val a = friend("a")
        val b = friend("b")
        listOf(a, b).forEach { g.friendRepo.upsert(it) }
        g.confirmKinship(a.id, LinkType.STEP_CHILD)
        g.confirmKinship(b.id, LinkType.NIECE_NEPHEW)
        assertFalse(tracks(a.id))
        assertTrue(tracks(b.id))
    }
}
