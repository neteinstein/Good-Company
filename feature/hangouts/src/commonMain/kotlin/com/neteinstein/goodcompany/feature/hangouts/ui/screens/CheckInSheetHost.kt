package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInSheet
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "I'm with…" check-in sheet on its own, for another screen to open (Today, issue #65): the same sheet and
 * [HangoutsViewModel] as Together. [onSaved] gets the first names it logged; [onDismiss] means nothing was saved.
 */
@Composable
fun CheckInSheetHost(onSaved: (List<String>) -> Unit, onDismiss: () -> Unit, viewModel: HangoutsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestLocation = rememberPermissionRequester(AppPermission.LOCATION, viewModel::onLocationPermissionResult)
    LaunchedEffect(Unit) {
        viewModel.onMessageShown()
        viewModel.onStartCheckIn(null)
    }
    val draft = state.draft
    // Closes only after the sheet has been seen open: the first states may still predate onStartCheckIn.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(draft == null) {
        if (draft != null) {
            opened = true
            return@LaunchedEffect
        }
        if (!opened) return@LaunchedEffect
        // The sheet closed: saved (the view model says who with) or dismissed.
        val logged = (viewModel.state.value.message as? HangoutsMessage.LoggedWith)?.names
        viewModel.onMessageShown()
        if (logged != null) onSaved(logged) else onDismiss()
    }
    if (draft == null) return
    CheckInSheet(
        draft = draft,
        place = state.draftPlace,
        friends = state.friends,
        groups = state.groups.map { it.group },
        onPickGroup = viewModel::onPickDraftGroup,
        onToggleFriend = viewModel::onToggleDraftFriend,
        onQueryChange = viewModel::onDraftQueryChange,
        onPlaceChange = viewModel::onDraftPlaceChange,
        onNoteChange = viewModel::onDraftNoteChange,
        onUseLocation = requestLocation,
        onClearLocation = viewModel::onClearDraftLocation,
        onChangePlace = viewModel::onChangeDraftPlace,
        onSave = viewModel::onSaveCheckIn,
        onDismiss = viewModel::onDismissCheckIn,
    )
}
