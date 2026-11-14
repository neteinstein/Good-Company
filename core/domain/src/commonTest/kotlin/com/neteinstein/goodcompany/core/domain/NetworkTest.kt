package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.usecase.EdgeKind
import com.neteinstein.goodcompany.core.domain.usecase.FamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.ForceLayout
import com.neteinstein.goodcompany.core.domain.usecase.NetworkEdge
import com.neteinstein.goodcompany.core.domain.usecase.NetworkGraph
import com.neteinstein.goodcompany.core.domain.usecase.Point
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkTest {
    private val me = NetworkGraph.ME
    private fun id(s: String) = FriendId(s)

    private fun dist(p: Map<String, Point>, a: String, b: String) = hypot(p.getValue(a).x - p.getValue(b).x, p.getValue(a).y - p.getValue(b).y)

    // --- Graph ---

    @Test
    fun graph_youAtTheCentre_linksAndIntroducedBy_addressBookOnlyLeftOut() {
        val rui = friend("rui") { copy(isCloseFriend = true) }
        val ana = friend("ana") { copy(relationToMe = LinkType.SPOUSE) }
        val bea = friend("bea") { copy(relationToMe = LinkType.SIBLING) }
        val leonor = friend("leonor") // only on the map through Rui (his daughter)
        val pedro = friend("pedro") { copy(introducedBy = Introducer(id("rui"), "Rui")) } // only through "introduced by"
        val zed = friend("zed") // address book only: off the map
        val gone = friend("gone") { copy(isCloseFriend = true, isOrphaned = true) }
        val links = listOf(
            PersonLink("l1", from = id("rui"), to = id("leonor"), type = LinkType.CHILD),
            PersonLink("l2", from = id("rui"), to = id("ana"), type = LinkType.FRIEND),
            PersonLink("l3", from = id("ana"), to = id("bea"), type = LinkType.IN_LAW),
            PersonLink("l4", from = id("zed"), to = id("leonor"), type = LinkType.FRIEND), // neither is yours: ignored
            PersonLink("l5", from = id("rui"), to = id("nobody"), type = LinkType.FRIEND), // unknown person: ignored
        )
        val climbing = FriendGroup("c", "Climbing", memberIds = listOf(id("rui"), id("zed2")))
        val family = FamilyCircle.of(listOf(ana, bea), "Family")!!
        val g = NetworkGraph.build(listOf(rui, ana, bea, leonor, pedro, zed, gone), links, listOf(family, climbing), warmth = null)

        assertEquals(setOf("rui", "ana", "bea", "leonor", "pedro"), g.nodes.map { it.friend.id.value }.toSet())
        val edges = g.edges.toSet()
        assertTrue(NetworkEdge(me, "rui", EdgeKind.FRIEND) in edges || NetworkEdge("rui", me, EdgeKind.FRIEND) in edges)
        fun kind(a: String, b: String) = g.edges.single { setOf(it.a, it.b) == setOf(a, b) }.kind
        assertEquals(EdgeKind.PARTNER, kind(me, "ana"))
        assertEquals(EdgeKind.FAMILY, kind(me, "bea"))
        assertEquals(EdgeKind.FAMILY, kind("rui", "leonor"))
        assertEquals(EdgeKind.FRIEND, kind("rui", "ana"))
        assertEquals(EdgeKind.FAMILY, kind("ana", "bea"))
        assertEquals(EdgeKind.INTRODUCED, kind("pedro", "rui"))
        // Leonor and Pedro hang off Rui, not off you.
        assertTrue(g.edges.none { setOf(it.a, it.b) == setOf(me, "leonor") || setOf(it.a, it.b) == setOf(me, "pedro") })
        // Colour by the first circle; warmth hidden → null.
        assertEquals("c", g.nodes.single { it.friend.id == id("rui") }.circle?.id)
        assertEquals(FamilyCircle.ID, g.nodes.single { it.friend.id == id("bea") }.circle?.id) // a sibling is family
        assertNull(g.nodes.single { it.friend.id == id("ana") }.circle) // a spouse isn't in the Family circle
        assertNull(g.nodes.first().warmth)
        assertEquals(listOf(FamilyCircle.ID, "c"), g.circles.map { it.id })
    }

    @Test
    fun graph_strongerKindWinsForTheSamePair() {
        val ana = friend("ana") { copy(isCloseFriend = true, introducedBy = Introducer(id("rui"), "Rui")) }
        val rui = friend("rui") { copy(isCloseFriend = true) }
        val g = NetworkGraph.build(
            listOf(ana, rui),
            listOf(PersonLink("l", from = id("ana"), to = id("rui"), type = LinkType.SIBLING)),
            emptyList(),
            warmth = mapOf(id("ana") to 80),
        )
        assertEquals(EdgeKind.FAMILY, g.edges.single { setOf(it.a, it.b) == setOf("ana", "rui") }.kind)
        assertEquals(80, g.nodes.single { it.friend.id == id("ana") }.warmth)
        assertNull(g.nodes.single { it.friend.id == id("rui") }.warmth)
    }

    // --- Layout ---

    @Test
    fun layout_isDeterministic_andKeepsThePinnedNode() {
        val ids = listOf(me, "a", "b", "c", "d", "e")
        val edges = listOf(me to "a", me to "b", "a" to "c", "b" to "d", "d" to "e")
        val pinned = mapOf(me to Point(0.0, 0.0))
        val first = ForceLayout.layout(ids, edges, pinned)
        val second = ForceLayout.layout(ids, edges, pinned)
        assertEquals(first, second)
        assertEquals(Point(0.0, 0.0), first[me])
        assertTrue(first.values.all { it.x.isFinite() && it.y.isFinite() })
        assertEquals(emptyMap(), ForceLayout.layout(emptyList(), emptyList()))
    }

    @Test
    fun layout_converges_triangleIsEven_andLinkedPeopleSitCloser() {
        val tri = ForceLayout.layout(listOf("a", "b", "c"), listOf("a" to "b", "b" to "c", "c" to "a"))
        val sides = listOf(dist(tri, "a", "b"), dist(tri, "b", "c"), dist(tri, "c", "a"))
        assertTrue(sides.max() / sides.min() < 1.05, "triangle sides $sides")
        assertTrue(sides.all { it in 0.3 * ForceLayout.IDEAL_LENGTH..2.0 * ForceLayout.IDEAL_LENGTH }, "sides $sides")

        // A path a–b–c–d: neighbours closer than the two ends.
        val path = ForceLayout.layout(listOf("a", "b", "c", "d"), listOf("a" to "b", "b" to "c", "c" to "d"))
        assertTrue(dist(path, "a", "b") < dist(path, "a", "d"))
        assertTrue(dist(path, "c", "d") < dist(path, "a", "d"))

        // A star around a pinned centre: the leaves end up at about the same distance.
        val leaves = (1..6).map { "l$it" }
        val star = ForceLayout.layout(listOf(me) + leaves, leaves.map { me to it }, pinned = mapOf(me to Point(0.0, 0.0)))
        val radii = leaves.map { dist(star, me, it) }
        assertTrue(radii.max() / radii.min() < 1.1, "radii $radii")
    }

    @Test
    fun layout_isStable_aSecondRunFromTheCacheBarelyMoves_andNewPeopleJoinWithoutAJump() {
        val ids = listOf(me) + (1..12).map { "p$it" }
        val edges = (1..12).map { me to "p$it" } + listOf("p1" to "p2", "p3" to "p4", "p5" to "p6")
        val pinned = mapOf(me to Point(0.0, 0.0))
        val settled = ForceLayout.layout(ids, edges, pinned)
        val again = ForceLayout.layout(ids, edges, pinned, previous = settled, iterations = ForceLayout.ITERATIONS / 3)
        val moved = ids.maxOf { dist(mapOf("x" to settled.getValue(it), "y" to again.getValue(it)), "x", "y") }
        assertTrue(moved < ForceLayout.IDEAL_LENGTH * 0.5, "moved $moved")

        val grown = ForceLayout.layout(ids + "new", edges + ("p1" to "new"), pinned, previous = settled, iterations = ForceLayout.ITERATIONS / 3)
        assertTrue(ids.maxOf { dist(mapOf("x" to settled.getValue(it), "y" to grown.getValue(it)), "x", "y") } < ForceLayout.IDEAL_LENGTH)
    }

    @Test
    fun layout_aFewHundredPeople_staysFiniteAndBounded() {
        val ids = listOf(me) + (1..250).map { "p$it" }
        val edges = (1..250).map { (if (it % 5 == 0) me else "p${it - 1}".takeIf { _ -> it > 1 } ?: me) to "p$it" }
        val result = ForceLayout.layout(ids, edges, pinned = mapOf(me to Point(0.0, 0.0)))
        assertTrue(result.values.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(result.values.maxOf { hypot(it.x, it.y) } < ForceLayout.IDEAL_LENGTH * 60)
    }

    // --- Use case ---

    @Test
    fun theMapUseCase_placesYouAtTheOrigin_andHidesWarmthWhenOff() = runTest {
        val g = TestGraph(
            friends = listOf(friend("ana") { copy(isCloseFriend = true, closeness = Closeness.INNER) }, friend("rui") { copy(isCloseFriend = true) }),
            hangouts = listOf(Hangout("h", listOf(id("ana")), at(2026, 3, 9))),
        )
        val map = g.observeNetworkMap().first()
        assertEquals(Point(0.0, 0.0), map.positions[me])
        assertEquals(setOf(me, "ana", "rui"), map.positions.keys)
        assertTrue(map.graph.nodes.single { it.friend.id == id("ana") }.warmth!! > map.graph.nodes.single { it.friend.id == id("rui") }.warmth!!)

        g.settingsRepo.update { it.copy(showWarmth = false) }
        val hidden = g.observeNetworkMap().first()
        assertTrue(hidden.graph.nodes.all { it.warmth == null })
        assertEquals(false, hidden.showWarmth)
        assertEquals(AppSettings().showWarmth, true)
    }
}
