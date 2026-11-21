package com.neteinstein.goodcompany.feature.settings.ui.screens.activities

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.ui.components.activityIcon
import com.neteinstein.goodcompany.core.ui.components.activityLabel
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.activities
import com.neteinstein.goodcompany.feature.settings.resources.activities_add
import com.neteinstein.goodcompany.feature.settings.resources.activities_hint
import com.neteinstein.goodcompany.feature.settings.resources.activities_name_blank
import com.neteinstein.goodcompany.feature.settings.resources.activities_name_taken
import com.neteinstein.goodcompany.feature.settings.resources.activities_removed
import com.neteinstein.goodcompany.feature.settings.resources.activities_rename
import com.neteinstein.goodcompany.feature.settings.resources.activities_yours
import com.neteinstein.goodcompany.feature.settings.resources.activities_yours_hint
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class ActivitiesActions(
    val onBack: () -> Unit = {},
    val onToggle: (HangoutActivity) -> Unit = {},
    val onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    /** Your own activities (issue #121). */
    val onAddCustom: (String) -> Unit = {},
    val onRenameCustom: (from: String, to: String) -> Unit = { _, _ -> },
    val onRemoveCustom: (String) -> Unit = {},
    val onMoveCustom: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onUndoRemove: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Localised snackbar text for an [ActivitiesMessage]. */
suspend fun ActivitiesMessage.text(): String = when (this) {
    is ActivitiesMessage.Removed -> getString(Res.string.activities_removed, name)
    is ActivitiesMessage.NotSaved -> getString(
        when (problem) {
            ActivityNameProblem.BLANK -> Res.string.activities_name_blank
            ActivityNameProblem.TAKEN -> Res.string.activities_name_taken
        },
    )
}

/** Settings → Activities (F3): which activities the Log popup offers, and in what order. */
@Composable
fun ActivitiesScreen(onBack: () -> Unit, viewModel: ActivitiesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActivitiesContent(
        state,
        ActivitiesActions(
            onBack = onBack,
            onToggle = viewModel::onToggle,
            onMove = viewModel::onMove,
            onAddCustom = viewModel::onAddCustom,
            onRenameCustom = viewModel::onRenameCustom,
            onRemoveCustom = viewModel::onRemoveCustom,
            onMoveCustom = viewModel::onMoveCustom,
            onUndoRemove = viewModel::onUndoRemove,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

/** Settings → Activities (F3, issue #121), drawn like Settings → How (issue #152) by [OrderedOptionsContent]. */
@Composable
fun ActivitiesContent(state: ActivitiesUiState, actions: ActivitiesActions) {
    OrderedOptionsContent(
        texts = OrderedOptionsTexts(
            title = stringResource(Res.string.activities),
            hint = stringResource(Res.string.activities_hint),
            yours = stringResource(Res.string.activities_yours),
            yoursHint = stringResource(Res.string.activities_yours_hint),
            add = stringResource(Res.string.activities_add),
            rename = stringResource(Res.string.activities_rename),
        ),
        look = OrderedOptionLook({ activityIcon(it) }, { activityLabel(it) }),
        enabled = state.enabled,
        disabled = state.disabled,
        canTurnOff = state.canTurnOff,
        custom = state.custom,
        customIcon = { activityIcon(ActivityChoice.Custom(it)) },
        message = state.message?.let { m -> OrderedOptionsMessage(m, { m.text() }, withUndo = m is ActivitiesMessage.Removed) },
        actions = OrderedOptionsActions(
            onBack = actions.onBack,
            onToggle = actions.onToggle,
            onMove = actions.onMove,
            onAddCustom = actions.onAddCustom,
            onRenameCustom = actions.onRenameCustom,
            onRemoveCustom = actions.onRemoveCustom,
            onMoveCustom = actions.onMoveCustom,
            onUndoRemove = actions.onUndoRemove,
            onMessageShown = actions.onMessageShown,
        ),
    )
}
