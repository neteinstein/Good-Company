package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.how.HowOptionsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.how.HowOptionsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #152: Settings → How, built like Settings → Activities. */
class HowOptionsViewModelTest {
    private val g = TestGraph()

    private fun vm() = HowOptionsViewModel(g.observeSettings, g.setLogKinds, g.customLogKinds)

    @Test
    fun allAreOnByDefault_inTheDefaultOrder() = runViewModelTest {
        val state = collecting(vm().state)
        assertEquals(LogKind.DEFAULT_ORDER, state.value.enabled)
        assertTrue(state.value.disabled.isEmpty())
        assertTrue(state.value.custom.isEmpty())
    }

    @Test
    fun turningOffMovesItBelow_andOnAgainAddsItAtTheEnd() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onToggle(LogKind.EMAIL)
        assertEquals(listOf(LogKind.EMAIL), state.value.disabled)
        assertEquals(LogKind.DEFAULT_ORDER - LogKind.EMAIL, g.settingsRepo.settings.value.logKinds)
        vm.onToggle(LogKind.EMAIL)
        assertEquals(LogKind.EMAIL, state.value.enabled.last())
        assertTrue(state.value.disabled.isEmpty())
    }

    @Test
    fun theLastOneStaysOn() = runViewModelTest {
        g.settingsRepo.update { it.copy(logKinds = listOf(LogKind.CALL, LogKind.MEETUP)) }
        val vm = vm()
        val state = collecting(vm.state)
        vm.onToggle(LogKind.CALL)
        assertFalse(state.value.canTurnOff)
        vm.onToggle(LogKind.MEETUP)
        assertEquals(listOf(LogKind.MEETUP), g.settingsRepo.settings.value.logKinds)
    }

    @Test
    fun movesReorder_andOutOfRangeIsIgnored() = runViewModelTest {
        g.settingsRepo.update { it.copy(logKinds = listOf(LogKind.CALL, LogKind.MEETUP, LogKind.EMAIL)) }
        val vm = vm()
        val state = collecting(vm.state)
        vm.onMove(2, 0)
        assertEquals(listOf(LogKind.EMAIL, LogKind.CALL, LogKind.MEETUP), state.value.enabled)
        vm.onMove(0, 9)
        vm.onMove(1, 1)
        assertEquals(listOf(LogKind.EMAIL, LogKind.CALL, LogKind.MEETUP), g.settingsRepo.settings.value.logKinds)
    }

    @Test
    fun yourOwn_addRenameMoveRemoveAndUndo() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onAddCustom("Voice note")
        vm.onAddCustom("Letter")
        vm.onAddCustom("letter")
        assertEquals(HowOptionsMessage.NotSaved(ActivityNameProblem.TAKEN), state.value.message)
        vm.onMessageShown()
        vm.onRenameCustom("Letter", "Postcard")
        vm.onMoveCustom(1, 0)
        assertEquals(listOf("Postcard", "Voice note"), state.value.custom)

        vm.onRemoveCustom("Postcard")
        assertEquals(HowOptionsMessage.Removed("Postcard"), state.value.message)
        assertEquals(listOf("Voice note"), state.value.custom)
        vm.onUndoRemove()
        vm.onMessageShown()
        assertNull(state.value.message)
        assertEquals(listOf("Postcard", "Voice note"), g.settingsRepo.settings.value.customLogKinds)
        // Activities are untouched.
        assertTrue(g.settingsRepo.settings.value.customActivities.isEmpty())
    }
}
