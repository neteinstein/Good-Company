package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInHero
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInSheet
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

@Composable
fun HangoutsScreen(onOpenFriend: (FriendId) -> Unit, viewModel: HangoutsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestLocation = rememberPermissionRequester(AppPermission.LOCATION, viewModel::onLocationPermissionResult)
    HangoutsContent(
        state = state,
        actions = HangoutsActions(
            onOpenFriend = onOpenFriend,
            onStartCheckIn = viewModel::onStartCheckIn,
            onToggleFilter = viewModel::onToggleFilter,
            onClearFilter = viewModel::onClearFilter,
            onDeleteHangout = viewModel::onDeleteHangout,
            onUndoDelete = viewModel::onUndoDelete,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
    state.draft?.let { draft ->
        CheckInSheet(
            draft = draft,
            friends = state.friends,
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
}

data class HangoutsActions(
    val onOpenFriend: (FriendId) -> Unit = {},
    val onStartCheckIn: (FriendId?) -> Unit = {},
    val onToggleFilter: (FriendId) -> Unit = {},
    val onClearFilter: () -> Unit = {},
    val onDeleteHangout: (String) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun HangoutsContent(state: HangoutsUiState, actions: HangoutsActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = if (state.recentlyDeleted != null) "Undo" else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete()
        actions.onMessageShown()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom =
                padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = MONTHS[state.today.month.ordinal],
                    title = "Together",
                    subtitle = if (state.stats.meetups >
                        0
                    ) {
                        "You've seen ${state.stats.friendsSeen} friends this month 💛"
                    } else {
                        "Make some memories this month"
                    },
                )
            }
            item(key = "hero") { CheckInHero(state.stats, state.celebrate, onClick = { actions.onStartCheckIn(null) }) }

            if (state.overdue.isNotEmpty()) {
                item(key = "overdue-h") { SectionHeader("Time to catch up") }
                items(state.overdue, key = { "o-${it.friend.id.value}" }) { s ->
                    MeetupRow(
                        name = s.friend.name,
                        initials = s.friend.initials,
                        progress = 1f,
                        overdue = true,
                        subtitle =
                        s.daysSinceLastSeen?.let { "$it days since you met · goal every ${s.friend.meetEveryDays} days" }
                            ?: "No meetup logged yet",
                        onClick = { actions.onOpenFriend(s.friend.id) },
                        trailing = { FilledTonalButton(onClick = { actions.onStartCheckIn(s.friend.id) }) { Text("Met") } },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-h") { SectionHeader("Coming due") }
                items(state.upcoming, key = { "u-${it.friend.id.value}" }) { s ->
                    val goal = s.friend.meetEveryDays ?: 1
                    MeetupRow(
                        name = s.friend.name,
                        initials = s.friend.initials,
                        progress = (s.daysSinceLastSeen ?: 0).toFloat() / goal,
                        overdue = false,
                        subtitle = "Due ${relativeDayPhrase(s.dueDate, state.today)} · every $goal days",
                        onClick = { actions.onOpenFriend(s.friend.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "filter-h") {
                SectionHeader("Last time with…") {
                    AnimatedVisibility(state.filter.isNotEmpty()) { TextButton(onClick = actions.onClearFilter) { Text("Clear") } }
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
                                        "Last together ${relativePast(it.hangout.at, state.now, state.zone)}" +
                                            (it.hangout.place?.label?.let { p -> " · $p" } ?: "") + " · ${state.history.size}× in total"
                                    } ?: "You haven't logged a meetup with all of them yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "history-h") { SectionHeader(if (state.filter.isEmpty()) "Memories" else "Memories together") }
            if (state.history.isEmpty()) {
                item(key = "history-empty") {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = "No memories yet",
                        message = "Tap “I'm with…” when you're with friends. Over time you'll see who you've been missing.",
                    )
                }
            }
            val byMonth = state.history.groupBy { h -> h.hangout.at.toLocalDateTime(state.zone).let { it.year to it.month.ordinal } }
            byMonth.forEach { (ym, list) ->
                item(key = "m-${ym.first}-${ym.second}") {
                    Text(
                        MONTHS[ym.second] + if (ym.first != state.today.year) " ${ym.first}" else "",
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
                                    h.friends.joinToString { it.name.substringBefore(' ') }.ifEmpty { "Someone" },
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
                            IconButton(onClick = { actions.onDeleteHangout(h.hangout.id) }) { Icon(Icons.Rounded.Delete, "Delete memory") }
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
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
