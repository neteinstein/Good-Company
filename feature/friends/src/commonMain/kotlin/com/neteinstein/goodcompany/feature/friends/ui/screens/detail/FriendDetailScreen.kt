package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.HeartBurst
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.QuickAction
import com.neteinstein.goodcompany.core.ui.components.ReminderEditor
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.TagEditor
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.feature.friends.ui.components.ImportantEventDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.KidDialog
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
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
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
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
            onTogetherNow = viewModel::onTogetherNow,
            onUndoTogether = viewModel::onUndoTogether,
            onOpenReminderEditor = viewModel::onOpenReminderEditor,
            onDismissReminderEditor = viewModel::onDismissReminderEditor,
            onSaveReminder = viewModel::onSaveReminder,
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
    val onTogetherNow: () -> Unit = {},
    val onUndoTogether: () -> Unit = {},
    val onOpenReminderEditor: () -> Unit = {},
    val onDismissReminderEditor: () -> Unit = {},
    val onSaveReminder: (String, List<FriendId>, LocalDate?, Int) -> Unit = { _, _, _, _ -> },
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
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = if (state.undoHangoutId != null) "Undo" else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoTogether()
        actions.onMessageShown()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val detail = state.detail
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text("This friend no longer exists.")
            }
            return@Scaffold
        }
        val f = detail.friend
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp)) {
            item(key = "hero") { Hero(f, state, actions, onEditBirthday = { pickBirthday = true }, onDelete = { confirmDelete = true }) }

            item(key = "touch") {
                SectionCard("Stay in touch", icon = Icons.Rounded.Schedule) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val goal = f.meetEveryDays
                        val days = detail.lastSeen?.toLocalDateTime(state.zone)?.date?.daysUntil(state.today)
                        val progress = if (goal != null && days != null) {
                            days.toFloat() / goal
                        } else if (goal != null) {
                            1f
                        } else {
                            0f
                        }
                        val overdue = goal != null && progress >= 1f
                        ProgressRing(
                            progress,
                            size = 64.dp,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        ) {
                            Text(days?.toString() ?: "–", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                detail.lastSeen?.let { "Last together ${relativePast(it, state.now, state.zone)}" } ?: "No meetups logged yet",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                when {
                                    goal == null -> "Pick how often you'd like to meet — we'll nudge you."
                                    overdue -> "It's been a while — your goal is every $goal days."
                                    else -> "Goal: every $goal days · ${goal - (days ?: 0)} days to go"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MEET_OPTIONS.forEach { (d, label) ->
                            FilterChip(f.meetEveryDays == d, { actions.onMeetEveryChange(d) }, label = { Text(label) })
                        }
                    }
                    LastContactRow(detail.lastContactByType, state)
                }
            }

            item(key = "tastes") {
                SectionCard("Tastes", icon = Icons.Rounded.ThumbUp) {
                    Label("Loves", Icons.Rounded.ThumbUp)
                    TagEditor(f.likes, "Add likes — jazz, sushi, F1…", actions.onAddLikes, actions.onRemoveLike)
                    Label("Not a fan of", Icons.Rounded.ThumbDown)
                    TagEditor(
                        f.dislikes,
                        "Add dislikes",
                        actions.onAddDislikes,
                        actions.onRemoveDislike,
                        chipColor = MaterialTheme.colorScheme.errorContainer,
                    )
                }
            }

            item(key = "kids") {
                SectionCard("Kids", icon = Icons.Rounded.ChildCare, action = { TextButton(onClick = { addingKid = true }) { Text("Add") } }) {
                    if (f.kids.isEmpty()) Hint("Names, birthdays and favourite things — handy before a party.")
                    f.kids.forEach { kid ->
                        ListItem(
                            modifier = Modifier.clickable { kidEditing = kid },
                            leadingContent = { FriendAvatar(kid.name, kid.name.take(1).uppercase(), size = 44.dp) },
                            headlineContent = { Text(kid.name, style = MaterialTheme.typography.titleSmall) },
                            supportingContent = {
                                val parts = listOfNotNull(
                                    kid.birthday?.let { b ->
                                        val next = b.nextOccurrence(state.today)
                                        "🎂 ${relativeDay(next, state.today)}" + (b.ageOn(next)?.let { " · turns $it" } ?: "")
                                    },
                                    kid.likes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Loves "),
                                )
                                if (parts.isNotEmpty()) Text(parts.joinToString("\n"))
                            },
                            trailingContent = {
                                IconButton(onClick = { actions.onRemoveKid(kid.id) }) { Icon(Icons.Rounded.Close, "Remove ${kid.name}") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            item(key = "moments") {
                SectionCard("Important moments", icon = Icons.Rounded.Favorite, action = {
                    TextButton(onClick = { addingEvent = true }) { Text("Add") }
                }) {
                    if (f.importantEvents.isEmpty()) Hint("Surgery, exam, big interview? You'll get a nudge to check in.")
                    f.importantEvents.forEach { event ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            DateBadge(event.date, state.today)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    relativeDay(event.date, state.today) +
                                        if (event.remindDaysBefore > 0) " · reminder ${event.remindDaysBefore}d before" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { actions.onRemoveEvent(event.id) }) { Icon(Icons.Rounded.Close, "Remove ${event.title}") }
                        }
                    }
                }
            }

            item(key = "ideas") {
                SectionCard(
                    "Things to talk about",
                    icon = Icons.Rounded.AutoAwesome,
                    action = {
                        AnimatedContent(state.isGenerating) { busy ->
                            if (busy) {
                                CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = if (state.hasAiKey) actions.onGenerateIdeas else actions.onOpenSettings) {
                                    Text(if (state.hasAiKey) "Find ideas" else "Set up AI")
                                }
                            }
                        }
                    },
                ) {
                    if (detail.ideas.isEmpty()) {
                        Hint(
                            if (state.hasAiKey) {
                                "News and conversation starters matched to what ${f.name.substringBefore(' ')} loves" +
                                    if (detail.messageCount > 0) " and your recent chats." else "."
                            } else {
                                "Add your Anthropic API key in Settings to match their tastes with the news."
                            },
                        )
                    }
                    detail.ideas.take(5).forEach { IdeaRow(it, actions.onDismissIdea) }
                }
            }

            item(key = "together") {
                SectionCard("Together", icon = Icons.Rounded.Diversity3) {
                    if (detail.hangouts.isEmpty()) Hint("Tap “Together now” above next time you meet.")
                    detail.hangouts.take(8).forEach { h ->
                        val others = h.friends.filterNot { it.id == f.id }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            DateBadge(h.hangout.at.toLocalDateTime(state.zone).date, state.today)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.hangout.place?.label ?: "Hung out", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(
                                        relativePast(h.hangout.at, state.now, state.zone),
                                        h.hangout.note.ifBlank {
                                            null
                                        },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (others.isNotEmpty()) StackedAvatars(others.map { it.name to it.initials }, size = 28.dp, max = 3)
                        }
                    }
                }
            }

            item(key = "chat") {
                SectionCard("Conversations", icon = Icons.Rounded.Forum) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (detail.messageCount > 0) {
                                "${detail.messageCount} recent WhatsApp messages kept on this device as AI context."
                            } else {
                                "WhatsApp and Messenger don't let apps read chats. In WhatsApp: open the chat → ⋮ → More → Export chat → " +
                                    "Without media, then import the .txt here."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = actions.onImportChat) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import WhatsApp export")
                        }
                    }
                }
            }

            item(key = "notes") {
                SectionCard("Notes", icon = Icons.AutoMirrored.Rounded.Notes) {
                    var notes by rememberSaveable(f.id.value) { mutableStateOf(f.notes) }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            actions.onNotesChange(it)
                        },
                        placeholder = { Text("Partner's name, allergies, gift ideas…") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(120.dp),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }

            item(key = "prefs") {
                SectionCard("Preferences", icon = Icons.Rounded.Tune) {
                    ToggleRow("Real friend", "Shows up first, on your home and agenda", f.isCloseFriend, actions.onToggleClose)
                    ToggleRow("Birthday reminders", "For them and their kids", f.notifyBirthday, actions.onToggleBirthdayNotifications)
                    ToggleRow("Weekly event ideas", "AI suggests plans that match their tastes", f.suggestEvents, actions.onToggleSuggestEvents)
                }
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
        if (state.showReminderEditor) {
            val people = (listOf(f) + detail.hangouts.flatMap { it.friends }).distinctBy { it.id }
            ReminderEditor(
                friends = people,
                today = state.today,
                onDismiss = actions.onDismissReminderEditor,
                onSave = actions.onSaveReminder,
                preselected = setOf(f.id),
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                icon = { Icon(Icons.Rounded.Delete, null) },
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
private fun Hero(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions, onEditBirthday: () -> Unit, onDelete: () -> Unit) {
    val (start, end) = avatarColors(f.name)
    val uri = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val phone = f.phoneNumbers.firstOrNull()
    val digits = phone?.filter { it.isDigit() || it == '+' }
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(start.copy(alpha = 0.55f), end.copy(alpha = 0.22f), MaterialTheme.colorScheme.background)),
            ),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val glass = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                FilledTonalIconButton(onClick = actions.onBack, colors = glass) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = actions.onToggleClose,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        contentColor = if (f.isCloseFriend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        if (f.isCloseFriend) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (f.isCloseFriend) "Unmark real friend" else "Mark as real friend",
                    )
                }
                FilledTonalIconButton(onClick = onDelete, colors = glass) { Icon(Icons.Rounded.Delete, "Remove friend") }
            }
            FriendAvatar(f.name, f.initials, size = 112.dp, highlighted = f.isCloseFriend, sharedKey = "avatar-${f.id.value}")
            Spacer(Modifier.height(12.dp))
            Text(
                f.name,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                val birthdayText = f.birthday?.let { b ->
                    val next = b.nextOccurrence(state.today)
                    val phrase = relativeDayPhrase(next, state.today)
                    (if (phrase.startsWith("on ")) next.shortLabel() else "${next.shortLabel()} · $phrase") +
                        (b.ageOn(next)?.let { " · turns $it" } ?: "")
                } ?: "Add birthday"
                Box(Modifier.clip(CircleShape).clickable(onClick = onEditBirthday)) {
                    InfoPill(birthdayText, icon = if (f.birthday != null) Icons.Rounded.Cake else Icons.Rounded.Edit, emphasized = f.birthday != null)
                }
                state.detail?.lastSeen?.let { InfoPill("Seen ${relativePast(it, state.now, state.zone)}", icon = Icons.Rounded.Place) }
                if (f.isOrphaned) InfoPill("Not in contacts anymore")
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    QuickAction(Icons.Rounded.Diversity3, "Together", onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onTogetherNow()
                    }, emphasized = true)
                    HeartBurst(state.celebrate, Modifier.size(72.dp))
                }
                QuickAction(Icons.Rounded.Call, "Call", onClick = {
                    digits?.let { uri.openUri("tel:$it") }
                }, enabled = digits != null, modifier = Modifier.weight(1f))
                QuickAction(Icons.Rounded.Sms, "Text", onClick = {
                    digits?.let { uri.openUri("sms:$it") }
                }, enabled = digits != null, modifier = Modifier.weight(1f))
                QuickAction(
                    Icons.AutoMirrored.Rounded.Chat,
                    "WhatsApp",
                    onClick = { digits?.let { uri.openUri("https://wa.me/" + it.filter(Char::isDigit)) } },
                    enabled = digits != null,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(Icons.Rounded.AlarmAdd, "Remind", onClick = actions.onOpenReminderEditor, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Label(text: String, icon: ImageVector) {
    Row(Modifier.padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { onToggle() }) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastContactRow(byType: Map<InteractionType, kotlin.time.Instant>, state: FriendDetailUiState) {
    if (byType.isEmpty()) return
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun IdeaRow(idea: Idea, onDismiss: (String) -> Unit) {
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(idea.title, style = MaterialTheme.typography.titleSmall)
            if (idea.description.isNotBlank()) {
                Text(
                    idea.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            idea.url?.let { url ->
                TextButton(onClick = { uri.openUri(url) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Open")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp))
                }
            }
        }
        IconButton(onClick = { onDismiss(idea.id) }) { Icon(Icons.Rounded.Close, "Dismiss") }
    }
}
