package com.neteinstein.goodcompany.feature.friends.ui.screens.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.TimelineFilter
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.ContentWidth
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.TimelineEntryRow
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.monthLong
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.chat_import
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_imported
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_invalid
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_matched
import com.neteinstein.goodcompany.feature.friends.resources.msg_timeline_removed
import com.neteinstein.goodcompany.feature.friends.resources.timeline_back
import com.neteinstein.goodcompany.feature.friends.resources.timeline_delete
import com.neteinstein.goodcompany.feature.friends.resources.timeline_empty
import com.neteinstein.goodcompany.feature.friends.resources.timeline_filter_all
import com.neteinstein.goodcompany.feature.friends.resources.timeline_filter_calls
import com.neteinstein.goodcompany.feature.friends.resources.timeline_filter_empty
import com.neteinstein.goodcompany.feature.friends.resources.timeline_filter_gifts
import com.neteinstein.goodcompany.feature.friends.resources.timeline_filter_messages
import com.neteinstein.goodcompany.feature.friends.resources.timeline_filter_notes
import com.neteinstein.goodcompany.feature.friends.resources.timeline_filter_together
import com.neteinstein.goodcompany.feature.friends.resources.timeline_log
import com.neteinstein.goodcompany.feature.friends.resources.timeline_more_options
import com.neteinstein.goodcompany.feature.friends.resources.timeline_screen_title
import com.neteinstein.goodcompany.feature.friends.resources.timeline_title
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.undo as coreUndo

@Composable
fun FriendTimelineScreen(
    friendId: FriendId,
    onBack: () -> Unit,
    onLog: () -> Unit,
    viewModel: FriendTimelineViewModel = koinViewModel(key = "timeline-${friendId.value}") { parametersOf(friendId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickChat = rememberTextFilePicker(onPicked = viewModel::onChatPicked)
    FriendTimelineContent(
        state = state,
        actions = FriendTimelineActions(
            onBack = onBack,
            onLog = onLog,
            onFilter = viewModel::onFilter,
            onDelete = viewModel::onDelete,
            onUndoDelete = viewModel::onUndoDelete,
            onImportChat = pickChat,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class FriendTimelineActions(
    val onBack: () -> Unit = {},
    val onLog: () -> Unit = {},
    val onFilter: (TimelineFilter) -> Unit = {},
    val onDelete: (TimelineEntry) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onImportChat: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

suspend fun FriendTimelineMessage.text(): String = when (this) {
    FriendTimelineMessage.Removed -> getString(Res.string.msg_timeline_removed)
    is FriendTimelineMessage.ChatImported -> getPluralString(Res.plurals.msg_chat_imported, messages, messages).let { base ->
        sender?.let { getString(Res.string.msg_chat_matched, base, it) } ?: base
    }
    FriendTimelineMessage.ChatInvalid -> getString(Res.string.msg_chat_invalid)
}

@Composable
fun timelineFilterLabel(filter: TimelineFilter): String = stringResource(
    when (filter) {
        TimelineFilter.ALL -> Res.string.timeline_filter_all
        TimelineFilter.TOGETHER -> Res.string.timeline_filter_together
        TimelineFilter.CALLS -> Res.string.timeline_filter_calls
        TimelineFilter.MESSAGES -> Res.string.timeline_filter_messages
        TimelineFilter.NOTES -> Res.string.timeline_filter_notes
        TimelineFilter.GIFTS -> Res.string.timeline_filter_gifts
    },
)

@Composable
fun FriendTimelineContent(state: FriendTimelineUiState, actions: FriendTimelineActions) {
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(CoreRes.string.coreUndo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showMessage(message.text(), if (state.undo != null) undoLabel else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete()
        actions.onMessageShown()
    }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        ContentWidth {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp)) {
                item(key = "header") { Header(state, actions) }
                item(key = "filters") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(TimelineFilter.entries) { f ->
                            FilterChip(state.filter == f, onClick = { actions.onFilter(f) }, label = { Text(timelineFilterLabel(f)) })
                        }
                    }
                    Spacer(Modifier.padding(top = 8.dp))
                }
                when {
                    state.isLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    !state.hasEntries -> item(key = "empty") {
                        EmptyState(Icons.Rounded.Add, stringResource(Res.string.timeline_title), stringResource(Res.string.timeline_empty))
                    }
                    state.months.isEmpty() -> item(key = "none") {
                        Text(
                            stringResource(Res.string.timeline_filter_empty),
                            Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.months.forEach { month ->
                    item(key = "m-${month.year}-${month.month.ordinal}") {
                        val label = monthLong(month.month).uppercase() + if (month.year != state.today.year) " ${month.year}" else ""
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 68.dp, top = 12.dp, bottom = 8.dp).semantics { heading() },
                        )
                    }
                    month.entries.forEachIndexed { i, entry ->
                        item(key = entry.key) {
                            DeletableRow(entry, enabled = entry.deletable, onDelete = { actions.onDelete(entry) }) {
                                TimelineEntryRow(entry, state.zone, last = i == month.entries.lastIndex)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: FriendTimelineUiState, actions: FriendTimelineActions) {
    var menu by remember { mutableStateOf(false) }
    val name = state.friend?.displayName?.substringBefore(' ').orEmpty()
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.timeline_back)) }
        Text(
            if (name.isEmpty()) stringResource(Res.string.timeline_title) else stringResource(Res.string.timeline_screen_title, name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        FilledIconButton(onClick = actions.onLog) { Icon(Icons.Rounded.Add, stringResource(Res.string.timeline_log)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.timeline_more_options)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_import)) },
                    leadingIcon = { Icon(Icons.Rounded.UploadFile, null) },
                    onClick = {
                        menu = false
                        actions.onImportChat()
                    },
                )
            }
        }
    }
}

/** Swipe left to delete (the snackbar offers Undo). Gifts can't be removed here. */
@Composable
private fun DeletableRow(entry: TimelineEntry, enabled: Boolean, onDelete: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val swipe = rememberSwipeToDismissBoxState()
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            swipe.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, stringResource(Res.string.timeline_delete), tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(4.dp))
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}
