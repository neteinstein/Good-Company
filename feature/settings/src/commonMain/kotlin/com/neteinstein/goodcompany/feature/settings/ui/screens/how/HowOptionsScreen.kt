package com.neteinstein.goodcompany.feature.settings.ui.screens.how

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogKindOption
import com.neteinstein.goodcompany.core.ui.components.logKindIcon
import com.neteinstein.goodcompany.core.ui.components.logKindLabel
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.activities_name_blank
import com.neteinstein.goodcompany.feature.settings.resources.activities_removed
import com.neteinstein.goodcompany.feature.settings.resources.how_add
import com.neteinstein.goodcompany.feature.settings.resources.how_counts
import com.neteinstein.goodcompany.feature.settings.resources.how_counts_hint
import com.neteinstein.goodcompany.feature.settings.resources.how_hint
import com.neteinstein.goodcompany.feature.settings.resources.how_name_taken
import com.neteinstein.goodcompany.feature.settings.resources.how_options
import com.neteinstein.goodcompany.feature.settings.resources.how_rename
import com.neteinstein.goodcompany.feature.settings.resources.how_yours
import com.neteinstein.goodcompany.feature.settings.resources.how_yours_hint
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.OrderedOptionLook
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.OrderedOptionsActions
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.OrderedOptionsContent
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.OrderedOptionsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.OrderedOptionsTexts
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

data class HowOptionsActions(
    val onBack: () -> Unit = {},
    val onToggle: (LogKind) -> Unit = {},
    val onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onAddCustom: (String) -> Unit = {},
    val onRenameCustom: (from: String, to: String) -> Unit = { _, _ -> },
    val onRemoveCustom: (String) -> Unit = {},
    val onMoveCustom: (from: Int, to: Int) -> Unit = { _, _ -> },
    val onUndoRemove: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onToggleCounts: (LogKindOption) -> Unit = {},
)

/** Localised snackbar text for a [HowOptionsMessage]. */
suspend fun HowOptionsMessage.text(): String = when (this) {
    is HowOptionsMessage.Removed -> getString(Res.string.activities_removed, name)
    is HowOptionsMessage.NotSaved -> getString(
        when (problem) {
            ActivityNameProblem.BLANK -> Res.string.activities_name_blank
            ActivityNameProblem.TAKEN -> Res.string.how_name_taken
        },
    )
}

/** Settings → How (issue #152): which ways of keeping in touch the Log popup offers, in what order, and your own. */
@Composable
fun HowOptionsScreen(onBack: () -> Unit, viewModel: HowOptionsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HowOptionsContent(
        state,
        HowOptionsActions(
            onBack = onBack,
            onToggle = viewModel::onToggle,
            onMove = viewModel::onMove,
            onAddCustom = viewModel::onAddCustom,
            onRenameCustom = viewModel::onRenameCustom,
            onRemoveCustom = viewModel::onRemoveCustom,
            onMoveCustom = viewModel::onMoveCustom,
            onUndoRemove = viewModel::onUndoRemove,
            onMessageShown = viewModel::onMessageShown,
            onToggleCounts = viewModel::onToggleCounts,
        ),
    )
}

/** Drawn like Settings → Activities, by the same [OrderedOptionsContent]. */
@Composable
fun HowOptionsContent(state: HowOptionsUiState, actions: HowOptionsActions) {
    OrderedOptionsContent(
        texts = OrderedOptionsTexts(
            title = stringResource(Res.string.how_options),
            hint = stringResource(Res.string.how_hint),
            yours = stringResource(Res.string.how_yours),
            yoursHint = stringResource(Res.string.how_yours_hint),
            add = stringResource(Res.string.how_add),
            rename = stringResource(Res.string.how_rename),
        ),
        look = OrderedOptionLook({ logKindIcon(it) }, { logKindLabel(it) }),
        enabled = state.enabled,
        disabled = state.disabled,
        canTurnOff = state.canTurnOff,
        custom = state.custom,
        customIcon = { logKindIcon(LogKind.OTHER) },
        message = state.message?.let { m -> OrderedOptionsMessage(m, { m.text() }, withUndo = m is HowOptionsMessage.Removed) },
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
        maxNameLength = LogInteractionUseCase.LABEL_MAX,
    ) { CountsAsCatchingUp(state, actions.onToggleCounts) }
}

/**
 * Decision 2026-11-21 ("Catch-up · what counts"): which ways of keeping in touch restart the countdown for people on
 * "As in Settings" (Edit person). In person always counts, so it has no row; journal entries never do.
 */
@Composable
private fun CountsAsCatchingUp(state: HowOptionsUiState, onToggle: (LogKindOption) -> Unit) {
    Text(
        stringResource(Res.string.how_counts),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
    Text(
        stringResource(Res.string.how_counts_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    state.countable.forEach { option ->
        val name = option.name
        val label = name ?: logKindLabel(option.kind)
        val checked = state.counts(option)
        ListItem(
            headlineContent = { Text(label) },
            leadingContent = { Icon(logKindIcon(option.kind), null) },
            trailingContent = { Switch(checked, { onToggle(option) }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(checked, role = Role.Switch) { onToggle(option) },
        )
    }
}
