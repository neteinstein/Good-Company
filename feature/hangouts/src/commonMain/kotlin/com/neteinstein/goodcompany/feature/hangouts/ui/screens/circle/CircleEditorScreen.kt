package com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.CircleBackdrop
import com.neteinstein.goodcompany.core.ui.components.CircleColorSwatch
import com.neteinstein.goodcompany.core.ui.components.CropShape
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.PeoplePickerSheet
import com.neteinstein.goodcompany.core.ui.components.PhotoCropDialog
import com.neteinstein.goodcompany.core.ui.components.circlePalette
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.components.showUndo
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.platform.rememberImagePicker
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_closeness_note
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_colour
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_editor_edit
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_editor_new
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_emoji
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_members
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_members_empty
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_name
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_remind
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_remind_off
import com.neteinstein.goodcompany.feature.hangouts.resources.circle_remind_on
import com.neteinstein.goodcompany.feature.hangouts.resources.cover_add
import com.neteinstein.goodcompany.feature.hangouts.resources.cover_change
import com.neteinstein.goodcompany.feature.hangouts.resources.cover_remove
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_cover_removed
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_cover_unreadable
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_group_deleted
import com.neteinstein.goodcompany.feature.hangouts.resources.page_cover
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.add as addLabel
import com.neteinstein.goodcompany.core.ui.resources.back as backLabel
import com.neteinstein.goodcompany.core.ui.resources.delete as deleteLabel
import com.neteinstein.goodcompany.core.ui.resources.remove_item as removeItem
import com.neteinstein.goodcompany.core.ui.resources.save as saveLabel
import com.neteinstein.goodcompany.core.ui.resources.undo as undoLabel

/** Opened from Together's "Your circles", a long-press on a Home circle chip and the circle pills on a friend page. */
@Composable
fun CircleEditorScreen(
    groupId: String?,
    onClose: () -> Unit,
    /** A new circle that starts with this person in (issue #70). */
    withFriend: FriendId? = null,
    viewModel: CircleEditorViewModel = koinViewModel(key = "circle-$groupId-${withFriend?.value}") { parametersOf(groupId, withFriend) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    // Cover photo (issue #88): a picked picture is cropped wide first; one the picker couldn't read goes straight on.
    var cropping by remember { mutableStateOf<ByteArray?>(null) }
    val pickCover = rememberImagePicker { image ->
        if (image != null && image.isNotEmpty()) cropping = image else viewModel.onCoverPicked(image)
    }
    cropping?.let { image ->
        PhotoCropDialog(image, onDismiss = { cropping = null }, shape = CropShape.WIDE, onCropped = { cropped ->
            cropping = null
            viewModel.onCoverPicked(cropped)
        })
    }
    CircleEditorContent(
        state,
        CircleEditorActions(
            onName = viewModel::onName,
            onEmoji = viewModel::onEmoji,
            onColor = viewModel::onColor,
            onRemind = viewModel::onRemind,
            onDays = viewModel::onDays,
            onMembers = viewModel::onMembers,
            onRemoveMember = viewModel::onRemoveMember,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onUndoDelete = viewModel::onUndoDelete,
            onMessageShown = viewModel::onMessageShown,
            onBack = onClose,
            onPickCover = pickCover,
            onRemoveCover = viewModel::onRemoveCover,
            onUndoRemoveCover = viewModel::onUndoRemoveCover,
        ),
    )
}

data class CircleEditorActions(
    val onName: (String) -> Unit = {},
    val onEmoji: (String) -> Unit = {},
    val onColor: (CircleColor) -> Unit = {},
    val onRemind: (Boolean) -> Unit = {},
    val onDays: (Int) -> Unit = {},
    val onMembers: (List<com.neteinstein.goodcompany.core.domain.model.FriendId>) -> Unit = {},
    val onRemoveMember: (com.neteinstein.goodcompany.core.domain.model.FriendId) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onPickCover: () -> Unit = {},
    val onRemoveCover: () -> Unit = {},
    val onUndoRemoveCover: () -> Unit = {},
)

/** Localised snackbar text for a [CircleEditorMessage]. */
suspend fun CircleEditorMessage.text(): String = when (this) {
    is CircleEditorMessage.Deleted -> getString(Res.string.msg_group_deleted, name)
    CircleEditorMessage.CoverRemoved -> getString(Res.string.msg_cover_removed)
    CircleEditorMessage.CoverUnreadable -> getString(Res.string.msg_cover_unreadable)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CircleEditorContent(state: CircleEditorUiState, actions: CircleEditorActions) {
    val snackbar = remember { SnackbarHostState() }
    var picking by rememberSaveable { mutableStateOf(false) }
    val undo = stringResource(CoreRes.string.undoLabel)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        when (message) {
            is CircleEditorMessage.Deleted -> {
                val result = snackbar.showUndo(message.text(), undo)
                if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete() else actions.onMessageShown()
            }
            CircleEditorMessage.CoverRemoved -> {
                if (snackbar.showUndo(message.text(), undo) == SnackbarResult.ActionPerformed) actions.onUndoRemoveCover()
                actions.onMessageShown()
            }
            CircleEditorMessage.CoverUnreadable -> {
                snackbar.showMessage(message.text())
                actions.onMessageShown()
            }
        }
    }
    val palette = circlePalette(state.color)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.isNew) Res.string.circle_editor_new else Res.string.circle_editor_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.backLabel)) }
                },
                actions = {
                    if (!state.isNew) {
                        TextButton(onClick = actions.onDelete, enabled = state.deleted == null) {
                            Text(stringResource(CoreRes.string.deleteLabel), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = actions.onSave,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
                ) { Text(stringResource(CoreRes.string.saveLabel)) }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "identity") {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(palette.container, CircleShape), contentAlignment = Alignment.Center) {
                        Text(state.emoji, style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        state.emoji,
                        actions.onEmoji,
                        label = { Text(stringResource(Res.string.circle_emoji)) },
                        singleLine = true,
                        modifier = Modifier.width(88.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        state.name,
                        actions.onName,
                        label = { Text(stringResource(Res.string.circle_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "colour") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(stringResource(Res.string.circle_colour), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircleColor.entries.forEach { c -> CircleColorSwatch(c, selected = c == state.color, onClick = { actions.onColor(c) }) }
                    }
                }
            }
            item(key = "cover") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(stringResource(Res.string.page_cover), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The card as it will look on the Circles tab: the cover under a veil in the colour.
                        CircleBackdrop(
                            FriendGroup(id = "", name = state.name, emoji = state.emoji, color = state.color, cover = state.cover),
                            Modifier.width(112.dp).height(63.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {}
                        OutlinedButton(onClick = actions.onPickCover) {
                            Text(stringResource(if (state.cover == null) Res.string.cover_add else Res.string.cover_change))
                        }
                        if (state.cover != null) {
                            TextButton(onClick = actions.onRemoveCover) { Text(stringResource(Res.string.cover_remove)) }
                        }
                    }
                }
            }
            item(key = "remind") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.circle_remind), style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (state.remind) {
                                    stringResource(Res.string.circle_remind_on, everyNDays(state.days))
                                } else {
                                    stringResource(Res.string.circle_remind_off)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(state.remind, actions.onRemind)
                    }
                    if (state.remind) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (CircleEditorUiState.RHYTHMS + listOf(state.days)).distinct().sorted().forEach { d ->
                                FilterChip(state.days == d, { actions.onDays(d) }, label = { Text(everyNDays(d)) })
                            }
                        }
                    }
                }
            }
            item(key = "members-h") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(Res.plurals.circle_members, state.members.size, state.members.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { picking = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(CoreRes.string.addLabel))
                    }
                }
            }
            if (state.members.isEmpty() && !state.isLoading) {
                item(key = "members-empty") {
                    Text(
                        stringResource(Res.string.circle_members_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            items(state.members, key = { it.id.value }) { f -> MemberRow(f, onRemove = { actions.onRemoveMember(f.id) }) }
            item(key = "note") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        stringResource(Res.string.circle_closeness_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
    if (picking) {
        PeoplePickerSheet(
            state.everyone,
            state.circles,
            chosen = state.members.map { it.id },
            onDismiss = { picking = false },
            onDone = {
                actions.onMembers(it)
                picking = false
            },
            requireSomeone = true,
        )
    }
}

/** A member with their closeness, read-only (it belongs to the person, not the circle). */
@Composable
private fun MemberRow(friend: Friend, onRemove: () -> Unit) {
    ListItem(
        modifier = Modifier.widthIn(max = 720.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        leadingContent = { FriendAvatar(friend, size = 36.dp) },
        headlineContent = { Text(friend.displayName) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoPill(closenessLabel(friend.closeness, short = true))
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.removeItem, friend.displayName)) }
            }
        },
    )
}
