package com.neteinstein.goodcompany.feature.hangouts.ui.screens.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.CirclePage
import com.neteinstein.goodcompany.core.domain.usecase.CircleUpcoming
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.Timeline
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.isBuiltIn
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.AttachmentPreview
import com.neteinstein.goodcompany.core.ui.components.CircleBackdrop
import com.neteinstein.goodcompany.core.ui.components.CircleReminderEditor
import com.neteinstein.goodcompany.core.ui.components.CropShape
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.PhotoCropDialog
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.QuickAction
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.TimelineEntryRow
import com.neteinstein.goodcompany.core.ui.components.activityIcon
import com.neteinstein.goodcompany.core.ui.components.activityLabel
import com.neteinstein.goodcompany.core.ui.components.circlePalette
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.monthLong
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.platform.rememberImagePicker
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.core.ui.resources.delete
import com.neteinstein.goodcompany.core.ui.resources.edit
import com.neteinstein.goodcompany.core.ui.resources.plan_make
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.core.ui.resources.reminder_no_date
import com.neteinstein.goodcompany.core.ui.resources.tl_activity_at
import com.neteinstein.goodcompany.core.ui.resources.tl_together
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.cover_add
import com.neteinstein.goodcompany.feature.hangouts.resources.cover_change
import com.neteinstein.goodcompany.feature.hangouts.resources.cover_remove
import com.neteinstein.goodcompany.feature.hangouts.resources.group_met
import com.neteinstein.goodcompany.feature.hangouts.resources.group_more
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snooze_week
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snooze_weekend
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_cover_removed
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_cover_unreadable
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_entry_deleted
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_group_snoozed
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_reminder_saved
import com.neteinstein.goodcompany.feature.hangouts.resources.page_coming_up
import com.neteinstein.goodcompany.feature.hangouts.resources.page_cover
import com.neteinstein.goodcompany.feature.hangouts.resources.page_empty
import com.neteinstein.goodcompany.feature.hangouts.resources.page_everyone
import com.neteinstein.goodcompany.feature.hangouts.resources.page_members_at
import com.neteinstein.goodcompany.feature.hangouts.resources.page_people
import com.neteinstein.goodcompany.feature.hangouts.resources.page_reminder
import com.neteinstein.goodcompany.feature.hangouts.resources.page_together
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.circleStatusLine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** A circle's page (issue #88): header, actions, people, "Coming up", then the timeline, in one scrolling list. */
@Composable
fun CirclePageScreen(
    circleId: String,
    onBack: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    /** "We met": the small panel on this circle, members listed, nobody ticked (F20). */
    onWeMet: (String) -> Unit,
    /** "Edit": the circle editor (never for the built-in Family circle). */
    onEdit: (String) -> Unit,
    /** ⋮ → Delete: back to the Circles tab, which deletes it and offers Undo. */
    onDelete: (String) -> Unit,
    viewModel: CirclePageViewModel = koinViewModel(key = "circle-$circleId") { parametersOf(circleId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Deleted elsewhere: the page closes.
    LaunchedEffect(state.isGone) { if (state.isGone) onBack() }
    var cropping by remember { mutableStateOf<ByteArray?>(null) }
    val pickCover = rememberImagePicker { image ->
        if (image != null && image.isNotEmpty()) cropping = image else viewModel.onCoverPicked(image)
    }
    CirclePageContent(
        state = state,
        actions = CirclePageActions(
            onBack = onBack,
            onOpenFriend = onOpenFriend,
            onWeMet = { onWeMet(circleId) },
            onPlan = viewModel::onNewPlan,
            onReminder = viewModel::onNewReminder,
            onEdit = { onEdit(circleId) },
            onDelete = { onDelete(circleId) },
            onSnooze = viewModel::onSnooze,
            onPickCover = pickCover,
            onRemoveCover = viewModel::onRemoveCover,
            onOpenPlan = viewModel::onOpenPlan,
            onOpenReminder = viewModel::onOpenReminder,
            onDeleteEntry = viewModel::onDeleteEntry,
            onUndo = viewModel::onUndo,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
    val page = state.page ?: return
    state.planEditor?.let { editor ->
        PlanEditor(
            friends = state.friends,
            today = state.today,
            onDismiss = viewModel::onDismissPlan,
            onSave = viewModel::onSavePlan,
            initial = editor.plan,
            circles = state.circles,
            preselectedCircle = page.group.id,
        )
    }
    state.reminderEditor?.let { editor ->
        val due = editor.reminder?.dueAt?.toLocalDateTime(state.zone)
        CircleReminderEditor(
            friends = state.friends,
            today = state.today,
            circle = page.group,
            onDismiss = viewModel::onDismissReminder,
            onSave = viewModel::onSaveReminder,
            initial = editor.reminder,
            initialDate = due?.date,
            initialHour = due?.hour ?: com.neteinstein.goodcompany.core.ui.components.DEFAULT_HOUR,
        )
    }
    cropping?.let { image ->
        PhotoCropDialog(image, onDismiss = { cropping = null }, shape = CropShape.WIDE, onCropped = { cropped ->
            cropping = null
            viewModel.onCoverPicked(cropped)
        })
    }
}

data class CirclePageActions(
    val onBack: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onWeMet: () -> Unit = {},
    val onPlan: () -> Unit = {},
    val onReminder: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onSnooze: (SnoozeOption) -> Unit = {},
    val onPickCover: () -> Unit = {},
    val onRemoveCover: () -> Unit = {},
    val onOpenPlan: (Plan) -> Unit = {},
    val onOpenReminder: (Reminder) -> Unit = {},
    val onDeleteEntry: (TimelineEntry) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Localised snackbar text for a [CirclePageMessage]. */
suspend fun CirclePageMessage.text(): String = when (this) {
    is CirclePageMessage.Snoozed -> getString(Res.string.msg_group_snoozed, name, until.shortLabelText())
    CirclePageMessage.PlanSaved -> getString(CoreRes.string.plan_saved)
    CirclePageMessage.ReminderSaved -> getString(Res.string.msg_reminder_saved)
    CirclePageMessage.EntryDeleted -> getString(Res.string.msg_entry_deleted)
    CirclePageMessage.CoverRemoved -> getString(Res.string.msg_cover_removed)
    CirclePageMessage.CoverUnreadable -> getString(Res.string.msg_cover_unreadable)
}

@Composable
fun CirclePageContent(state: CirclePageUiState, actions: CirclePageActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showMessage(message.text(), if (state.undo != null) undo else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndo()
        actions.onMessageShown()
    }
    val page = state.page
    val list = rememberLazyListState()
    val headerGone by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0)) { padding ->
        if (page == null) return@Scaffold
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp),
            ) {
                item(key = "header") { Header(page, state, actions) }
                item(key = "actions") { Actions(page, actions) }
                item(key = "people") { People(page, actions) }
                if (page.upcoming.isNotEmpty()) {
                    item(key = "upcoming-h") { SectionHeader(stringResource(Res.string.page_coming_up)) }
                    items(page.upcoming, key = { it.key }) { UpcomingRow(it, state, actions) }
                }
                item(key = "together-h") { SectionHeader(stringResource(Res.string.page_together)) }
                if (page.timeline.isEmpty()) {
                    item(key = "together-empty") {
                        Text(
                            stringResource(Res.string.page_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
                Timeline.byMonth(page.timeline, state.zone).forEach { month ->
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
                            EntryRow(page, entry, state.zone, last = i == month.entries.lastIndex, actions)
                        }
                    }
                }
            }
            // Once the header scrolls away, a bar keeps the back button, the emoji and the name in view.
            AnimatedVisibility(headerGone, enter = fadeIn(), exit = fadeOut()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                        Text(
                            "${page.group.emoji} ${page.group.name}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        PageMenu(page, actions)
                    }
                }
            }
        }
    }
}

/** The circle's cover (or colour) with its emoji, name and status line; back, cover and ⋮ on top. */
@Composable
private fun Header(page: CirclePage, state: CirclePageUiState, actions: CirclePageActions) {
    val g = page.group
    CircleBackdrop(g, Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                Spacer(Modifier.weight(1f))
                CoverButton(page, actions)
                PageMenu(page, actions)
            }
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.padding(horizontal = 20.dp).size(56.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(g.emoji, style = MaterialTheme.typography.headlineSmall) }
            Spacer(Modifier.height(10.dp))
            Text(
                g.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            Text(
                circleStatusLine(page.status, state.today, state.now, state.zone),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** The camera button: add a cover, or change / remove it. */
@Composable
private fun CoverButton(page: CirclePage, actions: CirclePageActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { if (page.group.cover == null) actions.onPickCover() else menu = true }) {
            Icon(
                Icons.Rounded.PhotoCamera,
                stringResource(if (page.group.cover == null) Res.string.cover_add else Res.string.page_cover),
            )
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.cover_change)) },
                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                onClick = {
                    menu = false
                    actions.onPickCover()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.cover_remove)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = {
                    menu = false
                    actions.onRemoveCover()
                },
            )
        }
    }
}

/** ⋮: Snooze (only with a rhythm) and Delete (not for the built-in Family circle). */
@Composable
private fun PageMenu(page: CirclePage, actions: CirclePageActions) {
    val g = page.group
    val hasRhythm = g.meetEveryDays != null
    if (!hasRhythm && g.isBuiltIn) return
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.group_more)) }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            if (hasRhythm) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.group_snooze_weekend)) },
                    leadingIcon = { Icon(Icons.Rounded.Weekend, null) },
                    onClick = {
                        menu = false
                        actions.onSnooze(SnoozeOption.WEEKEND)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.group_snooze_week)) },
                    leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                    onClick = {
                        menu = false
                        actions.onSnooze(SnoozeOption.ONE_WEEK)
                    },
                )
            }
            if (!g.isBuiltIn) {
                DropdownMenuItem(
                    text = { Text(stringResource(CoreRes.string.delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        menu = false
                        actions.onDelete()
                    },
                )
            }
        }
    }
}

/** We met · Plan · Reminder · Edit (no Edit for the built-in Family circle). */
@Composable
private fun Actions(page: CirclePage, actions: CirclePageActions) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(
            Icons.Rounded.Groups,
            stringResource(Res.string.group_met),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onWeMet()
            },
            modifier = Modifier.weight(1f),
            emphasized = true,
        )
        QuickAction(Icons.Rounded.EventAvailable, stringResource(CoreRes.string.plan_make), actions.onPlan, Modifier.weight(1f))
        QuickAction(Icons.Rounded.AlarmAdd, stringResource(Res.string.page_reminder), actions.onReminder, Modifier.weight(1f))
        if (!page.group.isBuiltIn) {
            QuickAction(Icons.Rounded.Edit, stringResource(CoreRes.string.edit), actions.onEdit, Modifier.weight(1f))
        }
    }
}

/** "People · N": the members' avatars and first names, scrolling sideways; each opens the person's page. */
@Composable
private fun People(page: CirclePage, actions: CirclePageActions) {
    Column {
        SectionHeader(stringResource(Res.string.page_people, page.members.size))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(page.members, key = { it.id.value }) { f ->
                Column(
                    Modifier.widthIn(max = 72.dp).clickable { actions.onOpenFriend(f.id) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FriendAvatar(f, size = 52.dp)
                    Text(f.firstName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val CircleUpcoming.key: String
    get() = when (this) {
        is CircleUpcoming.PlanItem -> "plan-${plan.id}"
        is CircleUpcoming.ReminderItem -> "reminder-${reminder.id}"
    }

@Composable
private fun UpcomingRow(item: CircleUpcoming, state: CirclePageUiState, actions: CirclePageActions) {
    val (icon, title) = when (item) {
        is CircleUpcoming.PlanItem -> (if (item.plan.isRecurring) Icons.Rounded.Repeat else Icons.Rounded.EventAvailable) to item.plan.title
        is CircleUpcoming.ReminderItem -> Icons.Rounded.Alarm to item.reminder.title
    }
    val tint = state.page?.group?.color?.let { circlePalette(it).accent } ?: MaterialTheme.colorScheme.primary
    Surface(
        onClick = {
            when (item) {
                is CircleUpcoming.PlanItem -> actions.onOpenPlan(item.plan)
                is CircleUpcoming.ReminderItem -> actions.onOpenReminder(item.reminder)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(
                item.date?.shortLabel() ?: stringResource(CoreRes.string.reminder_no_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A meetup with who came ("4 of 6", "Everyone"), or a share (the shared timeline row). Long press deletes, with Undo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(page: CirclePage, entry: TimelineEntry, zone: TimeZone, last: Boolean, actions: CirclePageActions) {
    val haptics = LocalHapticFeedback.current
    val modifier = Modifier.fillMaxWidth().combinedClickable(
        onClick = {},
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            actions.onDeleteEntry(entry)
        },
    )
    when (entry) {
        is TimelineEntry.Meetup -> MeetupRow(page, entry, zone, modifier, actions)
        else -> TimelineEntryRow(entry, zone, modifier, last = last)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetupRow(page: CirclePage, entry: TimelineEntry.Meetup, zone: TimeZone, modifier: Modifier, actions: CirclePageActions) {
    val h = entry.hangout
    val place = h.place?.label
    val activity = h.activity?.let { activityLabel(it) }
    val title = when {
        activity != null && place != null -> stringResource(CoreRes.string.tl_activity_at, activity, place)
        activity != null -> activity
        place != null -> place
        else -> stringResource(CoreRes.string.tl_together)
    }
    val who = if (page.everyoneAt(entry)) {
        stringResource(Res.string.page_everyone)
    } else {
        stringResource(Res.string.page_members_at, page.membersAt(entry), page.group.memberIds.size)
    }
    Row(modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Icon(
                h.activity?.let(::activityIcon) ?: Icons.Rounded.Groups,
                null,
                Modifier.padding(9.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(h.at.toLocalDate(zone).shortLabel(), who).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Who came: the members first, then anyone else (as the domain orders them).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.others.forEach { f -> PersonPill(f) { actions.onOpenFriend(f.id) } }
            }
            h.note.trim().takeIf { it.isNotEmpty() }?.let {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            entry.attachment?.let { AttachmentPreview(it) }
        }
    }
}

@Composable
private fun PersonPill(friend: Friend, onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FriendAvatar(friend, size = 22.dp)
        Text(friend.firstName, style = MaterialTheme.typography.labelMedium)
    }
}

private val Friend.firstName: String get() = displayName.substringBefore(' ')
