package org.goodcompany.app.feature.settings

import org.goodcompany.app.core.domain.model.HangoutActivity
import org.goodcompany.app.core.domain.model.HangoutActivity.COFFEE
import org.goodcompany.app.core.domain.model.HangoutActivity.LUNCH
import org.goodcompany.app.core.domain.model.HangoutActivity.WALK
import org.goodcompany.app.core.domain.usecase.ActivityNameProblem
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.feature.settings.ui.screens.activities.ActivitiesMessage
import org.goodcompany.app.feature.settings.ui.screens.activities.ActivitiesViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivitiesViewModelTest {
    private val g = TestGraph()

    private fun vm() = ActivitiesViewModel(g.observeSettings, g.setActivities, g.customActivities)

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

    @Test
    fun yourOwnActivities_addRenameMoveAndRemoveWithUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddCustom("Surf")
        vm.onAddCustom("Chess")
        vm.onAddCustom("surf")
        assertEquals(ActivitiesMessage.NotSaved(ActivityNameProblem.TAKEN), state.value.message)
        vm.onMessageShown()
        vm.onRenameCustom("Chess", " ")
        assertEquals(ActivitiesMessage.NotSaved(ActivityNameProblem.BLANK), state.value.message)
        vm.onRenameCustom("Chess", "Board games")
        vm.onMoveCustom(1, 0)
        assertEquals(listOf("Board games", "Surf"), state.value.custom)

        vm.onRemoveCustom("Board games")
        assertEquals(ActivitiesMessage.Removed("Board games"), state.value.message)
        assertEquals(listOf("Surf"), state.value.custom)
        vm.onUndoRemove()
        assertEquals(listOf("Board games", "Surf"), state.value.custom)
        vm.onMessageShown()
        assertNull(state.value.message)
        // Built-ins are untouched.
        assertEquals(HangoutActivity.entries, state.value.enabled)
    }
}
