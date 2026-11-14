package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity.COFFEE
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity.LUNCH
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity.WALK
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.ActivitiesViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivitiesViewModelTest {
    private val g = TestGraph()

    private fun vm() = ActivitiesViewModel(g.observeSettings, g.setActivities)

    @Test
    fun allAreOnByDefault_inTodaysOrder() = runViewModelTest {
        val state = collecting(vm().state)
        assertEquals(HangoutActivity.entries, state.value.enabled)
        assertTrue(state.value.disabled.isEmpty())
    }

    @Test
    fun turningOffMovesItBelow_andOnAgainAddsItAtTheEnd() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onToggle(LUNCH)
        assertFalse(LUNCH in state.value.enabled)
        assertEquals(listOf(LUNCH), state.value.disabled)
        assertEquals(HangoutActivity.entries - LUNCH, g.settingsRepo.settings.value.activities)
        vm.onToggle(LUNCH)
        assertEquals(LUNCH, state.value.enabled.last())
        assertTrue(state.value.disabled.isEmpty())
    }

    @Test
    fun theLastOneStaysOn() = runViewModelTest {
        g.settingsRepo.update { it.copy(activities = listOf(WALK, COFFEE)) }
        val vm = vm()
        val state = collecting(vm.state)
        vm.onToggle(WALK)
        assertFalse(state.value.canTurnOff)
        vm.onToggle(COFFEE)
        assertEquals(listOf(COFFEE), state.value.enabled)
        assertEquals(listOf(COFFEE), g.settingsRepo.settings.value.activities)
    }

    @Test
    fun movesReorder_andOutOfRangeIsIgnored() = runViewModelTest {
        g.settingsRepo.update { it.copy(activities = listOf(LUNCH, WALK, COFFEE)) }
        val vm = vm()
        val state = collecting(vm.state)
        vm.onMove(2, 0)
        assertEquals(listOf(COFFEE, LUNCH, WALK), state.value.enabled)
        vm.onMove(0, 5)
        vm.onMove(1, 1)
        assertEquals(listOf(COFFEE, LUNCH, WALK), g.settingsRepo.settings.value.activities)
    }
}
