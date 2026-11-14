package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.PeopleDashboard
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeopleDashboardTest {
    private val today = LocalDate(2026, 3, 10)
    private val zone = TimeZone.UTC
    private fun id(s: String) = FriendId(s)

    // Ana was seen a lot until early February, then nothing: cooling. Rui and Bea are seen now: warming.
    private val ana = friend("ana") { copy(isCloseFriend = true) }
    private val rui = friend("rui") { copy(isCloseFriend = true) }
    private val bea = friend("bea") { copy(closeness = Closeness.INNER, relationToMe = LinkType.SIBLING) }
    private val newbie = friend("newbie") { copy(isCloseFriend = true) }
    private val zed = friend("zed") // address book only
    private val friends = listOf(ana, rui, bea, newbie, zed)
    private val climbing = FriendGroup("climb", "Climbing", memberIds = listOf(id("ana"), id("rui")))
    private val choir = FriendGroup("choir", "Choir", memberIds = listOf(id("ana")))
    private val empty = FriendGroup("empty", "Old job", memberIds = listOf(id("zed-gone")))
    private val hangouts = listOf(
        Hangout("1", listOf(id("ana")), at(2026, 2, 1)),
        Hangout("2", listOf(id("ana")), at(2026, 2, 5)),
        Hangout("3", listOf(id("ana")), at(2026, 2, 8)),
        Hangout("4", listOf(id("rui")), at(2026, 3, 9)),
        Hangout("5", listOf(id("bea")), at(2026, 3, 10)),
        Hangout("old", listOf(id("zed")), at(2025, 10, 20)),
        Hangout("older", listOf(id("zed")), at(2025, 9, 1)), // before the six months shown
    )
    private val interactions = listOf(Interaction(id("rui"), InteractionType.CALL, at(2026, 3, 1)))

    private fun build(settings: AppSettings = AppSettings()) =
        PeopleDashboard.build(friends, listOf(climbing, choir, empty), hangouts, interactions, settings, today, zone)

    private fun warmth(f: Friend, day: LocalDate) = Warmth.score(f, hangouts, interactions, CadenceRules().cadenceFor(f), day, zone).value

    @Test
    fun countsAveragesAndCirclesFromTheSameWarmthAsEachPage() {
        val d = build()
        assertEquals(4, d.people) // not Zed
        assertEquals(5, d.meetupsLast90Days) // Zed's October meetup is over 90 days ago
        val now = listOf(warmth(ana, today), warmth(rui, today), warmth(bea, today), warmth(newbie, today))
        assertEquals(now.average().roundToInt(), d.averageWarmth)

        // Choir is just Ana: it's the cooling circle. Climbing (Ana + Rui) is not. Empty circles are left out.
        assertEquals(setOf("climb", "choir"), d.byCircle.map { it.circle.id }.toSet())
        val choirRow = d.byCircle.single { it.circle.id == "choir" }
        assertEquals(warmth(ana, today), choirRow.warmth)
        assertEquals(warmth(ana, today) - warmth(ana, LocalDate(2026, 2, 8)), choirRow.change)
        assertTrue(choirRow.isCooling)
        assertTrue(!d.byCircle.single { it.circle.id == "climb" }.isCooling)
        assertEquals(2, d.byCircle.single { it.circle.id == "climb" }.members)

        assertEquals(listOf(Closeness.INNER), d.byCloseness.map { it.closeness })
        assertEquals(warmth(bea, today), d.byCloseness.single().warmth)
    }

    @Test
    fun coolingTrendMonthsAndNeverLogged() {
        val d = build()
        assertEquals(listOf("ana"), d.cooling.map { it.friend.id.value })
        val c = d.cooling.single()
        assertEquals(warmth(ana, LocalDate(2026, 2, 8)), c.before)
        assertEquals(warmth(ana, today), c.now)
        assertEquals("climb", c.circle?.id)

        assertEquals(PeopleDashboard.TREND_WEEKS + 1, d.trend.size)
        assertEquals(d.averageWarmth, d.trend.last())
        assertEquals(d.trend.last() - d.trend.first(), d.trendDelta)

        assertEquals(listOf(2025 to 10, 2025 to 11, 2025 to 12, 2026 to 1, 2026 to 2, 2026 to 3), d.meetupsPerMonth.map { it.year to it.month })
        assertEquals(listOf(1, 0, 0, 0, 3, 2), d.meetupsPerMonth.map { it.meetups })

        assertEquals(listOf("newbie"), d.neverLogged.map { it.id.value })
    }

    @Test
    fun withWarmthHidden_onlyCountsRemain() {
        val d = build(AppSettings(showWarmth = false))
        assertEquals(false, d.showWarmth)
        assertNull(d.averageWarmth)
        assertTrue(d.trend.isEmpty())
        assertNull(d.trendDelta)
        assertTrue(d.cooling.isEmpty())
        assertTrue(d.byCircle.all { it.warmth == null && it.change == null && !it.isCooling })
        assertEquals(2, d.byCircle.single { it.circle.id == "climb" }.members)
        assertTrue(d.byCloseness.all { it.warmth == null })
        assertEquals(4, d.people)
        assertEquals(5, d.meetupsLast90Days)
        assertEquals(listOf("newbie"), d.neverLogged.map { it.id.value })
    }

    @Test
    fun nobodyYet() {
        val d = PeopleDashboard.build(emptyList(), emptyList(), emptyList(), emptyList(), AppSettings(), today, zone)
        assertEquals(0, d.people)
        assertNull(d.averageWarmth)
        assertTrue(d.trend.isEmpty() && d.cooling.isEmpty() && d.byCircle.isEmpty() && d.neverLogged.isEmpty())
        assertEquals(PeopleDashboard.MONTHS, d.meetupsPerMonth.size)
    }

    @Test
    fun theUseCaseIncludesTheFamilyCircle() = runTest {
        val g = TestGraph(friends = friends, hangouts = hangouts)
        val d = g.observePeopleDashboard().first()
        assertTrue(d.byCircle.any { it.circle.name == "Family" && it.members == 1 })
    }
}
