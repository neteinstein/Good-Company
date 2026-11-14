package com.neteinstein.goodcompany.feature.settings.ui.screens.tags

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.usecase.Tag
import com.neteinstein.goodcompany.core.domain.usecase.Tags
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.msg_tag_deleted
import com.neteinstein.goodcompany.feature.settings.resources.msg_tag_merged
import com.neteinstein.goodcompany.feature.settings.resources.msg_tag_renamed
import com.neteinstein.goodcompany.feature.settings.resources.tags_both
import com.neteinstein.goodcompany.feature.settings.resources.tags_dislike_short
import com.neteinstein.goodcompany.feature.settings.resources.tags_edit
import com.neteinstein.goodcompany.feature.settings.resources.tags_empty
import com.neteinstein.goodcompany.feature.settings.resources.tags_keep_both
import com.neteinstein.goodcompany.feature.settings.resources.tags_like_short
import com.neteinstein.goodcompany.feature.settings.resources.tags_merge
import com.neteinstein.goodcompany.feature.settings.resources.tags_name
import com.neteinstein.goodcompany.feature.settings.resources.tags_people_dislike
import com.neteinstein.goodcompany.feature.settings.resources.tags_people_like
import com.neteinstein.goodcompany.feature.settings.resources.tags_same
import com.neteinstein.goodcompany.feature.settings.resources.tags_search
import com.neteinstein.goodcompany.feature.settings.resources.tags_title
import com.neteinstein.goodcompany.feature.settings.resources.tags_will_merge
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.back as backLabel
import com.neteinstein.goodcompany.core.ui.resources.cancel as cancelLabel
import com.neteinstein.goodcompany.core.ui.resources.delete as deleteLabel
import com.neteinstein.goodcompany.core.ui.resources.save as saveLabel
import com.neteinstein.goodcompany.core.ui.resources.tags_chip_count as chipCount
import com.neteinstein.goodcompany.core.ui.resources.undo as undoLabel

/** Localised snackbar text for a [TagsMessage]. */
suspend fun TagsMessage.text(): String = when (this) {
    is TagsMessage.Renamed -> getString(Res.string.msg_tag_renamed, to)
    is TagsMessage.Merged -> getString(Res.string.msg_tag_merged, into)
    is TagsMessage.Deleted -> getString(Res.string.msg_tag_deleted, name)
}

@Composable
fun TagsScreen(onBack: () -> Unit, viewModel: TagsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TagsContent(
        state,
        TagsActions(
            onBack = onBack,
            onQueryChange = viewModel::onQueryChange,
            onRename = viewModel::onRename,
            onMerge = viewModel::onMerge,
            onKeepBoth = viewModel::onKeepBoth,
            onDelete = viewModel::onDelete,
            onUndoDelete = viewModel::onUndoDelete,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class TagsActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onRename: (Tag, String) -> Unit = { _, _ -> },
    val onMerge: (List<Tag>) -> Unit = {},
    val onKeepBoth: (List<Tag>) -> Unit = {},
    val onDelete: (Tag) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsContent(state: TagsUiState, actions: TagsActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undoLabel)
    var searching by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tag?>(null) }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val withUndo = message is TagsMessage.Deleted
        val result = snackbar.showSnackbar(message.text(), actionLabel = if (withUndo) undo else null, duration = SnackbarDuration.Short)
        if (withUndo && result == SnackbarResult.ActionPerformed) actions.onUndoDelete()
        actions.onMessageShown()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tags_title), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.backLabel)) }
                },
                actions = {
                    IconButton(onClick = { searching = !searching }) { Icon(Icons.Rounded.Search, stringResource(Res.string.tags_search)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            if (searching || state.query.isNotEmpty()) {
                item(key = "search") {
                    OutlinedTextField(
                        state.query,
                        actions.onQueryChange,
                        placeholder = { Text(stringResource(Res.string.tags_search)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(state.duplicates, key = { group -> "dup-" + Tags.signature(group.map { it.name }) }) { group ->
                DuplicateCard(group, onMerge = { actions.onMerge(group) }, onKeepBoth = { actions.onKeepBoth(group) })
            }
            if (!state.isLoading && state.tags.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(Res.string.tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            items(state.shown, key = { "tag-" + it.name }) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(tagCounts(tag)) },
                    trailingContent = {
                        IconButton(onClick = { editing = tag }) { Icon(Icons.Rounded.Edit, stringResource(Res.string.tags_edit, tag.name)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    editing?.let { tag ->
        EditTagDialog(
            tag = tag,
            others = state.tags.filter { it.name != tag.name },
            onDismiss = { editing = null },
            onRename = {
                actions.onRename(tag, it)
                editing = null
            },
            onDelete = {
                actions.onDelete(tag)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicateCard(group: List<Tag>, onMerge: () -> Unit, onKeepBoth: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.tags_same), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { tag -> SuggestionChip(onClick = {}, label = { Text(stringResource(CoreRes.string.chipCount, tag.name, tag.uses)) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onMerge()
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.MergeType, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(Res.string.tags_merge))
                }
                OutlinedButton(onClick = onKeepBoth, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.tags_keep_both)) }
            }
        }
    }
}

/** "4 people like", "3 people don't like" or "4 like · 1 doesn't". */
@Composable
private fun tagCounts(tag: Tag): String {
    val likes = tag.likedBy.size
    val dislikes = tag.dislikedBy.size
    return when {
        dislikes == 0 -> pluralStringResource(Res.plurals.tags_people_like, likes, likes)
        likes == 0 -> pluralStringResource(Res.plurals.tags_people_dislike, dislikes, dislikes)
        else -> stringResource(
            Res.string.tags_both,
            pluralStringResource(Res.plurals.tags_like_short, likes, likes),
            pluralStringResource(Res.plurals.tags_dislike_short, dislikes, dislikes),
        )
    }
}

/** The pencil: rename (merging when the new name is another tag) or delete for everyone. */
@Composable
private fun EditTagDialog(tag: Tag, others: List<Tag>, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable(tag.name) { mutableStateOf(tag.name) }
    val mergeWith = name.trim().takeIf { it.isNotEmpty() && it != tag.name }?.let { typed ->
        others.firstOrNull { it.name == typed } ?: others.firstOrNull { it.key == Tags.normalize(typed) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.tags_edit, tag.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(Res.string.tags_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                mergeWith?.let {
                    Text(
                        stringResource(Res.string.tags_will_merge, it.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(CoreRes.string.deleteLabel))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && name.trim() != tag.name) {
                Text(stringResource(if (mergeWith != null) Res.string.tags_merge else CoreRes.string.saveLabel))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancelLabel)) } },
    )
}
