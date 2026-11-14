package com.neteinstein.goodcompany.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.components.showUndo
import com.neteinstein.goodcompany.core.ui.components.snackbarDuration
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SnackbarsTest {
    @Test
    fun durationIsLongWithAnActionAndShortWithout() {
        assertEquals(SnackbarDuration.Long, snackbarDuration("Undo"))
        assertEquals(SnackbarDuration.Short, snackbarDuration(null))
        assertNotEquals(SnackbarDuration.Indefinite, snackbarDuration("Undo"))
    }

    @Test
    fun undoSnackbarIsLongNotIndefinite() = runTest {
        val host = SnackbarHostState()
        val shown = async { host.showUndo("Deleted", "Undo") }
        runCurrent()
        val visuals = host.currentSnackbarData!!.visuals
        assertEquals(SnackbarDuration.Long, visuals.duration)
        assertEquals("Undo", visuals.actionLabel)
        host.currentSnackbarData!!.performAction()
        assertEquals(SnackbarResult.ActionPerformed, shown.await())
    }

    @Test
    fun plainMessageIsShortAndTheNextShowsOnceDismissed() = runTest {
        val host = SnackbarHostState()
        val first = async { host.showMessage("Saved") }
        runCurrent()
        assertEquals(SnackbarDuration.Short, host.currentSnackbarData!!.visuals.duration)
        assertNull(host.currentSnackbarData!!.visuals.actionLabel)
        val second = async { host.showMessage("Again") }
        runCurrent()
        assertEquals("Saved", host.currentSnackbarData!!.visuals.message)
        host.currentSnackbarData!!.dismiss()
        assertEquals(SnackbarResult.Dismissed, first.await())
        runCurrent()
        assertEquals("Again", host.currentSnackbarData!!.visuals.message)
        host.currentSnackbarData!!.dismiss()
        assertEquals(SnackbarResult.Dismissed, second.await())
    }
}
