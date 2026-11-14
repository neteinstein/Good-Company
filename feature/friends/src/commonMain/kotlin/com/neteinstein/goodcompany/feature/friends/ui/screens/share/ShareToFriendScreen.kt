package com.neteinstein.goodcompany.feature.friends.ui.screens.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.RankedFriend
import com.neteinstein.goodcompany.core.domain.usecase.ShareChoice
import com.neteinstein.goodcompany.core.domain.usecase.ShareReason
import com.neteinstein.goodcompany.core.ui.components.CircleChip
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.LinkCard
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.LogDateChoice
import com.neteinstein.goodcompany.core.ui.components.QuotedText
import com.neteinstein.goodcompany.core.ui.components.attachmentKindLabel
import com.neteinstein.goodcompany.core.ui.components.logKindIcon
import com.neteinstein.goodcompany.core.ui.components.logKindLabel
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.share_add_typed
import com.neteinstein.goodcompany.feature.friends.resources.share_ai_hint
import com.neteinstein.goodcompany.feature.friends.resources.share_caption
import com.neteinstein.goodcompany.feature.friends.resources.share_circles
import com.neteinstein.goodcompany.feature.friends.resources.share_counts
import com.neteinstein.goodcompany.feature.friends.resources.share_counts_hint
import com.neteinstein.goodcompany.feature.friends.resources.share_failed
import com.neteinstein.goodcompany.feature.friends.resources.share_gift
import com.neteinstein.goodcompany.feature.friends.resources.share_gift_hint
import com.neteinstein.goodcompany.feature.friends.resources.share_how_count
import com.neteinstein.goodcompany.feature.friends.resources.share_image
import com.neteinstein.goodcompany.feature.friends.resources.share_mentioned
import com.neteinstein.goodcompany.feature.friends.resources.share_no_friends
import com.neteinstein.goodcompany.feature.friends.resources.share_note
import com.neteinstein.goodcompany.feature.friends.resources.share_note_hint
import com.neteinstein.goodcompany.feature.friends.resources.share_nothing_found
import com.neteinstein.goodcompany.feature.friends.resources.share_price
import com.neteinstein.goodcompany.feature.friends.resources.share_reason_number
import com.neteinstein.goodcompany.feature.friends.resources.share_reason_recent
import com.neteinstein.goodcompany.feature.friends.resources.share_reason_seen_today
import com.neteinstein.goodcompany.feature.friends.resources.share_save
import com.neteinstein.goodcompany.feature.friends.resources.share_save_for
import com.neteinstein.goodcompany.feature.friends.resources.share_search
import com.neteinstein.goodcompany.feature.friends.resources.share_suggest
import com.neteinstein.goodcompany.feature.friends.resources.share_title
import com.neteinstein.goodcompany.feature.friends.resources.share_who
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.back as backLabel
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
            onSelectCircle = viewModel::onSelectCircle,
            onAddTyped = viewModel::onAddTyped,
            onChangeFriend = viewModel::onChangeFriend,
            onChoice = viewModel::onChoice,
            onKind = viewModel::onKind,
            onDate = viewModel::onDate,
            onCaptionChange = viewModel::onCaptionChange,
            onPriceChange = viewModel::onPriceChange,
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
    val onSelectCircle: (FriendGroup) -> Unit = {},
    val onAddTyped: () -> Unit = {},
    val onChangeFriend: () -> Unit = {},
    val onChoice: (ShareChoice) -> Unit = {},
    val onKind: (LogKind) -> Unit = {},
    val onDate: (kotlinx.datetime.LocalDate) -> Unit = {},
    val onCaptionChange: (String) -> Unit = {},
    val onPriceChange: (String) -> Unit = {},
    val onSuggest: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

suspend fun ShareMessage.text(): String = when (this) {
    is ShareMessage.NothingFound -> getString(Res.string.share_nothing_found, firstName)
    is ShareMessage.AiError -> aiErrorText(error)
    ShareMessage.SaveFailed -> getString(Res.string.share_failed)
}

/** Who the confirm step is for, as a name: the person's first name or the circle's name. */
private val PickedTarget.label: String
    get() = when (this) {
        is PickedTarget.Person -> friend.name.substringBefore(' ')
        is PickedTarget.Circle -> circle.name
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
    val target = state.target
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).imePadding()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (target == null) {
                    IconButton(onClick = actions.onCancel) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.closeLabel)) }
                    Text(stringResource(Res.string.share_title), style = MaterialTheme.typography.titleLarge)
                } else {
                    IconButton(onClick = actions.onChangeFriend) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.backLabel))
                    }
                    Text(
                        stringResource(Res.string.share_save_for, target.label),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (target) {
                null -> {
                    state.content?.let { SharedSummary(it) }
                    FriendPicker(state, actions, Modifier.weight(1f))
                }
                else -> Confirm(target, state, actions, Modifier.weight(1f))
            }
        }
    }
}

/** Step 1's compact preview: a thumbnail, the text's first lines or the link. */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SharedSummary(content: SharedContent) {
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
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                val headline = content.title?.trim()?.takeIf { it.isNotEmpty() }
                    ?: content.text?.trim()?.takeIf { it.isNotEmpty() && it != content.url }
                Text(
                    headline ?: attachmentKindLabel(content.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
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
        if (!state.hasFriends && state.query.isBlank()) {
            Text(
                stringResource(Res.string.share_no_friends),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.friends, key = { it.friend.id.value }) { ranked -> PersonRow(ranked) { actions.onSelect(ranked.friend) } }
            if (state.circles.isNotEmpty()) {
                item(key = "circles") {
                    Text(
                        stringResource(Res.string.share_circles),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(state.circles, key = { "circle-" + it.id }) { circle ->
                    Row(
                        Modifier.fillMaxWidth().clickable { actions.onSelectCircle(circle) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleChip(circle, selected = false, onClick = {
                            actions.onSelectCircle(circle)
                        }, trailing = circle.memberIds.size.toString())
                    }
                }
            }
            if (state.canAddTyped) {
                item(key = "add") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = actions.onAddTyped),
                        leadingContent = { Icon(Icons.Rounded.PersonAdd, null) },
                        headlineContent = { Text(stringResource(Res.string.share_add_typed, state.query.trim())) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonRow(ranked: RankedFriend, onClick: () -> Unit) {
    val friend = ranked.friend
    val reason = when (ranked.reason) {
        ShareReason.MENTIONED -> stringResource(Res.string.share_mentioned)
        ShareReason.NUMBER -> stringResource(Res.string.share_reason_number)
        ShareReason.SEEN_TODAY -> stringResource(Res.string.share_reason_seen_today)
        ShareReason.RECENT -> stringResource(Res.string.share_reason_recent)
        null -> null
    }
    val strong = ranked.reason == ShareReason.MENTIONED || ranked.reason == ShareReason.NUMBER
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { FriendAvatar(friend, size = 44.dp, highlighted = strong) },
        headlineContent = { Text(friend.name) },
        supportingContent = reason?.let {
            {
                Text(
                    it,
                    color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Confirm(target: PickedTarget, state: ShareUiState, actions: ShareActions, modifier: Modifier) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            state.content?.let { BigPreview(it) }
            Text(stringResource(Res.string.share_how_count), style = MaterialTheme.typography.titleSmall)
            ChoiceCard(
                selected = state.choice == ShareChoice.INTERACTION,
                title = stringResource(Res.string.share_counts),
                hint = stringResource(Res.string.share_counts_hint),
                onClick = { actions.onChoice(ShareChoice.INTERACTION) },
            )
            ChoiceCard(
                selected = state.choice == ShareChoice.NOTE,
                title = stringResource(Res.string.share_note),
                hint = stringResource(Res.string.share_note_hint, target.label),
                onClick = { actions.onChoice(ShareChoice.NOTE) },
            )
            if (state.canBeGift) {
                ChoiceCard(
                    selected = state.choice == ShareChoice.GIFT_IDEA,
                    title = stringResource(Res.string.share_gift),
                    hint = stringResource(Res.string.share_gift_hint, target.label),
                    onClick = { actions.onChoice(ShareChoice.GIFT_IDEA) },
                )
            }
            if (state.choice == ShareChoice.INTERACTION) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHARE_KINDS.forEach { kind ->
                        FilterChip(
                            selected = state.kind == kind,
                            onClick = { actions.onKind(kind) },
                            label = { Text(logKindLabel(kind)) },
                            leadingIcon = { Icon(logKindIcon(kind), null, Modifier.size(18.dp)) },
                        )
                    }
                }
                LogDateChoice(state.date, state.today, actions.onDate, onPick = { pickingDate = true })
            }
            OutlinedTextField(
                value = state.caption,
                onValueChange = actions.onCaptionChange,
                label = { Text(stringResource(Res.string.share_caption)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.choice == ShareChoice.GIFT_IDEA) {
                OutlinedTextField(
                    value = state.price,
                    onValueChange = actions.onPriceChange,
                    label = { Text(stringResource(Res.string.share_price)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (target is PickedTarget.Person) {
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
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = actions.onSave,
            enabled = !state.isSaving,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 52.dp),
        ) {
            Text(stringResource(Res.string.share_save))
        }
    }
    if (pickingDate) {
        LocalDatePickerDialog(state.date, onDismiss = { pickingDate = false }, onPicked = actions.onDate, latest = state.today)
    }
}

/** Step 2's preview: the photo large, the link as a card, or the text quoted. */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun BigPreview(content: SharedContent) {
    when (content.kind) {
        AttachmentKind.PHOTO -> {
            val bitmap = remember(content.image) { content.image?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() } }
            if (bitmap != null) {
                Image(
                    bitmap,
                    stringResource(Res.string.share_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large),
                )
            }
        }
        AttachmentKind.LINK -> LinkCard(content.title, content.url.orEmpty(), null)
        AttachmentKind.TEXT -> QuotedText(content.text.orEmpty(), null, Modifier.fillMaxWidth(), maxLines = 8)
    }
}

/** A radio card: "Counts as an interaction" / "Just an idea or note" / "Gift idea". */
@Composable
private fun ChoiceCard(selected: Boolean, title: String, hint: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).selectable(selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
