package com.neteinstein.goodcompany.feature.friends.ui.screens.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.LogInteractionSheet
import com.neteinstein.goodcompany.core.ui.components.LogSheetActions
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The "Log" sheet as a screen: shown by the `LogInteractionRoute` dialog destination. [onClose] runs after a save too. */
@Composable
fun LogInteractionScreen(
    friendIds: List<FriendId>,
    type: String?,
    onClose: () -> Unit,
    circleId: String? = null,
    prefill: LogPrefill = LogPrefill(),
    viewModel: LogInteractionViewModel = koinViewModel(key = "log-${friendIds.joinToString { it.value }}-$type-$circleId-$prefill") {
        parametersOf(friendIds, type, circleId, prefill)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    LogInteractionSheet(
        state.sheet,
        LogSheetActions(
            onKind = viewModel::onKind,
            onPeople = viewModel::onPeople,
            onDate = viewModel::onDate,
            onNote = viewModel::onNote,
            onPlaceName = viewModel::onPlaceName,
            onUseLocation = viewModel::onUseLocation,
            onClearLocation = viewModel::onClearLocation,
            onActivity = viewModel::onActivity,
            onCircle = viewModel::onCircle,
            onTogglePerson = viewModel::onTogglePerson,
            onSave = viewModel::onSave,
            onDismiss = onClose,
        ),
    )
}
