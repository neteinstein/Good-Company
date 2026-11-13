package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.format.monthLong
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.clear
import com.neteinstein.goodcompany.feature.hangouts.resources.delete_memory
import com.neteinstein.goodcompany.feature.hangouts.resources.free_days
import com.neteinstein.goodcompany.feature.hangouts.resources.free_due
import com.neteinstein.goodcompany.feature.hangouts.resources.free_hint
import com.neteinstein.goodcompany.feature.hangouts.resources.free_never
import com.neteinstein.goodcompany.feature.hangouts.resources.free_plan
import com.neteinstein.goodcompany.feature.hangouts.resources.free_title
import com.neteinstein.goodcompany.feature.hangouts.resources.group_delete
import com.neteinstein.goodcompany.feature.hangouts.resources.group_due
import com.neteinstein.goodcompany.feature.hangouts.resources.group_edit
import com.neteinstein.goodcompany.feature.hangouts.resources.group_last_together
import com.neteinstein.goodcompany.feature.hangouts.resources.group_met
import com.neteinstein.goodcompany.feature.hangouts.resources.group_more
import com.neteinstein.goodcompany.feature.hangouts.resources.group_never
import com.neteinstein.goodcompany.feature.hangouts.resources.group_overdue
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snooze_week
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snooze_weekend
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snoozed
import com.neteinstein.goodcompany.feature.hangouts.resources.groups_hint
import com.neteinstein.goodcompany.feature.hangouts.resources.groups_new
import com.neteinstein.goodcompany.feature.hangouts.resources.groups_title
import com.neteinstein.goodcompany.feature.hangouts.resources.last_together
import com.neteinstein.goodcompany.feature.hangouts.resources.last_together_place
import com.neteinstein.goodcompany.feature.hangouts.resources.meet_days_since
import com.neteinstein.goodcompany.feature.hangouts.resources.meet_due
import com.neteinstein.goodcompany.feature.hangouts.resources.meet_met
import com.neteinstein.goodcompany.feature.hangouts.resources.meet_never
import com.neteinstein.goodcompany.feature.hangouts.resources.memories_empty_body
import com.neteinstein.goodcompany.feature.hangouts.resources.memories_empty_title
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_group_deleted
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_group_saved
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_group_snoozed
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_location_denied
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_location_unavailable
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_logged_with
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_memory_deleted
import com.neteinstein.goodcompany.feature.hangouts.resources.never_all
import com.neteinstein.goodcompany.feature.hangouts.resources.review_card_body
import com.neteinstein.goodcompany.feature.hangouts.resources.review_card_cta
import com.neteinstein.goodcompany.feature.hangouts.resources.review_card_title
import com.neteinstein.goodcompany.feature.hangouts.resources.section_catch_up
import com.neteinstein.goodcompany.feature.hangouts.resources.section_coming_due
import com.neteinstein.goodcompany.feature.hangouts.resources.section_last_time_with
import com.neteinstein.goodcompany.feature.hangouts.resources.section_memories
import com.neteinstein.goodcompany.feature.hangouts.resources.section_memories_together
import com.neteinstein.goodcompany.feature.hangouts.resources.someone
import com.neteinstein.goodcompany.feature.hangouts.resources.subtitle_none
import com.neteinstein.goodcompany.feature.hangouts.resources.subtitle_seen
import com.neteinstein.goodcompany.feature.hangouts.resources.times_total
import com.neteinstein.goodcompany.feature.hangouts.resources.title
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInHero
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInSheet
import com.neteinstein.goodcompany.feature.hangouts.ui.components.GroupEditorDialog
import kotlinx.datetime.Month
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

@Composable
fun HangoutsScreen(onOpenFriend: (FriendId) -> Unit, onOpenYearReview: (Int) -> Unit, viewModel: HangoutsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestLocation = rememberPermissionRequester(AppPermission.LOCATION, viewModel::onLocationPermissionResult)
    HangoutsContent(
        state = state,
        actions = HangoutsActions(
            onOpenFriend = onOpenFriend,
            onOpenYearReview = onOpenYearReview,
            onStartCheckIn = viewModel::onStartCheckIn,
            onToggleFilter = viewModel::onToggleFilter,
            onClearFilter = viewModel::onClearFilter,
            onDeleteHangout = viewModel::onDeleteHangout,
            onUndoDelete = viewModel::onUndoDelete,
            onNewGroup = viewModel::onNewGroup,
            onEditGroup = viewModel::onEditGroup,
            onCheckInGroup = viewModel::onCheckInGroup,
            onSnoozeGroup = viewModel::onSnoozeGroup,
            onDeleteGroup = viewModel::onDeleteGroup,
            onMessageShown = viewModel::onMessageShown,
            onSelectOccasion = viewModel::onSelectOccasion,
            onPlanWith = viewModel::onPlanWith,
        ),
    )
    state.planDraft?.let { draft ->
        PlanEditor(
            friends = state.friends,
            today = state.today,
            onDismiss = viewModel::onDismissPlan,
            onSave = viewModel::onSavePlan,
            preselected = setOf(draft.friendId),
            initialTitle = occasionLabel(draft.occasion),
        )
    }
    state.draft?.let { draft ->
        CheckInSheet(
            draft = draft,
            friends = state.friends,
            groups = state.groups.map { it.group },
            onPickGroup = viewModel::onPickDraftGroup,
            onToggleFriend = viewModel::onToggleDraftFriend,
            onQueryChange = viewModel::onDraftQueryChange,
            onPlaceChange = viewModel::onDraftPlaceChange,
            onNoteChange = viewModel::onDraftNoteChange,
            onUseLocation = requestLocation,
            onClearLocation = viewModel::onClearDraftLocation,
            onSave = viewModel::onSaveCheckIn,
            onDismiss = viewModel::onDismissCheckIn,
        )
    }
    state.groupDraft?.let { draft ->
        GroupEditorDialog(
            draft = draft,
            friends = state.friends,
            onChange = viewModel::onGroupDraftChange,
            onSave = viewModel::onSaveGroup,
            onDelete = draft.id?.let { id -> { viewModel.onDeleteGroup(id) } },
            onDismiss = viewModel::onDismissGroup,
        )
    }
}

data class HangoutsActions(
    val onOpenFriend: (FriendId) -> Unit = {},
    val onOpenYearReview: (Int) -> Unit = {},
    val onStartCheckIn: (FriendId?) -> Unit = {},
    val onToggleFilter: (FriendId) -> Unit = {},
    val onClearFilter: () -> Unit = {},
    val onDeleteHangout: (String) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onNewGroup: () -> Unit = {},
    val onEditGroup: (String) -> Unit = {},
    val onCheckInGroup: (String) -> Unit = {},
    val onSnoozeGroup: (String, SnoozeOption) -> Unit = { _, _ -> },
    val onDeleteGroup: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onSelectOccasion: (Occasion) -> Unit = {},
    val onPlanWith: (FriendId, Occasion) -> Unit = { _, _ -> },
)

/** Localised snackbar text for a [HangoutsMessage]. */
suspend fun HangoutsMessage.text(): String = when (this) {
    HangoutsMessage.LocationDenied -> getString(Res.string.msg_location_denied)
    HangoutsMessage.LocationUnavailable -> getString(Res.string.msg_location_unavailable)
    is HangoutsMessage.LoggedWith -> getString(Res.string.msg_logged_with, names.joinToString())
    HangoutsMessage.MemoryDeleted -> getString(Res.string.msg_memory_deleted)
    is HangoutsMessage.GroupSaved -> getString(Res.string.msg_group_saved, name)
    is HangoutsMessage.GroupDeleted -> getString(Res.string.msg_group_deleted, name)
    is HangoutsMessage.GroupSnoozed -> getString(Res.string.msg_group_snoozed, name, until.shortLabelText())
    HangoutsMessage.PlanSaved -> getString(CoreRes.string.plan_saved)
}

@Composable
fun HangoutsContent(state: HangoutsUiState, actions: HangoutsActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message.text(), actionLabel = if (state.recentlyDeleted != null) undo else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete()
        actions.onMessageShown()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = monthLong(state.today.month),
                    title = stringResource(Res.string.title),
                    subtitle = if (state.stats.meetups > 0) {
                        stringResource(Res.string.subtitle_seen, state.stats.friendsSeen)
                    } else {
                        stringResource(Res.string.subtitle_none)
                    },
                )
            }
            item(key = "hero") { CheckInHero(state.stats, state.celebrate, onClick = { actions.onStartCheckIn(null) }) }

            state.reviewYear?.let { year ->
                item(key = "review") { YearReviewCard(year, onClick = { actions.onOpenYearReview(year) }) }
            }

            if (state.overdue.isNotEmpty()) {
                item(key = "overdue-h") { SectionHeader(stringResource(Res.string.section_catch_up)) }
                items(state.overdue, key = { "o-${it.friend.id.value}" }) { s ->
                    MeetupRow(
                        name = s.friend.name,
                        initials = s.friend.initials,
                        progress = 1f,
                        overdue = true,
                        subtitle = s.daysSinceLastSeen?.let { pluralStringResource(Res.plurals.meet_days_since, it, it, everyNDays(s.cadenceDays)) }
                            ?: stringResource(Res.string.meet_never),
                        onClick = { actions.onOpenFriend(s.friend.id) },
                        trailing = {
                            FilledTonalButton(onClick = { actions.onStartCheckIn(s.friend.id) }) { Text(stringResource(Res.string.meet_met)) }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-h") { SectionHeader(stringResource(Res.string.section_coming_due)) }
                items(state.upcoming, key = { "u-${it.friend.id.value}" }) { s ->
                    val goal = s.cadenceDays.coerceAtLeast(1)
                    MeetupRow(
                        name = s.friend.name,
                        initials = s.friend.initials,
                        progress = (s.daysSinceLastSeen ?: 0).toFloat() / goal,
                        overdue = false,
                        subtitle = stringResource(Res.string.meet_due, relativeDayPhrase(s.dueDate, state.today), everyNDays(goal)),
                        onClick = { actions.onOpenFriend(s.friend.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "free-h") { SectionHeader(stringResource(Res.string.free_title)) }
            if (state.occasions.isEmpty()) {
                item(key = "free-hint") {
                    Text(
                        stringResource(Res.string.free_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            } else {
                item(key = "free-chips") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.occasions.keys.forEach { o ->
                            FilterChip(o == state.selectedOccasion, { actions.onSelectOccasion(o) }, label = { Text(occasionLabel(o)) })
                        }
                    }
                }
                val occasion = state.selectedOccasion
                val matches = occasion?.let { state.occasions[it] }.orEmpty().take(FREE_LIMIT)
                items(matches, key = { "free-${occasion?.name}-${it.friend.id.value}" }) { m ->
                    MeetupRow(
                        name = m.friend.name,
                        initials = m.friend.initials,
                        progress = if (m.catchUpDue) 1f else 0f,
                        overdue = false,
                        subtitle = when {
                            m.catchUpDue -> stringResource(Res.string.free_due)
                            m.daysSinceLastSeen != null -> pluralStringResource(Res.plurals.free_days, m.daysSinceLastSeen!!, m.daysSinceLastSeen!!)
                            else -> stringResource(Res.string.free_never)
                        },
                        onClick = { actions.onOpenFriend(m.friend.id) },
                        trailing = {
                            TextButton(onClick = { occasion?.let { actions.onPlanWith(m.friend.id, it) } }) {
                                Text(stringResource(Res.string.free_plan))
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "groups-h") {
                SectionHeader(stringResource(Res.string.groups_title)) {
                    TextButton(onClick = actions.onNewGroup) { Text(stringResource(Res.string.groups_new)) }
                }
            }
            if (state.groups.isEmpty()) {
                item(key = "groups-empty") {
                    Text(
                        stringResource(Res.string.groups_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            items(state.groups, key = { "g-${it.group.id}" }) { g ->
                GroupCard(g, state, actions, Modifier.animateItem())
            }

            item(key = "filter-h") {
                SectionHeader(stringResource(Res.string.section_last_time_with)) {
                    AnimatedVisibility(state.filter.isNotEmpty()) {
                        TextButton(onClick = actions.onClearFilter) { Text(stringResource(Res.string.clear)) }
                    }
                }
                val choices = state.friends.filter { it.isCloseFriend || it.id in state.filter }.ifEmpty { state.friends.take(12) }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(choices, key = { it.id.value }) { f ->
                        FilterChip(
                            selected = f.id in state.filter,
                            onClick = { actions.onToggleFilter(f.id) },
                            label = { Text(f.name.substringBefore(' ')) },
                            leadingIcon = { FriendAvatar(f.name, f.initials, size = 22.dp) },
                        )
                    }
                }
                AnimatedVisibility(state.filter.isNotEmpty()) {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val selected = state.friends.filter { it.id in state.filter }
                            StackedAvatars(selected.map { it.name to it.initials }, size = 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selected.joinToString(" & ") { it.name.substringBefore(' ') }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    state.lastTogether?.let {
                                        val ago = relativePast(it.hangout.at, state.now, state.zone)
                                        val base = it.hangout.place?.label?.let { p -> stringResource(Res.string.last_together_place, ago, p) }
                                            ?: stringResource(Res.string.last_together, ago)
                                        pluralStringResource(Res.plurals.times_total, state.history.size, base, state.history.size)
                                    } ?: stringResource(Res.string.never_all),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "history-h") {
                SectionHeader(stringResource(if (state.filter.isEmpty()) Res.string.section_memories else Res.string.section_memories_together))
            }
            if (!state.isLoading && state.history.isEmpty()) {
                item(key = "history-empty") {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = stringResource(Res.string.memories_empty_title),
                        message = stringResource(Res.string.memories_empty_body),
                    )
                }
            }
            val byMonth = state.history.groupBy { h -> h.hangout.at.toLocalDateTime(state.zone).let { it.year to it.month.ordinal } }
            byMonth.forEach { (ym, list) ->
                item(key = "m-${ym.first}-${ym.second}") {
                    Text(
                        monthLong(Month.entries[ym.second]) + if (ym.first != state.today.year) " ${ym.first}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { "h-${it.hangout.id}" }) { h ->
                    val local = h.hangout.at.toLocalDateTime(state.zone)
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            DateBadge(local.date, state.today)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    h.friends.joinToString { it.name.substringBefore(' ') }.ifEmpty { stringResource(Res.string.someone) },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    h.hangout.place?.label?.let {
                                        Icon(
                                            Icons.Rounded.Place,
                                            null,
                                            Modifier.padding(end = 2.dp).height(14.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Text(" · ", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        local.timeLabel(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (h.hangout.note.isNotBlank()) {
                                    Text(
                                        h.hangout.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            StackedAvatars(h.friends.map { it.name to it.initials }, size = 30.dp, max = 3)
                            IconButton(onClick = { actions.onDeleteHangout(h.hangout.id) }) {
                                Icon(Icons.Rounded.Delete, stringResource(Res.string.delete_memory))
                            }
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun YearReviewCard(year: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
    ) {
        Row(
            Modifier.background(Brush.linearGradient(OnColorGradients.Indigo)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✨", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.review_card_title, year), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(stringResource(Res.string.review_card_body), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(Res.string.review_card_cta), tint = Color.White)
        }
    }
}

@Composable
private fun GroupCard(status: Relationships.GroupStatus, state: HangoutsUiState, actions: HangoutsActions, modifier: Modifier = Modifier) {
    val g = status.group
    val snoozed = g.snoozedUntil?.takeIf { it >= state.today }
    val overdue = status.isOverdue(state.today)
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = { actions.onEditGroup(g.id) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(g.emoji, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        snoozed != null -> stringResource(Res.string.group_snoozed, snoozed.shortLabel())
                        overdue && g.meetEveryDays != null -> stringResource(Res.string.group_overdue, everyNDays(g.meetEveryDays!!))
                        status.dueDate != null && g.meetEveryDays != null ->
                            stringResource(Res.string.group_due, relativeDayPhrase(status.dueDate!!, state.today), everyNDays(g.meetEveryDays!!))
                        status.lastTogether != null -> stringResource(
                            Res.string.group_last_together,
                            relativePast(status.lastTogether!!, state.now, state.zone),
                        )
                        else -> stringResource(Res.string.group_never)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue && snoozed == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StackedAvatars(status.members.map { it.name to it.initials }, size = 24.dp, max = 6)
                    Spacer(Modifier.width(10.dp))
                    val pad = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    val label: @Composable () -> Unit = {
                        Text(stringResource(Res.string.group_met), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    if (overdue && snoozed == null) {
                        Button(onClick = { actions.onCheckInGroup(g.id) }, contentPadding = pad, modifier = Modifier.height(34.dp)) { label() }
                    } else {
                        FilledTonalButton(onClick = {
                            actions.onCheckInGroup(g.id)
                        }, contentPadding = pad, modifier = Modifier.height(34.dp)) { label() }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.group_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.group_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = {
                            menu = false
                            actions.onEditGroup(g.id)
                        },
                    )
                    if (g.meetEveryDays != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.group_snooze_weekend)) },
                            leadingIcon = { Icon(Icons.Rounded.Weekend, null) },
                            onClick = {
                                menu = false
                                actions.onSnoozeGroup(g.id, SnoozeOption.WEEKEND)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.group_snooze_week)) },
                            leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                            onClick = {
                                menu = false
                                actions.onSnoozeGroup(g.id, SnoozeOption.ONE_WEEK)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.group_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = {
                            menu = false
                            actions.onDeleteGroup(g.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetupRow(
    name: String,
    initials: String,
    progress: Float,
    overdue: Boolean,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress,
                size = 52.dp,
                stroke = 4.dp,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ) {
                FriendAvatar(name, initials, size = 40.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

private const val FREE_LIMIT = 5
