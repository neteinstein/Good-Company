package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.SoftCard
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInHero
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInSheet
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HangoutsScreen(onOpenFriend: (FriendId) -> Unit, viewModel: HangoutsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestLocation = rememberPermissionRequester(AppPermission.LOCATION, viewModel::onLocationPermissionResult)
    HangoutsContent(
        state = state,
        onOpenFriend = onOpenFriend,
        onStartCheckIn = viewModel::onStartCheckIn,
        onToggleFilter = viewModel::onToggleFilter,
        onClearFilter = viewModel::onClearFilter,
        onDeleteHangout = viewModel::onDeleteHangout,
        onMessageShown = viewModel::onMessageShown,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangoutsContent(
    state: HangoutsUiState,
    onOpenFriend: (FriendId) -> Unit,
    onStartCheckIn: (FriendId?) -> Unit,
    onToggleFilter: (FriendId) -> Unit,
    onClearFilter: () -> Unit,
    onDeleteHangout: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Together") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "hero") { CheckInHero(onClick = { onStartCheckIn(null) }) }

            if (state.overdue.isNotEmpty()) {
                item(key = "overdue-h") { SectionHeader("Time to catch up") }
                items(state.overdue, key = { "o-${it.friend.id.value}" }) { s ->
                    ListItem(
                        modifier = Modifier.animateItem(),
                        leadingContent = { FriendAvatar(s.friend.name, s.friend.initials, size = 40.dp) },
                        headlineContent = { Text(s.friend.name) },
                        supportingContent = {
                            Text(
                                s.daysSinceLastSeen?.let { "$it days since you met · goal every ${s.friend.meetEveryDays} days" }
                                    ?: "No meetup logged yet",
                            )
                        },
                        trailingContent = { TextButton(onClick = { onStartCheckIn(s.friend.id) }) { Text("Met") } },
                    )
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item(key = "upcoming-h") { SectionHeader("Coming due") }
                items(state.upcoming, key = { "u-${it.friend.id.value}" }) { s ->
                    ListItem(
                        modifier = Modifier.animateItem(),
                        leadingContent = { FriendAvatar(s.friend.name, s.friend.initials, size = 40.dp) },
                        headlineContent = { Text(s.friend.name) },
                        supportingContent = { Text("See them by ${relativeDay(s.dueDate, state.today).lowercase()}") },
                    )
                }
            }

            item(key = "filter-h") {
                SectionHeader("Last time with…") {
                    AnimatedVisibility(state.filter.isNotEmpty()) { TextButton(onClick = onClearFilter) { Text("Clear") } }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.friends.filter { it.isCloseFriend || it.id in state.filter }.ifEmpty { state.friends.take(12) }.forEach { f ->
                        FilterChip(f.id in state.filter, { onToggleFilter(f.id) }, label = { Text(f.name.substringBefore(' ')) })
                    }
                }
                AnimatedVisibility(state.filter.isNotEmpty()) {
                    SoftCard {
                        Column(Modifier.padding(16.dp)) {
                            val names = state.friends.filter { it.id in state.filter }.joinToString(" & ") { it.name.substringBefore(' ') }
                            Text(names, style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.lastTogether?.let {
                                    "Last together ${relativePast(it.hangout.at, state.now, state.zone)}" +
                                        (it.hangout.place?.label?.let { p -> " · $p" } ?: "") + " · ${state.history.size} times in total"
                                }
                                    ?: "You haven't logged a meetup with all of them yet.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item(key = "history-h") { SectionHeader("History") }
            if (state.history.isEmpty()) {
                item(key = "history-empty") {
                    EmptyState(
                        icon = Icons.Rounded.History,
                        title = "No memories yet",
                        message = "Tap “I'm with…” when you're with friends. Over time you'll see who you've been missing.",
                    )
                }
            }
            items(state.history, key = { "h-${it.hangout.id}" }) { h ->
                val date = h.hangout.at.toLocalDateTime(state.zone).date
                ListItem(
                    modifier = Modifier.animateItem(),
                    leadingContent = { Icon(if (h.friends.size > 1) Icons.Rounded.Groups else Icons.Rounded.Place, null) },
                    headlineContent = { Text(h.friends.joinToString { it.name.substringBefore(' ') }.ifEmpty { "Someone" }) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                date.shortLabel(),
                                relativePast(h.hangout.at, state.now, state.zone),
                                h.hangout.place?.label,
                                h.hangout.note.ifBlank {
                                    null
                                },
                            )
                                .joinToString(" · "),
                        )
                    },
                    trailingContent = { IconButton(onClick = { onDeleteHangout(h.hangout.id) }) { Icon(Icons.Rounded.Delete, "Delete") } },
                )
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
