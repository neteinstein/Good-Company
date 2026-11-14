package com.neteinstein.goodcompany.feature.review

import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.usecase.NetworkGraph
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.review.ui.screens.network.NetworkMapViewModel
import com.neteinstein.goodcompany.feature.review.ui.screens.network.PeopleDashboardViewModel
import kotlinx.coroutines.Dispatchers
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
