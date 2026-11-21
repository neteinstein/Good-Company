package com.neteinstein.goodcompany.feature.settings.ui.screens.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.usecase.PlaceSummary
import com.neteinstein.goodcompany.core.domain.usecase.TypedPlace
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.delete
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.places
import com.neteinstein.goodcompany.feature.settings.resources.places_deleted
import com.neteinstein.goodcompany.feature.settings.resources.places_empty
import com.neteinstein.goodcompany.feature.settings.resources.places_intro
import com.neteinstein.goodcompany.feature.settings.resources.places_meetups
import com.neteinstein.goodcompany.feature.settings.resources.places_merge
import com.neteinstein.goodcompany.feature.settings.resources.places_merge_hint
import com.neteinstein.goodcompany.feature.settings.resources.places_merge_title
import com.neteinstein.goodcompany.feature.settings.resources.places_merge_typed_hint
import com.neteinstein.goodcompany.feature.settings.resources.places_merged
import com.neteinstein.goodcompany.feature.settings.resources.places_more
import com.neteinstein.goodcompany.feature.settings.resources.places_nearby
import com.neteinstein.goodcompany.feature.settings.resources.places_no_location
import com.neteinstein.goodcompany.feature.settings.resources.places_pin
import com.neteinstein.goodcompany.feature.settings.resources.places_pinned
import com.neteinstein.goodcompany.feature.settings.resources.places_plans
import com.neteinstein.goodcompany.feature.settings.resources.places_rename
import com.neteinstein.goodcompany.feature.settings.resources.places_renamed
import com.neteinstein.goodcompany.feature.settings.resources.places_typed
import com.neteinstein.goodcompany.feature.settings.resources.places_usually
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

suspend fun PlacesMessage.text(): String = when (this) {
    is PlacesMessage.Deleted -> getString(Res.string.places_deleted, name)
    is PlacesMessage.Merged -> getString(Res.string.places_merged, into)
    is PlacesMessage.Renamed -> getString(Res.string.places_renamed, to)
    is PlacesMessage.Pinned -> getString(Res.string.places_pinned, name)
    PlacesMessage.NoLocation -> getString(Res.string.places_no_location)
}

@Composable
fun PlacesScreen(onBack: () -> Unit, viewModel: PlacesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // "Add coordinates" asks for the location first; the answer goes to the ViewModel with the place's name.
    var pinning by remember { mutableStateOf<String?>(null) }
    val requestLocation = rememberPermissionRequester(AppPermission.LOCATION) { granted ->
        pinning?.let { viewModel.onPinTyped(it, granted) }
        pinning = null
    }
    PlacesContent(
        state,
        PlacesActions(
            onBack = onBack,
            onRename = viewModel::onRename,
            onDelete = viewModel::onDelete,
            onUndoDelete = viewModel::onUndoDelete,
            onMerge = viewModel::onMerge,
            onMessageShown = viewModel::onMessageShown,
            onRenameTyped = viewModel::onRenameTyped,
            onPinTyped = { name ->
                pinning = name
                requestLocation()
            },
        ),
    )
}

data class PlacesActions(
    val onBack: () -> Unit = {},
    val onRename: (id: String, name: String) -> Unit = { _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onMerge: (fromId: String, intoId: String) -> Unit = { _, _ -> },
    val onMessageShown: () -> Unit = {},
    /** Places without GPS (issue #121): rename or merge (a name another one has), and add coordinates. */
    val onRenameTyped: (from: String, to: String) -> Unit = { _, _ -> },
    val onPinTyped: (String) -> Unit = {},
)

/** Settings → Places (F11). Names given to GPS spots; nothing here ever leaves the device. */
@Composable
fun PlacesContent(state: PlacesUiState, actions: PlacesActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showMessage(
            message.text(),
            if (message is PlacesMessage.Deleted) getString(CoreRes.string.undo) else null,
        )
        actions.onMessageShown()
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete()
    }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var merging by rememberSaveable { mutableStateOf<String?>(null) }
    var renamingTyped by rememberSaveable { mutableStateOf<String?>(null) }
    var mergingTyped by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                Text(
                    stringResource(Res.string.places),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp)) {
                    item {
                        Surface(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(
                                stringResource(Res.string.places_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    if (state.places.isEmpty() && state.typed.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Place,
                                title = stringResource(Res.string.places),
                                message = stringResource(Res.string.places_empty),
                            )
                        }
                    }
                    items(state.places, key = { it.place.id }) { summary ->
                        PlaceRow(
                            summary,
                            canMerge = state.places.size > 1,
                            onRename = { renaming = summary.place.id },
                            onMerge = { merging = summary.place.id },
                            onDelete = { actions.onDelete(summary.place.id) },
                        )
                    }
                    // Issue #121: every place used anywhere, also the ones with no GPS.
                    if (state.typed.isNotEmpty()) {
                        item(key = "typed-header") {
                            Text(
                                stringResource(Res.string.places_typed),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                            )
                        }
                    }
                    items(state.typed, key = { "typed-${it.name}" }) { place ->
                        TypedPlaceRow(
                            place,
                            canMerge = state.typed.size > 1,
                            onRename = { renamingTyped = place.name },
                            onMerge = { mergingTyped = place.name },
                            onPin = { actions.onPinTyped(place.name) },
                        )
                    }
                }
            }
        }
    }
    state.places.firstOrNull { it.place.id == renaming }?.let { summary ->
        RenameDialog(summary.place.name, onDismiss = { renaming = null }, onSave = { name ->
            actions.onRename(summary.place.id, name)
            renaming = null
        })
    }
    renamingTyped?.let { name ->
        RenameDialog(name, onDismiss = { renamingTyped = null }, onSave = { to ->
            actions.onRenameTyped(name, to)
            renamingTyped = null
        })
    }
    mergingTyped?.let { name ->
        TypedMergeDialog(name, state.typed.map { it.name } - name, onDismiss = { mergingTyped = null }, onMerge = { into ->
            actions.onRenameTyped(name, into)
            mergingTyped = null
        })
    }
    state.places.firstOrNull { it.place.id == merging }?.let { summary ->
        MergeDialog(summary, state.places, onDismiss = { merging = null }, onMerge = { intoId ->
            actions.onMerge(summary.place.id, intoId)
            merging = null
        })
    }
}

@Composable
private fun PlaceRow(summary: PlaceSummary, canMerge: Boolean, onRename: () -> Unit, onMerge: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val details = buildList {
        add(pluralStringResource(Res.plurals.places_meetups, summary.meetups, summary.meetups))
        if (summary.usually.isNotEmpty()) {
            add(stringResource(Res.string.places_usually, summary.usually.joinToString(", ") { it.displayName.substringBefore(' ') }))
        }
        summary.nearby?.let { add(stringResource(Res.string.places_nearby, it.distanceM.roundToInt(), it.place.name)) }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onRename),
        leadingContent = { Icon(Icons.Rounded.Place, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(summary.place.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (summary.nearby != null) {
                    TextButton(onClick = onMerge) {
                        Icon(Icons.AutoMirrored.Rounded.CallMerge, null)
                        Text(stringResource(Res.string.places_merge))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.places_more, summary.place.name)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.places_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = {
                                menu = false
                                onRename()
                            },
                        )
                        if (canMerge) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.places_merge)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CallMerge, null) },
                                onClick = {
                                    menu = false
                                    onMerge()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(CoreRes.string.delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** A place with no GPS: its meetups and plans, who you usually meet there; rename, merge, add coordinates. */
@Composable
private fun TypedPlaceRow(place: TypedPlace, canMerge: Boolean, onRename: () -> Unit, onMerge: () -> Unit, onPin: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val details = buildList {
        if (place.meetups > 0) add(pluralStringResource(Res.plurals.places_meetups, place.meetups, place.meetups))
        if (place.plans > 0) add(pluralStringResource(Res.plurals.places_plans, place.plans, place.plans))
        if (place.usually.isNotEmpty()) {
            add(stringResource(Res.string.places_usually, place.usually.joinToString(", ") { it.displayName.substringBefore(' ') }))
        }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onRename),
        leadingContent = { Icon(Icons.Rounded.LocationOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(place.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.places_more, place.name)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.places_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = {
                            menu = false
                            onRename()
                        },
                    )
                    if (canMerge) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.places_merge)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CallMerge, null) },
                            onClick = {
                                menu = false
                                onMerge()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.places_pin)) },
                        leadingIcon = { Icon(Icons.Rounded.MyLocation, null) },
                        onClick = {
                            menu = false
                            onPin()
                        },
                    )
                }
            }
        },
    )
}

/** Merge a place with no GPS into another spelling: its meetups and plans take that name. */
@Composable
private fun TypedMergeDialog(from: String, others: List<String>, onDismiss: () -> Unit, onMerge: (String) -> Unit) {
    var into by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.places_merge_title, from)) },
        text = {
            Column {
                Text(stringResource(Res.string.places_merge_typed_hint), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(others, key = { it }) { name ->
                        ListItem(
                            modifier = Modifier.clickable { into = name },
                            leadingContent = { RadioButton(selected = into == name, onClick = { into = name }) },
                            headlineContent = { Text(name) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { into?.let(onMerge) }, enabled = into != null) { Text(stringResource(Res.string.places_merge)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.places_rename)) },
        text = { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** Picks the place that keeps its name; the nearby one (if any) is picked to start with. */
@Composable
private fun MergeDialog(from: PlaceSummary, all: List<PlaceSummary>, onDismiss: () -> Unit, onMerge: (String) -> Unit) {
    val others = all.filter { it.place.id != from.place.id }
    var into by rememberSaveable { mutableStateOf(from.nearby?.place?.id ?: others.firstOrNull()?.place?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.places_merge_title, from.place.name)) },
        text = {
            Column {
                Text(stringResource(Res.string.places_merge_hint), style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(others, key = { it.place.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { into = p.place.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = into == p.place.id, onClick = { into = p.place.id })
                            Text(p.place.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { into?.let(onMerge) }, enabled = into != null) { Text(stringResource(Res.string.places_merge)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}
