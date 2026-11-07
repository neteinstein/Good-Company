package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.SoftCard
import com.neteinstein.goodcompany.core.ui.components.TagEditor
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.feature.friends.ui.components.ImportantEventDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.KidDialog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FriendDetailScreen(
    friendId: FriendId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: FriendDetailViewModel = koinViewModel(key = friendId.value) { parametersOf(friendId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickChat = rememberTextFilePicker(viewModel::onChatPicked)
    LaunchedEffect(state.deleted, state.notFound) { if (state.deleted) onBack() }
    FriendDetailContent(
        state = state,
        actions = FriendDetailActions(
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            onToggleClose = viewModel::onToggleClose,
            onToggleBirthdayNotifications = viewModel::onToggleBirthdayNotifications,
            onToggleSuggestEvents = viewModel::onToggleSuggestEvents,
            onMeetEveryChange = viewModel::onMeetEveryChange,
            onBirthdayChange = viewModel::onBirthdayChange,
            onAddLikes = viewModel::onAddLikes,
            onRemoveLike = viewModel::onRemoveLike,
            onAddDislikes = viewModel::onAddDislikes,
            onRemoveDislike = viewModel::onRemoveDislike,
            onNotesChange = viewModel::onNotesChange,
            onSaveKid = viewModel::onSaveKid,
            onRemoveKid = viewModel::onRemoveKid,
            onAddEvent = viewModel::onAddEvent,
            onRemoveEvent = viewModel::onRemoveEvent,
            onImportChat = pickChat,
            onGenerateIdeas = viewModel::onGenerateIdeas,
            onDismissIdea = viewModel::onDismissIdea,
            onDelete = viewModel::onDelete,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

/** All UI events of the detail screen, so the content composable stays previewable. */
data class FriendDetailActions(
    val onBack: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onToggleClose: () -> Unit = {},
    val onToggleBirthdayNotifications: () -> Unit = {},
    val onToggleSuggestEvents: () -> Unit = {},
    val onMeetEveryChange: (Int?) -> Unit = {},
    val onBirthdayChange: (LocalDate?) -> Unit = {},
    val onAddLikes: (List<String>) -> Unit = {},
    val onRemoveLike: (String) -> Unit = {},
    val onAddDislikes: (List<String>) -> Unit = {},
    val onRemoveDislike: (String) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onSaveKid: (id: String?, name: String, birthday: LocalDate?, likes: List<String>) -> Unit = { _, _, _, _ -> },
    val onRemoveKid: (String) -> Unit = {},
    val onAddEvent: (String, LocalDate, Int) -> Unit = { _, _, _ -> },
    val onRemoveEvent: (String) -> Unit = {},
    val onImportChat: () -> Unit = {},
    val onGenerateIdeas: () -> Unit = {},
    val onDismissIdea: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

private val MEET_OPTIONS = listOf(null to "Off", 7 to "Weekly", 14 to "2 weeks", 30 to "Monthly", 90 to "Quarterly")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FriendDetailContent(state: FriendDetailUiState, actions: FriendDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    var kidEditing by remember { mutableStateOf<Kid?>(null) }
    var addingKid by rememberSaveable { mutableStateOf(false) }
    var addingEvent by rememberSaveable { mutableStateOf(false) }
    var pickBirthday by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Remove friend") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val detail = state.detail
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text("This friend no longer exists.")
            }
            return@Scaffold
        }
        val f = detail.friend
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "hero") { Hero(f, state, actions, onEditBirthday = { pickBirthday = true }) }

            item(key = "toggles") {
                SoftCard {
                    Column {
                        ToggleRow(Icons.Rounded.Favorite, "Real friend", "Shows up first and in your agenda", f.isCloseFriend, actions.onToggleClose)
                        ToggleRow(
                            Icons.Rounded.Notifications,
                            "Birthday reminders",
                            "Them and their kids",
                            f.notifyBirthday,
                            actions.onToggleBirthdayNotifications,
                        )
                        ToggleRow(
                            Icons.Rounded.AutoAwesome,
                            "Weekly event ideas",
                            "AI suggests plans matching their tastes",
                            f.suggestEvents,
                            actions.onToggleSuggestEvents,
                        )
                    }
                }
            }

            item(key = "meet") {
                SectionHeader("Stay in touch")
                Text(
                    "Remind me if we haven't met in…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MEET_OPTIONS.forEach { (days, label) ->
                        FilterChip(f.meetEveryDays == days, { actions.onMeetEveryChange(days) }, label = { Text(label) })
                    }
                }
                LastContactRow(detail.lastContactByType, state)
            }

            item(key = "likes") {
                SectionHeader("Loves")
                TagEditor(f.likes, "Add likes — jazz, sushi, F1…", actions.onAddLikes, actions.onRemoveLike)
            }
            item(key = "dislikes") {
                SectionHeader("Not a fan of")
                TagEditor(
                    f.dislikes,
                    "Add dislikes",
                    actions.onAddDislikes,
                    actions.onRemoveDislike,
                    chipColor = MaterialTheme.colorScheme.errorContainer,
                )
            }

            item(key = "kids-header") {
                SectionHeader("Kids") { TextButton(onClick = { addingKid = true }) { Text("Add") } }
            }
            if (f.kids.isEmpty()) {
                item(key = "kids-empty") { Hint("Add their kids' names, birthdays and favourite things — handy before a party.") }
            }
            items(f.kids, key = { "kid-${it.id}" }) { kid ->
                ListItem(
                    modifier = Modifier.animateItem().clickable { kidEditing = kid },
                    leadingContent = { FriendAvatar(kid.name, kid.name.take(1).uppercase(), size = 40.dp) },
                    headlineContent = { Text(kid.name) },
                    supportingContent = {
                        val parts = listOfNotNull(
                            kid.birthday?.let { b ->
                                val next = b.nextOccurrence(state.today)
                                "🎂 ${relativeDay(next, state.today)}" + (b.ageOn(next)?.let { " · turns $it" } ?: "")
                            },
                            kid.likes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Likes "),
                        )
                        if (parts.isNotEmpty()) Text(parts.joinToString("\n"))
                    },
                    trailingContent = { IconButton(onClick = { actions.onRemoveKid(kid.id) }) { Icon(Icons.Rounded.Close, "Remove ${kid.name}") } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }

            item(key = "events-header") {
                SectionHeader("Important moments") { TextButton(onClick = { addingEvent = true }) { Text("Add") } }
            }
            if (f.importantEvents.isEmpty()) {
                item(key = "events-empty") { Hint("Surgery, exam, big interview? You'll get a nudge to check in.") }
            }
            items(f.importantEvents, key = { "event-${it.id}" }) { event ->
                ListItem(
                    modifier = Modifier.animateItem(),
                    leadingContent = { Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.tertiary) },
                    headlineContent = { Text(event.title) },
                    supportingContent = { Text(relativeDay(event.date, state.today) + " · " + event.date.longLabel()) },
                    trailingContent = { IconButton(onClick = { actions.onRemoveEvent(event.id) }) { Icon(Icons.Rounded.Close, "Remove") } },
                )
            }

            item(key = "ideas-header") {
                SectionHeader("Things to talk about") {
                    AnimatedContent(state.isGenerating) { busy ->
                        if (busy) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = if (state.hasAiKey) actions.onGenerateIdeas else actions.onOpenSettings) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.hasAiKey) "Find" else "Set up AI")
                            }
                        }
                    }
                }
            }
            if (detail.ideas.isEmpty()) {
                item(key = "ideas-empty") {
                    Hint(
                        if (state.hasAiKey) {
                            "Find news and conversation starters matched to what ${f.name.substringBefore(' ')} loves" +
                                if (detail.messageCount > 0) " and your recent chats." else "."
                        } else {
                            "Add your Anthropic API key in Settings to match their tastes with the news."
                        },
                    )
                }
            }
            items(detail.ideas, key = { "idea-${it.id}" }) { idea -> IdeaRow(idea, actions.onDismissIdea, Modifier.animateItem()) }

            item(key = "chat") {
                SectionHeader("Conversations")
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (detail.messageCount > 0) {
                                "${detail.messageCount} recent WhatsApp messages kept on this device as AI context."
                            } else {
                                "WhatsApp and Messenger don't let apps read your chats. In WhatsApp, open the chat → ⋮ → More → " +
                                    "Export chat → Without media, save the .txt and import it here."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = actions.onImportChat) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import WhatsApp export")
                        }
                    }
                }
            }

            item(key = "together-header") { SectionHeader("Together") }
            if (detail.hangouts.isEmpty()) {
                item(key = "together-empty") { Hint("Use “I'm with…” on the Together tab next time you meet.") }
            }
            items(detail.hangouts.take(20), key = { "h-${it.hangout.id}" }) { h ->
                val others = h.friends.filterNot { it.id == f.id }
                ListItem(
                    modifier = Modifier.animateItem(),
                    leadingContent = { Icon(if (others.isEmpty()) Icons.Rounded.Place else Icons.Rounded.Groups, null) },
                    headlineContent = { Text(h.hangout.place?.label ?: "Hung out") },
                    supportingContent = {
                        val date = h.hangout.at.toLocalDateTime(state.zone).date
                        Text(
                            relativePast(h.hangout.at, state.now, state.zone) + " · " + date.shortLabel() +
                                (if (others.isNotEmpty()) " · with ${others.joinToString { it.name.substringBefore(' ') }}" else ""),
                        )
                    },
                )
            }

            item(key = "notes") {
                SectionHeader("Notes")
                var notes by rememberSaveable(f.id.value) { mutableStateOf(f.notes) }
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        actions.onNotesChange(it)
                    },
                    placeholder = { Text("Partner's name, allergies, gift ideas…") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(140.dp),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(48.dp))
            }
        }

        if (addingKid || kidEditing != null) {
            val editing = kidEditing
            KidDialog(
                kid = editing,
                onDismiss = {
                    addingKid = false
                    kidEditing = null
                },
                onSave = { name, birthday, likes ->
                    actions.onSaveKid(editing?.id, name, birthday, likes)
                    addingKid = false
                    kidEditing = null
                },
            )
        }
        if (addingEvent) {
            ImportantEventDialog(state.today, { addingEvent = false }) { t, d, b ->
                actions.onAddEvent(t, d, b)
                addingEvent = false
            }
        }
        if (pickBirthday) {
            val initial = f.birthday?.let { LocalDate(it.year ?: 1990, Month(it.month), it.day) }
            LocalDatePickerDialog(initial, onDismiss = { pickBirthday = false }, onPicked = actions.onBirthdayChange)
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Remove ${f.name}?") },
                text = {
                    Text(
                        "Their tastes, kids and notes are deleted from Good Company. " +
                            if (f.contactId != null) "They'll come back as a plain contact on the next sync." else "",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        actions.onDelete()
                    }) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions, onEditBirthday: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)),
            )
            .padding(top = 8.dp, bottom = 16.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FriendAvatar(f.name, f.initials, size = 104.dp, highlighted = f.isCloseFriend, sharedKey = "avatar-${f.id.value}")
        Spacer(Modifier.height(12.dp))
        Text(f.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            val birthdayText = f.birthday?.let { b ->
                val next = b.nextOccurrence(state.today)
                relativeDay(next, state.today) + (b.ageOn(next)?.let { " · $it" } ?: "")
            } ?: "Add birthday"
            Box(Modifier.clip(CircleShape).clickable(onClick = onEditBirthday)) {
                InfoPill(birthdayText, icon = Icons.Rounded.Cake, emphasized = f.birthday != null)
            }
            state.detail?.lastSeen?.let { InfoPill("Seen ${relativePast(it, state.now, state.zone)}", icon = Icons.Rounded.Place) }
            if (f.isOrphaned) InfoPill("Not in contacts anymore")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val heart = if (f.isCloseFriend) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
            if (f.isCloseFriend) {
                Button(onClick = actions.onToggleClose) {
                    Icon(heart, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Real friend")
                }
            } else {
                FilledTonalButton(onClick = actions.onToggleClose) {
                    Icon(heart, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mark real friend")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { onToggle() }) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastContactRow(byType: Map<InteractionType, kotlin.time.Instant>, state: FriendDetailUiState) {
    if (byType.isEmpty()) return
    FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        byType.entries.sortedByDescending { it.value }.forEach { (type, at) ->
            val (icon, label) = when (type) {
                InteractionType.MEETUP -> Icons.Rounded.Groups to "Met"
                InteractionType.CALL -> Icons.Rounded.Call to "Call"
                InteractionType.WHATSAPP -> Icons.Rounded.Forum to "WhatsApp"
            }
            InfoPill("$label ${relativePast(at, state.now, state.zone)}", icon = icon)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun IdeaRow(idea: Idea, onDismiss: (String) -> Unit, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    SoftCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(idea.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onDismiss(idea.id) }) { Icon(Icons.Rounded.Close, "Dismiss") }
            }
            if (idea.description.isNotBlank()) Text(idea.description, style = MaterialTheme.typography.bodyMedium)
            AnimatedVisibility(idea.url != null) {
                TextButton(onClick = { idea.url?.let(uri::openUri) }) {
                    Text("Open")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 32.dp), color = MaterialTheme.colorScheme.surface)
}
