package org.goodcompany.app.feature.settings.ui.screens.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.goodcompany.app.core.ui.components.AppSnackbarHost
import org.goodcompany.app.core.ui.components.showMessage
import org.goodcompany.app.core.ui.resources.cancel
import org.goodcompany.app.core.ui.resources.remove
import org.goodcompany.app.core.ui.resources.save
import org.goodcompany.app.core.ui.resources.undo
import org.goodcompany.app.feature.settings.resources.Res
import org.goodcompany.app.feature.settings.resources.activities_move_down
import org.goodcompany.app.feature.settings.resources.activities_move_up
import org.goodcompany.app.feature.settings.resources.activities_name
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import org.goodcompany.app.core.ui.resources.Res as CoreRes
import org.goodcompany.app.core.ui.resources.back as backLabel

/**
 * The words of one ordered-options screen: Settings → Activities (F3, issue #121) or Settings → How (issue #152). Both
 * read the same: a hint, the built-in ones on (drag to reorder) then off, then "Your …" with its own hint and an add
 * button.
 */
internal data class OrderedOptionsTexts(
    val title: String,
    val hint: String,
    val yours: String,
    val yoursHint: String,
    val add: String,
    val rename: String,
)

/**
 * A snackbar message for an ordered-options screen: its [text], and whether it offers Undo (a removed name of your own).
 */
internal data class OrderedOptionsMessage(val key: Any, val text: suspend () -> String, val withUndo: Boolean)

/** What the built-in rows look like. */
internal class OrderedOptionLook<T>(val icon: @Composable (T) -> ImageVector, val label: @Composable (T) -> String)

private val ROW_HEIGHT = 56.dp

/**
 * The shared layout of Settings → Activities and Settings → How (issue #152 keeps them consistent): [enabled] (dragged by
 * the handle, or moved with the accessibility actions; the last one can't be turned off unless [canTurnOff]), [disabled]
 * below a divider, then your own [custom] names (tap to rename, move up / down, remove with Undo) and "Add".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Any> OrderedOptionsContent(
    texts: OrderedOptionsTexts,
    look: OrderedOptionLook<T>,
    enabled: List<T>,
    disabled: List<T>,
    canTurnOff: Boolean,
    custom: List<String>,
    customIcon: @Composable (String) -> ImageVector,
    message: OrderedOptionsMessage?,
    actions: OrderedOptionsActions<T>,
    maxNameLength: Int = Int.MAX_VALUE,
    /** More below "Add" (Settings → How: what counts as catching up). */
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    // null: closed; "": a new one; else the name being renamed.
    var naming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message?.key) {
        val m = message ?: return@LaunchedEffect
        val result = snackbar.showMessage(m.text(), if (m.withUndo) undo else null)
        if (m.withUndo && result == SnackbarResult.ActionPerformed) actions.onUndoRemove()
        actions.onMessageShown()
    }
    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(texts.title, Modifier.semantics { heading() }) },
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
                texts.hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            EnabledOptions(enabled, canTurnOff, look, actions)
            if (disabled.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            disabled.forEach { option ->
                OptionRow(option, look, checked = false, enabled = true, onToggle = { actions.onToggle(option) }, modifier = Modifier.alpha(0.6f))
            }
            Text(
                texts.yours,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
            )
            Text(
                texts.yoursHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            custom.forEachIndexed { i, name ->
                CustomNameRow(
                    name,
                    customIcon(name),
                    canMoveUp = i > 0,
                    canMoveDown = i < custom.lastIndex,
                    onRename = { naming = name },
                    onMoveUp = { actions.onMoveCustom(i, i - 1) },
                    onMoveDown = { actions.onMoveCustom(i, i + 1) },
                    onRemove = { actions.onRemoveCustom(name) },
                )
            }
            TextButton(onClick = { naming = "" }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(texts.add)
            }
            footer()
            Spacer(Modifier.height(24.dp))
        }
    }
    naming?.let { current ->
        NameDialog(
            title = if (current.isEmpty()) texts.add else texts.rename,
            initial = current,
            maxLength = maxNameLength,
            onDismiss = { naming = null },
            onSave = { typed ->
                if (current.isEmpty()) actions.onAddCustom(typed) else actions.onRenameCustom(current, typed)
                naming = null
            },
        )
    }
}

/** The callbacks of [OrderedOptionsContent]. */
internal data class OrderedOptionsActions<T>(
    val onBack: () -> Unit,
    val onToggle: (T) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onAddCustom: (String) -> Unit,
    val onRenameCustom: (from: String, to: String) -> Unit,
    val onRemoveCustom: (String) -> Unit,
    val onMoveCustom: (from: Int, to: Int) -> Unit,
    val onUndoRemove: () -> Unit,
    val onMessageShown: () -> Unit,
)

/** One name of your own: tap to rename; move up / down; remove (with Undo). */
@Composable
private fun CustomNameRow(
    name: String,
    icon: ImageVector,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onRename),
        leadingContent = { Icon(icon, null) },
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

/** Name a new one, or rename one ([initial] not empty); at most [maxLength] characters. */
@Composable
private fun NameDialog(title: String, initial: String, maxLength: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name,
                { name = it.take(maxLength) },
                label = { Text(stringResource(Res.string.activities_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** The ones that are on, dragged by their handle (or moved with the accessibility actions). */
@Composable
private fun <T : Any> EnabledOptions(enabled: List<T>, canTurnOff: Boolean, look: OrderedOptionLook<T>, actions: OrderedOptionsActions<T>) {
    // A local copy follows the finger; the new order is saved once, when the drag ends.
    val order = remember(enabled) { enabled.toMutableStateList() }
    var dragged by remember { mutableStateOf<T?>(null) }
    var dragStart by remember { mutableStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(Res.string.activities_move_up)
    val moveDown = stringResource(Res.string.activities_move_down)
    order.forEachIndexed { index, option ->
        // Keyed, so a row keeps its gesture while the list reorders under the finger.
        key(option) {
            val isDragged = dragged == option
            val handle = Modifier.pointerInput(option) {
                detectDragGestures(
                    onDragStart = {
                        dragged = option
                        dragStart = order.indexOf(option)
                        offset = 0f
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        offset += amount.y
                        val current = order.indexOf(option)
                        val target = (current + (offset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                        if (target != current) {
                            order.add(target, order.removeAt(current))
                            offset -= (target - current) * rowPx
                        }
                    },
                    onDragEnd = {
                        val end = order.indexOf(option)
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
            OptionRow(
                option,
                look,
                checked = true,
                enabled = canTurnOff,
                onToggle = { actions.onToggle(option) },
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
private fun <T : Any> OptionRow(
    option: T,
    look: OrderedOptionLook<T>,
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
                Icon(look.icon(option), null)
            }
        },
        headlineContent = { Text(look.label(option)) },
        trailingContent = { Switch(checked, { onToggle() }, enabled = enabled || !checked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background.takeIf { handle != null } ?: Color.Transparent),
    )
}
