package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/*
 * Snackbars always go away (decision "Snackbars · always go away", issue #63): one with an action (Undo) lasts
 * about 10 s, a plain one the short default, and a tap or a swipe dismisses either. Material 3 would make any
 * snackbar with an action `Indefinite`, so screens never call `showSnackbar` directly: they use [showMessage] or
 * [showUndo], and host them with [AppSnackbarHost].
 */

/** How long a snackbar stays: `Long` (≈10 s) with an action, `Short` without; never `Indefinite`. */
fun snackbarDuration(actionLabel: String?): SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short

/** Shows [message] (with an optional action) and suspends until it times out, is dismissed or its action is tapped. */
suspend fun SnackbarHostState.showMessage(message: String, actionLabel: String? = null): SnackbarResult =
    showSnackbar(message = message, actionLabel = actionLabel, duration = snackbarDuration(actionLabel))

/** An Undo snackbar: lasts about 10 s; `ActionPerformed` means the user tapped [undoLabel]. */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): SnackbarResult = showMessage(message, undoLabel)

/** The app's snackbar host: every snackbar can be dismissed by tapping it or swiping it away. */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> key(data) { DismissibleSnackbar(data) } }
}

@Composable
private fun DismissibleSnackbar(data: SnackbarData) {
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss()
    }
    SwipeToDismissBox(state = swipe, backgroundContent = {}) {
        Snackbar(
            snackbarData = data,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { data.dismiss() },
            ),
        )
    }
}
