package com.neteinstein.goodcompany.feature.settings.ui.screens.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.activityIcon
import com.neteinstein.goodcompany.core.ui.components.activityLabel
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.remove
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.activities
import com.neteinstein.goodcompany.feature.settings.resources.activities_add
import com.neteinstein.goodcompany.feature.settings.resources.activities_hint
import com.neteinstein.goodcompany.feature.settings.resources.activities_move_down
import com.neteinstein.goodcompany.feature.settings.resources.activities_move_up
import com.neteinstein.goodcompany.feature.settings.resources.activities_name
import com.neteinstein.goodcompany.feature.settings.resources.activities_name_blank
import com.neteinstein.goodcompany.feature.settings.resources.activities_name_taken
import com.neteinstein.goodcompany.feature.settings.resources.activities_removed
import com.neteinstein.goodcompany.feature.settings.resources.activities_rename
import com.neteinstein.goodcompany.feature.settings.resources.activities_yours
import com.neteinstein.goodcompany.feature.settings.resources.activities_yours_hint
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.back as backLabel

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

private val ROW_HEIGHT = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesContent(state: ActivitiesUiState, actions: ActivitiesActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    // null: closed; "": a new one; else the name being renamed.
    var naming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val withUndo = message is ActivitiesMessage.Removed
        val result = snackbar.showMessage(message.text(), if (withUndo) undo else null)
        if (withUndo && result == SnackbarResult.ActionPerformed) actions.onUndoRemove()
        actions.onMessageShown()
    }
    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.activities), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.backLabel))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(Res.string.activities_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            EnabledActivities(state, actions)
            if (state.disabled.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            state.disabled.forEach { a ->
                ActivityRow(a, checked = false, enabled = true, onToggle = { actions.onToggle(a) }, modifier = Modifier.alpha(0.6f))
            }
            // Issue #121: your own activities.
            Text(
                stringResource(Res.string.activities_yours),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
            )
            Text(
                stringResource(Res.string.activities_yours_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            state.custom.forEachIndexed { i, name ->
                CustomActivityRow(
                    name,
                    canMoveUp = i > 0,
                    canMoveDown = i < state.custom.lastIndex,
                    onRename = { naming = name },
                    onMoveUp = { actions.onMoveCustom(i, i - 1) },
                    onMoveDown = { actions.onMoveCustom(i, i + 1) },
                    onRemove = { actions.onRemoveCustom(name) },
                )
            }
            TextButton(onClick = { naming = "" }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.activities_add))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    naming?.let { current ->
        ActivityNameDialog(
            initial = current,
            onDismiss = { naming = null },
            onSave = { typed ->
                if (current.isEmpty()) actions.onAddCustom(typed) else actions.onRenameCustom(current, typed)
                naming = null
            },
        )
    }
}

/** One of your own activities: tap to rename; move up / down; remove (with Undo). */
@Composable
private fun CustomActivityRow(
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onRename),
        leadingContent = { Icon(activityIcon(ActivityChoice.Custom(name)), null) },
        headlineContent = { Text(name) },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(Res.string.activities_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.activities_move_down))
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, stringResource(CoreRes.string.remove)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Name a new activity, or rename one ([initial] not empty). */
@Composable
private fun ActivityNameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.isEmpty()) Res.string.activities_add else Res.string.activities_rename)) },
        text = {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(Res.string.activities_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** The activities that are on, dragged by their handle (or moved with the accessibility actions). */
@Composable
private fun EnabledActivities(state: ActivitiesUiState, actions: ActivitiesActions) {
    // A local copy follows the finger; the new order is saved once, when the drag ends.
    val order = remember(state.enabled) { state.enabled.toMutableStateList() }
    var dragged by remember { mutableStateOf<HangoutActivity?>(null) }
    var dragStart by remember { mutableStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(Res.string.activities_move_up)
    val moveDown = stringResource(Res.string.activities_move_down)
    order.forEachIndexed { index, a ->
        // Keyed, so a row keeps its gesture while the list reorders under the finger.
        key(a) {
            val isDragged = dragged == a
            val handle = Modifier.pointerInput(a) {
                detectDragGestures(
                    onDragStart = {
                        dragged = a
                        dragStart = order.indexOf(a)
                        offset = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        offset += amount.y
                        val current = order.indexOf(a)
                        val target = (current + (offset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                        if (target != current) {
                            order.add(target, order.removeAt(current))
                            offset -= (target - current) * rowPx
                        }
                    },
                    onDragEnd = {
                        val end = order.indexOf(a)
                        dragged = null
                        offset = 0f
                        if (end != dragStart) actions.onMove(dragStart, end)
                    },
                    onDragCancel = {
                        dragged = null
                        offset = 0f
                    },
                )
            }
            ActivityRow(
                a,
                checked = true,
                enabled = state.canTurnOff,
                onToggle = { actions.onToggle(a) },
                handle = handle,
                modifier = Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) offset else 0f }
                    .semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(moveUp) { actions.onMove(index, index - 1).let { true } }.takeIf { index > 0 },
                            CustomAccessibilityAction(moveDown) { actions.onMove(index, index + 1).let { true } }.takeIf { index < order.lastIndex },
                        )
                    },
            )
        }
    }
}

@Composable
private fun ActivityRow(
    activity: HangoutActivity,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    handle: Modifier? = null,
) {
    ListItem(
        modifier = modifier.height(ROW_HEIGHT),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.DragIndicator,
                    null,
                    (handle ?: Modifier).alpha(if (handle != null) 1f else 0.3f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Icon(activityIcon(activity), null)
            }
        },
        headlineContent = { Text(activityLabel(activity)) },
        trailingContent = { Switch(checked, { onToggle() }, enabled = enabled || !checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background.takeIf { handle != null } ?: Color.Transparent),
    )
}
