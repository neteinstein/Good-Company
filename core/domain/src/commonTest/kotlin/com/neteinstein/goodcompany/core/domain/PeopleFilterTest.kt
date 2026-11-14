package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.FamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.PeopleFilter
import com.neteinstein.goodcompany.core.domain.usecase.PeopleFiltering
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeopleFilterTest {
    private val ana = friend("ana", "Ana Lopes") { copy(closeness = Closeness.INNER, likes = listOf("Pizza")) }
    private val ana2 = friend("ana2", "Ana Silva") { copy(closeness = Closeness.CLOSE) }
    private val tomas = friend("tomas", "Tomás Costa") { copy(closeness = Closeness.CLOSE, relationToMe = LinkType.COUSIN) }
    private val kid = friend("kid", "Kiko") { copy(showInMyList = false) }
    private val rui = friend("rui", "Rui") // an Acquaintance
    private val everyone = listOf(rui, tomas, kid, ana2, ana)
    private val choir = FriendGroup("choir", "Choir", memberIds = listOf(FriendId("ana"), FriendId("kid")))
    private val family = FamilyCircle.of(everyone, "Family")!!
    private val circles = listOf(family, choir)

    private fun names(filter: PeopleFilter, query: String = "", kinship: Set<LinkType>? = null, exclude: Set<FriendId> = emptySet()) =
        PeopleFiltering.apply(everyone, filter, circles, query, kinship, exclude).people.map { it.name }

    @Test
    fun everyone_isAtoZ_withNoCap() {
        assertEquals(listOf("Ana Lopes", "Ana Silva", "Kiko", "Rui", "Tomás Costa"), names(PeopleFilter.Everyone))
    }

    @Test
    fun aCircle_keepsItsMembers_familyIncluded() {
        assertEquals(listOf("Ana Lopes", "Kiko"), names(PeopleFilter(circleId = "choir")))
        assertEquals(listOf("Tomás Costa"), names(PeopleFilter(circleId = FamilyCircle.ID)))
        // A circle that's gone reads as everyone.
        assertEquals(5, names(PeopleFilter(circleId = "gone")).size)
    }

    @Test
    fun aLevel_keepsThatLevel_andTheCountsIgnoreTheFilter() {
        val result = PeopleFiltering.apply(everyone, PeopleFilter(closeness = Closeness.CLOSE), circles, query = "ana")
        assertEquals(listOf("Ana Silva"), result.people.map { it.name })
        assertEquals(5, result.everyoneCount)
        assertEquals(mapOf(Closeness.INNER to 1, Closeness.CLOSE to 2, Closeness.GOOD to 0, Closeness.ACQUAINTANCE to 2), result.levelCounts)
    }

    @Test
    fun levelAndCircle_areOneChoice() {
        val level = PeopleFilter.Everyone.withCloseness(Closeness.CLOSE)
        assertEquals(PeopleFilter(circleId = "choir"), level.withCircle("choir"))
        assertEquals(PeopleFilter(closeness = Closeness.INNER), PeopleFilter(circleId = "choir").withCloseness(Closeness.INNER))
        assertTrue(level.withCloseness(null).isEveryone)
    }

    @Test
    fun theNameSearch_ignoresCase_andWorksInsideTheFilter() {
        assertEquals(listOf("Ana Lopes", "Ana Silva"), names(PeopleFilter.Everyone, "  aNA "))
        assertEquals(listOf("Ana Lopes"), names(PeopleFilter(circleId = "choir"), "ana"))
        // Accents are matched as typed, as on the People tab.
        assertEquals(listOf("Tomás Costa"), names(PeopleFilter.Everyone, "tomás"))
        assertEquals(emptyList(), names(PeopleFilter.Everyone, "tomas"))
        // Likes only count where asked for (the People tab).
        assertEquals(emptyList(), names(PeopleFilter.Everyone, "pizza"))
        assertTrue(PeopleFiltering.matches(ana, "pizza", likes = true))
    }

    @Test
    fun kinship_winsOverTheName() {
        assertEquals(listOf("Tomás Costa"), names(PeopleFilter.Everyone, "my cousins", setOf(LinkType.COUSIN)))
    }

    @Test
    fun excluded_neverShow_norCount() {
        val result = PeopleFiltering.apply(everyone, PeopleFilter.Everyone, circles, exclude = setOf(FriendId("ana")))
        assertEquals(4, result.everyoneCount)
        assertEquals(0, result.levelCounts[Closeness.INNER])
        assertEquals(listOf("Ana Silva", "Kiko", "Rui", "Tomás Costa"), result.people.map { it.name })
    }

    @Test
    fun listedOnly_hidesUnlisted_unlessSearchedOrInACircle() {
        fun listed(filter: PeopleFilter, query: String = "") =
            PeopleFiltering.apply(everyone, { it }, filter, circles, query, listedOnly = true)
        assertEquals(4, listed(PeopleFilter.Everyone).everyoneCount)
        assertTrue(kid !in listed(PeopleFilter.Everyone).people)
        assertTrue(kid in listed(PeopleFilter.Everyone, "kik").people)
        assertTrue(kid in listed(PeopleFilter(circleId = "choir")).people)
    }

    @Test
    fun byCloseness_groupsInnerFirst_andSkipsEmptyLevels() {
        val groups = PeopleFiltering.apply(everyone, PeopleFilter.Everyone, circles).byCloseness { it.closeness }
        assertEquals(listOf(Closeness.INNER, Closeness.CLOSE, Closeness.ACQUAINTANCE), groups.map { it.first })
        assertEquals(listOf("Ana Silva", "Tomás Costa"), groups[1].second.map { it.name })
    }
}
