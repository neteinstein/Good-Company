package org.goodcompany.app.feature.hangouts.ui.screens.met

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.goodcompany.app.core.ui.components.AppSnackbarHost
import org.goodcompany.app.core.ui.components.namesList
import org.goodcompany.app.core.ui.components.showUndo
import org.goodcompany.app.core.ui.resources.undo
import org.goodcompany.app.feature.hangouts.resources.Res
import org.goodcompany.app.feature.hangouts.resources.msg_logged_with
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.goodcompany.app.core.ui.resources.Res as CoreRes

/**
 * "Logged time with Rui ✨ · Undo" after the Log popup saved a meetup (issue #47). Hosted once by
 * the app shell, since the panel has closed by then.
 */
@Composable
fun LoggedMeetupSnackbar(modifier: Modifier = Modifier, viewModel: LoggedMeetupViewModel = koinViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    val meetup = pending
    val text = meetup?.let { stringResource(Res.string.msg_logged_with, namesList(it.people)) }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(meetup) {
        if (meetup == null || text == null) return@LaunchedEffect
        val result = host.showUndo(text, undo)
        if (result == SnackbarResult.ActionPerformed) viewModel.onUndo(meetup) else viewModel.onShown(meetup)
    }
    AppSnackbarHost(host, modifier)
}
