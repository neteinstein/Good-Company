package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.LinkType
import org.goodcompany.app.core.domain.usecase.CircleDots
import org.goodcompany.app.core.domain.usecase.FamilyCircle
import org.goodcompany.app.core.domain.usecase.PeopleOrder
import org.goodcompany.app.core.domain.usecase.PeopleTab
import org.goodcompany.app.core.domain.usecase.withoutDeviceOnly
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Issue #120: All · Friends · Family, recent first inside a level, circle dots. */
class PeopleTabsTest {
    private val ana = friend("ana")
    private val rui = friend("rui") { copy(relationToMe = LinkType.SIBLING) }
    private val joe = friend("joe") { copy(relationToMe = LinkType.FRIEND) }

    @Test
    fun family_isTheFamilyCircle_andFriendsIsEveryoneElse() {
        val all = listOf(ana, rui, joe)
        assertEquals(all, all.filter { PeopleTab.ALL.includes(it) })
        assertEquals(FamilyCircle.members(all), all.filter { PeopleTab.FAMILY.includes(it) })
        assertEquals(listOf(ana, joe), all.filter { PeopleTab.FRIENDS.includes(it) })
    }

    @Test
    fun recentFirst_newestContactFirst_thenNeverContactedAToZ() {
        val last = mapOf("bea" to at(2026, 3, 1), "cal" to at(2026, 3, 9), "ana" to at(2026, 3, 1))
        val people = listOf(friend("zed"), friend("bea"), friend("cal"), friend("dan"), friend("ana"))
        val ordered = PeopleOrder.recentFirst(people, { it }, { last[it.id.value] })
        // Ties fall back to A–Z.
        assertEquals(listOf("Cal", "Ana", "Bea", "Dan", "Zed"), ordered.map { it.name })
    }

    @Test
    fun circleDots_inTheCirclesOrder_atMostFourThenMore() {
        val id = FriendId("ana")
        val circles = (1..6).map { FriendGroup("g$it", "C$it", memberIds = if (it == 3) emptyList() else listOf(id)) }
        val dots = CircleDots.of(id, circles)
        assertEquals(listOf("g1", "g2", "g4", "g5"), dots.shown.map { it.id })
        assertEquals(1, dots.more)
        assertEquals(CircleDots.None, CircleDots.of(FriendId("nobody"), circles))
        val byPerson = CircleDots.byPerson(circles)
        assertEquals(setOf(id), byPerson.keys)
        assertEquals(dots, byPerson.getValue(id))
    }

    @Test
    fun theLegendTip_isDismissedOnce_andIsDeviceOnly() = runTest {
        val g = TestGraph()
        g.dismissPeopleLegend()
        assertTrue(g.settingsRepo.settings.first().peopleLegendSeen)
        assertFalse(AppSettings(peopleLegendSeen = true).withoutDeviceOnly().peopleLegendSeen)
    }
}
