package com.neteinstein.goodcompany.feature.agenda.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.ReminderEditor
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.feature.agenda.ui.components.AgendaRow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val SHORT_DAYS = listOf("M", "T", "W", "T", "F", "S", "S")
private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

@Composable
fun AgendaScreen(onOpenFriend: (FriendId) -> Unit, viewModel: AgendaViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AgendaContent(
        state = state,
        onOpenFriend = onOpenFriend,
        onNewReminder = viewModel::onNewReminder,
        onToggleDone = viewModel::onToggleDone,
        onDeleteReminder = viewModel::onDeleteReminder,
        onMessageShown = viewModel::onMessageShown,
        onUndoDelete = viewModel::onUndoDelete,
    )
    if (state.showReminderEditor) {
        ReminderEditor(state.friends, state.today, viewModel::onDismissReminderEditor, viewModel::onSaveReminder)
    }
}

@Composable
fun AgendaContent(
    state: AgendaUiState,
    onOpenFriend: (FriendId) -> Unit,
    onNewReminder: () -> Unit,
    onToggleDone: (Reminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onMessageShown: () -> Unit,
    onUndoDelete: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = if (state.recentlyDeleted != null) "Undo" else null)
        if (result == SnackbarResult.ActionPerformed) onUndoDelete()
        onMessageShown()
    }
    // Index of each day's header in the lazy list, so tapping the week strip can scroll to it.
    val dayIndex = remember(state.agendaByDay) {
        var i = 2 // header + week strip
        state.agendaByDay.associate { (day, items) -> (day to i).also { i += 1 + items.size } }
    }
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewReminder, icon = { Icon(Icons.Rounded.AlarmAdd, null) }, text = { Text("Reminder") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = "${WEEKDAYS[state.today.dayOfWeek.ordinal]}, ${MONTHS[state.today.month.ordinal]} ${state.today.day}",
                    title = "Coming up",
                    subtitle = when (val n = state.agenda.size) {
                        0 -> "Nothing on the horizon"
                        1 -> "1 thing in the next two months"
                        else -> "$n things in the next two months"
                    },
                )
            }
            item(key = "week") {
                WeekStrip(state.week, state.today) { day -> dayIndex[day]?.let { scope.launch { listState.animateScrollToItem(it) } } }
            }
            if (!state.isLoading && state.agenda.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.EventAvailable,
                        title = "All quiet",
                        message = "Mark real friends and add birthdays, kids and important moments — they'll show up here.",
                    )
                }
            }
            state.agendaByDay.forEach { (day, items) ->
                item(key = "day-$day") {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateBadge(day, state.today)
                        Spacer(Modifier.width(12.dp))
                        Text(relativeDay(day, state.today), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(items, key = { it.key }) { AgendaRow(it, onOpenFriend, Modifier.padding(start = 84.dp, end = 16.dp).animateItem()) }
            }

            item(key = "reminders-h") { SectionHeader("Your reminders · ${state.openReminders} open") }
            if (state.reminders.isEmpty()) {
                item(key = "reminders-empty") {
                    Text(
                        "“Book tickets for the concert with Rui” — add one with the button below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(state.reminders, key = { "r-${it.id}" }) { r ->
                val faded by animateColorAsState(if (r.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                val people = state.friends.filter { it.id in r.friendIds }
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).animateItem(),
                    shape = MaterialTheme.shapes.large,
                    color = if (r.done) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = r.done, onCheckedChange = { onToggleDone(r) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = faded,
                                textDecoration = if (r.done) TextDecoration.LineThrough else null,
                            )
                            val due = r.dueAt?.toLocalDateTime(state.zone)?.let { "${relativeDay(it.date, state.today)} · ${it.timeLabel()}" }
                            val text = listOfNotNull(
                                due,
                                people.joinToString {
                                    it.name.substringBefore(' ')
                                }.ifBlank { null }?.let { "with $it" },
                            ).joinToString(" · ")
                            if (text.isNotBlank()) {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (people.isNotEmpty()) StackedAvatars(people.map { it.name to it.initials }, size = 28.dp, max = 3)
                        IconButton(onClick = { onDeleteReminder(r.id) }) { Icon(Icons.Rounded.Close, "Delete reminder") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(days: List<Pair<LocalDate, Int>>, today: LocalDate, onDay: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { (day, count) ->
            val selected = day == today
            Column(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(enabled = count > 0) { onDay(day) }
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background.copy(alpha = 0f))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(SHORT_DAYS[day.dayOfWeek.ordinal], style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                Text(day.day.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(minOf(count, 3)) {
                        Box(
                            Modifier.size(
                                5.dp,
                            ).background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                    if (count == 0) Box(Modifier.size(5.dp))
                }
            }
        }
    }
}
