package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.usecase.FamilyAround
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FamilyAroundTest {
    private val ana = friend("ana", "Ana Silva")

    /** [person] is [role] to Ana, stored from Ana's side. */
    private fun linked(id: String, role: LinkType, endedOn: LocalDate? = null) = LinkedPerson(
        link = PersonLink("l-$id", ana.id, FriendId(id), role, endedOn = endedOn),
        person = friend(id, "${id.replaceFirstChar { it.uppercase() }} Silva"),
        role = role,
    )

    @Test
    fun noFamily_isEmpty() {
        val around = FamilyAround.of(ana, listOf(linked("bia", LinkType.SIBLING), linked("joao", LinkType.FRIEND)))
        assertTrue(around.isEmpty)
        assertEquals(0, around.moreKids)
    }

    @Test
    fun splitsParentsPartnersAndKids_andLeavesSiblingsInTheFamilyCard() {
        val around = FamilyAround.of(
            ana,
            listOf(
                linked("mum", LinkType.PARENT),
                linked("rui", LinkType.SPOUSE),
                linked("tomas", LinkType.CHILD),
                linked("bia", LinkType.SIBLING),
            ),
        )
        assertEquals(listOf("Mum"), around.parents.map { it.firstName })
        assertEquals(listOf("Rui"), around.partners.map { it.firstName })
        assertEquals(listOf("Tomas"), around.kids.map { it.firstName })
        assertEquals(FriendId("tomas"), around.kids.single().person?.id)
        assertFalse(around.isEmpty)
    }

    @Test
    fun exPartners_andEndedRomances_areLeftOut() {
        val around = FamilyAround.of(
            ana,
            listOf(
                linked("old", LinkType.EX),
                linked("gone", LinkType.DATING, endedOn = LocalDate(2020, 1, 1)),
                linked("rui", LinkType.PARTNER),
            ),
        )
        assertEquals(listOf("Rui"), around.partners.map { it.firstName })
    }

    @Test
    fun stepFamily_sitsWithParentsAndKids() {
        val around = FamilyAround.of(ana, listOf(linked("step", LinkType.STEP_PARENT), linked("leo", LinkType.STEP_CHILD)))
        assertEquals(LinkType.STEP_PARENT, around.parents.single().role)
        assertEquals(LinkType.STEP_CHILD, around.kids.single().role)
    }

    @Test
    fun relativesNotYetPeople_comeAfterThePeople() {
        val withRelatives = ana.copy(
            relatives = listOf(
                Relative("r1", Relation.PARENT, "Dad Silva"),
                Relative("r2", Relation.PARTNER, "Rita"),
                Relative("r3", Relation.SIBLING, "Bia"),
                Relative("r4", Relation.PET, "Bolinha"),
            ),
            kids = listOf(Kid("k1", "Inês", birthday = Birthday(3, 4, 2022))),
        )
        val around = FamilyAround.of(withRelatives, listOf(linked("mum", LinkType.PARENT)))
        assertEquals(listOf("Mum", "Dad"), around.parents.map { it.firstName })
        assertNull(around.parents[1].person)
        assertEquals("r1", around.parents[1].relativeId)
        assertEquals(listOf("Rita"), around.partners.map { it.name })
        assertEquals(LinkType.PARTNER, around.partners.single().role)
        assertEquals(listOf("Inês"), around.kids.map { it.name })
        assertEquals(Birthday(3, 4, 2022), around.kids.single().birthday)
    }

    @Test
    fun upToFourKids_thenPlusN() {
        val kids = (1..6).map { linked("kid$it", LinkType.CHILD) }
        val around = FamilyAround.of(ana, kids)
        assertEquals(4, around.kids.size)
        assertEquals(2, around.moreKids)
        assertEquals(listOf("kid1", "kid2", "kid3", "kid4"), around.kids.map { it.person!!.id.value })

        val four = FamilyAround.of(ana, kids.take(4))
        assertEquals(0, four.moreKids)
    }
}
