package com.neteinstein.goodcompany.feature.friends.ui.screens.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.news_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.share_ai_hint
import com.neteinstein.goodcompany.feature.friends.resources.share_change
import com.neteinstein.goodcompany.feature.friends.resources.share_image
import com.neteinstein.goodcompany.feature.friends.resources.share_mentioned
import com.neteinstein.goodcompany.feature.friends.resources.share_no_friends
import com.neteinstein.goodcompany.feature.friends.resources.share_nothing_found
import com.neteinstein.goodcompany.feature.friends.resources.share_save
import com.neteinstein.goodcompany.feature.friends.resources.share_search
import com.neteinstein.goodcompany.feature.friends.resources.share_suggest
import com.neteinstein.goodcompany.feature.friends.resources.share_title
import com.neteinstein.goodcompany.feature.friends.resources.share_whats_new
import com.neteinstein.goodcompany.feature.friends.resources.share_who
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.close as closeLabel

@Composable
fun ShareToFriendScreen(onClose: () -> Unit, onSaved: (FriendId) -> Unit, viewModel: ShareToFriendViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedTo) { state.savedTo?.let(onSaved) }
    LaunchedEffect(state.closed, state.isLoading) { if (state.closed && !state.isLoading) onClose() }
    ShareToFriendContent(
        state = state,
        actions = ShareActions(
            onQueryChange = viewModel::onQueryChange,
            onSelect = viewModel::onSelect,
            onChangeFriend = viewModel::onChangeFriend,
            onDraftChange = viewModel::onDraftChange,
            onSuggest = viewModel::onSuggest,
            onSave = viewModel::onSave,
            onCancel = viewModel::onCancel,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class ShareActions(
    val onQueryChange: (String) -> Unit = {},
    val onSelect: (Friend) -> Unit = {},
    val onChangeFriend: () -> Unit = {},
    val onDraftChange: (String) -> Unit = {},
    val onSuggest: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

suspend fun ShareMessage.text(): String = when (this) {
    is ShareMessage.NothingFound -> getString(Res.string.share_nothing_found, firstName)
    is ShareMessage.AiError -> aiErrorText(error)
}

@Composable
fun ShareToFriendContent(state: ShareUiState, actions: ShareActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.text())
            actions.onMessageShown()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).imePadding()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onCancel) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.closeLabel)) }
                Text(stringResource(Res.string.share_title), style = MaterialTheme.typography.titleLarge)
            }
            state.content?.let { SharedPreview(it) }
            val selected = state.selected
            when {
                !state.hasFriends -> Text(
                    stringResource(Res.string.share_no_friends),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selected == null -> FriendPicker(state, actions, Modifier.weight(1f))
                else -> Composer(selected, state, actions, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SharedPreview(content: SharedContent) {
    val bitmap = remember(content.image) { content.image?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() } }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    stringResource(Res.string.share_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                val text = content.text?.trim()?.takeIf { it.isNotEmpty() && it != content.url }
                when {
                    text != null -> Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    bitmap != null -> Text(stringResource(Res.string.share_image), style = MaterialTheme.typography.bodyMedium)
                }
                content.url?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendPicker(state: ShareUiState, actions: ShareActions, modifier: Modifier) {
    Column(modifier) {
        Text(
            stringResource(Res.string.share_who),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        TextField(
            value = state.query,
            onValueChange = actions.onQueryChange,
            placeholder = { Text(stringResource(Res.string.share_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.friends, key = { it.id.value }) { friend ->
                ListItem(
                    modifier = Modifier.clickable { actions.onSelect(friend) },
                    leadingContent = { FriendAvatar(friend.name, friend.initials, size = 44.dp, highlighted = friend.isCloseFriend) },
                    headlineContent = { Text(friend.name) },
                    supportingContent = if (friend.id in state.mentioned) {
                        { Text(stringResource(Res.string.share_mentioned), color = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun Composer(friend: Friend, state: ShareUiState, actions: ShareActions, modifier: Modifier) {
    val firstName = friend.name.substringBefore(' ')
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FriendAvatar(friend.name, friend.initials, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(friend.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = actions.onChangeFriend) { Text(stringResource(Res.string.share_change)) }
        }
        OutlinedTextField(
            value = state.draft,
            onValueChange = actions.onDraftChange,
            label = { Text(stringResource(Res.string.share_whats_new, firstName)) },
            placeholder = { Text(stringResource(Res.string.news_placeholder)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
        if (state.hasAiKey) {
            FilledTonalButton(onClick = actions.onSuggest, enabled = !state.isSuggesting) {
                if (state.isSuggesting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.share_suggest))
            }
        } else if (state.content?.image != null) {
            Text(
                stringResource(Res.string.share_ai_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.CenterEnd) {
            Button(onClick = actions.onSave, enabled = state.draft.isNotBlank(), shape = CircleShape) {
                Text(stringResource(Res.string.share_save))
            }
        }
    }
}
