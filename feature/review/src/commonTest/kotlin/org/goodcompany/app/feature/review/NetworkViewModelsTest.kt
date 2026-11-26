package org.goodcompany.app.feature.review

import kotlinx.coroutines.Dispatchers
import org.goodcompany.app.core.domain.model.Closeness
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.usecase.NetworkGraph
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.feature.review.ui.screens.network.NetworkMapViewModel
import org.goodcompany.app.feature.review.ui.screens.network.PeopleDashboardViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkViewModelsTest {
    private val g = TestGraph(
        friends = listOf(friend("ana") { copy(closeness = Closeness.CLOSE) }, friend("rui") { copy(closeness = Closeness.CLOSE) }),
        hangouts = listOf(Hangout("h", listOf(FriendId("ana")), at(2026, 3, 1))),
    )

    private suspend fun climbing() = g.groupRepo.upsert(FriendGroup("climb", "Climbing", memberIds = listOf(FriendId("ana"))))

    @Test
    fun theMapFiltersByCircle_andTappingTheChipAgainClearsIt() = runViewModelTest {
        climbing()
        val vm = NetworkMapViewModel(g.observeNetworkMap, Dispatchers.Main)
        val state = collecting(vm.state)
        assertEquals(setOf("ana", "rui"), state.value.visibleIds)
        assertEquals(NetworkGraph.ME in state.value.map!!.positions, true)

        vm.onCircle("climb")
        assertEquals("climb", state.value.circleId)
        assertEquals(setOf("ana"), state.value.visibleIds)

        vm.onCircle("climb")
        assertNull(state.value.circleId)
        assertEquals(2, state.value.visibleIds.size)
    }

    @Test
    fun theDashboardShowsYourPeople() = runViewModelTest {
        climbing()
        val vm = PeopleDashboardViewModel(g.observePeopleDashboard, Dispatchers.Main)
        val state = collecting(vm.state)
        val d = state.value.dashboard!!
        assertEquals(2, d.people)
        assertEquals(listOf("climb"), d.byCircle.map { it.circle.id })
        assertEquals(listOf("rui"), d.neverLogged.map { it.id.value })
    }
}
