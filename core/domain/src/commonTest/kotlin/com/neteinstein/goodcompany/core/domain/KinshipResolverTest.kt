package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkType.AUNT_UNCLE
import com.neteinstein.goodcompany.core.domain.model.LinkType.CHILD
import com.neteinstein.goodcompany.core.domain.model.LinkType.COLLEAGUE
import com.neteinstein.goodcompany.core.domain.model.LinkType.COUSIN
import com.neteinstein.goodcompany.core.domain.model.LinkType.EX
import com.neteinstein.goodcompany.core.domain.model.LinkType.FRIEND
import com.neteinstein.goodcompany.core.domain.model.LinkType.GRANDCHILD
import com.neteinstein.goodcompany.core.domain.model.LinkType.GRANDPARENT
import com.neteinstein.goodcompany.core.domain.model.LinkType.HALF_SIBLING
import com.neteinstein.goodcompany.core.domain.model.LinkType.IN_LAW
import com.neteinstein.goodcompany.core.domain.model.LinkType.NIECE_NEPHEW
import com.neteinstein.goodcompany.core.domain.model.LinkType.PARENT
import com.neteinstein.goodcompany.core.domain.model.LinkType.PARTNER
import com.neteinstein.goodcompany.core.domain.model.LinkType.SIBLING
import com.neteinstein.goodcompany.core.domain.model.LinkType.SPOUSE
import com.neteinstein.goodcompany.core.domain.model.LinkType.STEP_CHILD
import com.neteinstein.goodcompany.core.domain.model.LinkType.STEP_PARENT
import com.neteinstein.goodcompany.core.domain.model.LinkType.STEP_SIBLING
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.repository.KinshipWords
import com.neteinstein.goodcompany.core.domain.usecase.KinshipQuery
import com.neteinstein.goodcompany.core.domain.usecase.KinshipResolver
import com.neteinstein.goodcompany.core.testing.EnglishKinshipWords
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Automatic kinship (F7, issue #41). */
class KinshipResolverTest {
    private fun me(id: String, relation: LinkType?, block: Friend.() -> Friend = { this }) = friend(id) { copy(relationToMe = relation).block() }

    /** [personToVia]: what "p" is to "v". */
    private fun link(personToVia: LinkType) = PersonLink("l", from = FriendId("v"), to = FriendId("p"), type = personToVia)

    private fun propose(viaToMe: LinkType, personToVia: LinkType) =
        KinshipResolver.proposals(listOf(me("v", viaToMe), friend("p")), listOf(link(personToVia))).singleOrNull()

    // One row per path: what your relative is to you, what the other person is to them → what's proposed.
    private val table = listOf(
        Triple(SIBLING, CHILD, listOf(NIECE_NEPHEW)) to false,
        Triple(SIBLING, PARENT, listOf(PARENT)) to false,
        Triple(SIBLING, SIBLING, listOf(SIBLING)) to false,
        Triple(SIBLING, HALF_SIBLING, listOf(SIBLING, HALF_SIBLING)) to true,
        Triple(SIBLING, SPOUSE, listOf(IN_LAW)) to false,
        Triple(SIBLING, EX, listOf(IN_LAW)) to true,
        Triple(SIBLING, COUSIN, listOf(COUSIN)) to false,
        Triple(HALF_SIBLING, CHILD, listOf(NIECE_NEPHEW)) to false,
        Triple(HALF_SIBLING, PARENT, listOf(PARENT, STEP_PARENT)) to true,
        Triple(STEP_SIBLING, PARENT, listOf(STEP_PARENT, PARENT)) to true,
        Triple(PARENT, PARENT, listOf(GRANDPARENT)) to false,
        Triple(PARENT, SIBLING, listOf(AUNT_UNCLE)) to false,
        Triple(PARENT, CHILD, listOf(SIBLING, HALF_SIBLING, STEP_SIBLING)) to true,
        Triple(PARENT, STEP_CHILD, listOf(STEP_SIBLING)) to false,
        Triple(PARENT, SPOUSE, listOf(PARENT, STEP_PARENT)) to true,
        Triple(STEP_PARENT, CHILD, listOf(STEP_SIBLING, HALF_SIBLING)) to true,
        Triple(CHILD, CHILD, listOf(GRANDCHILD)) to false,
        Triple(CHILD, SIBLING, listOf(CHILD, STEP_CHILD)) to true,
        Triple(CHILD, PARTNER, listOf(IN_LAW)) to false,
        Triple(CHILD, EX, listOf(IN_LAW)) to true,
        Triple(GRANDPARENT, CHILD, listOf(PARENT, AUNT_UNCLE)) to true,
        Triple(GRANDCHILD, SIBLING, listOf(GRANDCHILD)) to false,
        Triple(AUNT_UNCLE, CHILD, listOf(COUSIN)) to false,
        Triple(AUNT_UNCLE, PARENT, listOf(GRANDPARENT)) to false,
        Triple(AUNT_UNCLE, SPOUSE, listOf(AUNT_UNCLE)) to false,
        Triple(NIECE_NEPHEW, PARENT, listOf(SIBLING, HALF_SIBLING, IN_LAW)) to true,
        Triple(COUSIN, PARENT, listOf(AUNT_UNCLE)) to false,
        Triple(COUSIN, SIBLING, listOf(COUSIN)) to false,
        Triple(SPOUSE, PARENT, listOf(IN_LAW)) to false,
        Triple(PARTNER, SIBLING, listOf(IN_LAW)) to false,
        Triple(SPOUSE, CHILD, listOf(CHILD, STEP_CHILD)) to true,
        Triple(EX, PARENT, listOf(IN_LAW)) to true,
        Triple(EX, CHILD, listOf(CHILD, STEP_CHILD)) to true,
    )

    @Test
    fun eachPath_proposesTheRightKinship() {
        table.forEach { (row, ambiguous) ->
            val (viaToMe, personToVia, expected) = row
            val proposal = propose(viaToMe, personToVia)
            assertEquals(expected, proposal?.options, "$viaToMe's $personToVia")
            assertEquals(ambiguous, proposal?.ambiguous, "$viaToMe's $personToVia")
            assertEquals(if (ambiguous) null else expected.single(), proposal?.proposed)
        }
    }

    @Test
    fun nothingIsProposed_beyondFamily() {
        assertNull(propose(SIBLING, FRIEND))
        assertNull(propose(FRIEND, CHILD))
        assertNull(propose(COLLEAGUE, SIBLING))
        assertNull(propose(CHILD, PARENT)) // your child's other parent is a partner or an ex, not kinship
        assertNull(propose(NIECE_NEPHEW, CHILD)) // no word for a grand-niece
        // Through someone whose relationship to you isn't known: nothing.
        assertTrue(KinshipResolver.proposals(listOf(friend("v"), friend("p")), listOf(link(CHILD))).isEmpty())
    }

    @Test
    fun myBrothersDaughter_isProposedAsMyNiece_throughHim() {
        val rui = me("rui", SIBLING) { copy(name = "Rui Costa") }
        val leonor = friend("leonor", "Leonor Costa")
        // Stored from Leonor's side ("Rui is Leonor's parent") to check both directions.
        val proposal = KinshipResolver.proposals(listOf(rui, leonor), listOf(PersonLink("l", leonor.id, rui.id, PARENT))).single()
        assertEquals(leonor.id, proposal.person.id)
        assertEquals(NIECE_NEPHEW, proposal.proposed)
        assertEquals(rui.id, proposal.via.id)
        assertEquals(CHILD, proposal.roleToVia)
    }

    @Test
    fun aChildOfBothMyParents_isMySibling_butOfOneOfThem_isOpen() {
        val mum = me("mum", PARENT)
        val dad = me("dad", PARENT)
        val duarte = friend("duarte")
        val fromMum = PersonLink("1", mum.id, duarte.id, CHILD)
        val fromDad = PersonLink("2", dad.id, duarte.id, CHILD)
        val half = KinshipResolver.proposals(listOf(mum, dad, duarte), listOf(fromMum)).single()
        assertTrue(half.ambiguous)
        assertEquals(listOf(SIBLING, HALF_SIBLING, STEP_SIBLING), half.options)
        assertEquals(SIBLING, KinshipResolver.proposals(listOf(mum, dad, duarte), listOf(fromMum, fromDad)).single().proposed)
    }

    @Test
    fun pathsThatDisagree_leaveTheChoiceOpen() {
        // Mia is my brother's daughter and my cousin's sister: niece or cousin?
        val rui = me("rui", SIBLING)
        val ines = me("ines", COUSIN)
        val mia = friend("mia")
        val links = listOf(PersonLink("1", rui.id, mia.id, CHILD), PersonLink("2", ines.id, mia.id, SIBLING))
        val proposal = KinshipResolver.proposals(listOf(rui, ines, mia), links).single()
        assertTrue(proposal.ambiguous)
        assertEquals(listOf(NIECE_NEPHEW, COUSIN), proposal.options)
        // Two paths that agree stay a plain proposal.
        val ana = me("ana", SIBLING)
        val agree = listOf(PersonLink("1", rui.id, mia.id, CHILD), PersonLink("3", ana.id, mia.id, NIECE_NEPHEW))
        assertEquals(NIECE_NEPHEW, KinshipResolver.proposals(listOf(rui, ana, mia), agree).single().proposed)
    }

    @Test
    fun cycles_peopleAlreadyPlaced_andDismissedOnes_proposeNothing() {
        // Two siblings of mine linked to each other: both already have a relationship to me.
        val rui = me("rui", SIBLING)
        val ana = me("ana", SIBLING)
        assertTrue(KinshipResolver.proposals(listOf(rui, ana), listOf(PersonLink("1", rui.id, ana.id, SIBLING))).isEmpty())
        // A loop back through the person themself (a link to someone missing, a self link) is skipped.
        val leo = friend("leo")
        val loops =
            listOf(PersonLink("2", rui.id, leo.id, CHILD), PersonLink("3", leo.id, rui.id, PARENT), PersonLink("4", rui.id, FriendId("ghost"), CHILD))
        assertEquals(listOf(NIECE_NEPHEW), KinshipResolver.proposals(listOf(rui, leo), loops).single().options)

        // "Not right" is remembered: the relations offered then aren't offered again.
        val dismissed = leo.copy(dismissedKinship = setOf(NIECE_NEPHEW))
        assertTrue(KinshipResolver.proposals(listOf(rui, dismissed), loops).isEmpty())
        // An open choice keeps whatever hasn't been dismissed.
        val mum = me("mum", PARENT)
        val duarte = friend("duarte") { copy(dismissedKinship = setOf(STEP_SIBLING)) }
        assertEquals(
            listOf(SIBLING, HALF_SIBLING),
            KinshipResolver.proposals(listOf(mum, duarte), listOf(PersonLink("5", mum.id, duarte.id, CHILD))).single().options,
        )
    }

    @Test
    fun confirmingOrDismissing_isKeptAndShowsOnThePersonsPage() = runTest {
        val g = TestGraph(listOf(me("rui", SIBLING), friend("leonor"), me("gra", PARENT), friend("duarte")))
        g.linkRepo.upsert(PersonLink("1", FriendId("rui"), FriendId("leonor"), CHILD))
        g.linkRepo.upsert(PersonLink("2", FriendId("gra"), FriendId("duarte"), CHILD))
        assertEquals(listOf("duarte", "leonor"), g.observeKinship().first().map { it.person.id.value })
        assertEquals(NIECE_NEPHEW, g.observeFriendDetail(FriendId("leonor")).first()!!.kinship?.proposed)

        g.confirmKinship(FriendId("leonor"), NIECE_NEPHEW)
        assertEquals(NIECE_NEPHEW, g.friendRepo.get(FriendId("leonor"))!!.relationToMe)
        assertNull(g.observeFriendDetail(FriendId("leonor")).first()!!.kinship)

        g.dismissKinship(FriendId("duarte"), listOf(SIBLING, HALF_SIBLING, STEP_SIBLING))
        assertTrue(g.observeKinship().first().isEmpty())
        assertNull(g.friendRepo.get(FriendId("duarte"))!!.relationToMe)
    }

    // --- Search by kinship ---

    @Test
    fun kinshipSearch_understandsPossessivesConnectorsAndAccents() {
        val en = EnglishKinshipWords
        assertEquals(setOf(NIECE_NEPHEW), KinshipQuery.parse("my nieces and nephews", en))
        assertEquals(setOf(NIECE_NEPHEW), KinshipQuery.parse("  My   NIECES ", en))
        assertEquals(setOf(COUSIN, SIBLING), KinshipQuery.parse("my cousins, my brothers & sisters", en))
        assertEquals(setOf(COUSIN), KinshipQuery.parse("cousins", en))
        assertNull(KinshipQuery.parse("Rui", en))
        assertNull(KinshipQuery.parse("my nieces and Rui", en))
        assertNull(KinshipQuery.parse("my", en))
        assertNull(KinshipQuery.parse("", en))

        val pt = KinshipWords(
            listOf("os meus", "meus"),
            listOf("e"),
            mapOf(
                NIECE_NEPHEW to listOf("sobrinhos", "sobrinhas"),
                GRANDPARENT to listOf("avós"),
            ),
        )
        assertEquals(setOf(NIECE_NEPHEW), KinshipQuery.parse("os meus sobrinhos e sobrinhas", pt))
        assertEquals(setOf(GRANDPARENT), KinshipQuery.parse("os meus avos", pt)) // typed without the accent
        // Romanian puts the possessive after the word.
        val ro = KinshipWords(listOf("mei", "mele"), listOf("și"), mapOf(COUSIN to listOf("verii"), NIECE_NEPHEW to listOf("nepoții")))
        assertEquals(setOf(COUSIN, NIECE_NEPHEW), KinshipQuery.parse("verii mei și nepoții mei", ro))
        assertFalse(KinshipQuery.parse("verii mei", ro).isNullOrEmpty())
    }
}
