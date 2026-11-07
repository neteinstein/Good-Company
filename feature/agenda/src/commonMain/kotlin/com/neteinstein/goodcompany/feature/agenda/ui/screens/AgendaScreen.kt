package com.neteinstein.goodcompany.feature.agenda.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.feature.agenda.ui.components.AgendaRow
import com.neteinstein.goodcompany.feature.agenda.ui.components.ReminderEditor
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

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
    )
    if (state.showReminderEditor) {
        ReminderEditor(state.friends, state.today, viewModel::onDismissReminderEditor, viewModel::onSaveReminder)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaContent(
    state: AgendaUiState,
    onOpenFriend: (FriendId) -> Unit,
    onNewReminder: () -> Unit,
    onToggleDone: (Reminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
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
        topBar = { TopAppBar(title = { Text("Coming up") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewReminder, icon = { Icon(Icons.Rounded.AlarmAdd, null) }, text = { Text("Reminder") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
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
                    SectionHeader(
                        relativeDay(day, state.today) + if (relativeDay(day, state.today) != day.shortLabel()) " · ${day.shortLabel()}" else "",
                    )
                }
                items(items, key = { it.key }) { AgendaRow(it, onOpenFriend, Modifier.animateItem()) }
            }

            item(key = "reminders-h") { SectionHeader("Your reminders") }
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
                val faded by animateColorAsState(
                    if (r.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val names = state.friends.filter { it.id in r.friendIds }.joinToString { it.name.substringBefore(' ') }
                ListItem(
                    modifier = Modifier.animateItem(),
                    leadingContent = { Checkbox(checked = r.done, onCheckedChange = { onToggleDone(r) }) },
                    headlineContent = {
                        Text(r.title, color = faded, textDecoration = if (r.done) TextDecoration.LineThrough else null)
                    },
                    supportingContent = {
                        val due = r.dueAt?.toLocalDateTime(state.zone)?.let { "${relativeDay(it.date, state.today)} ${it.timeLabel()}" }
                        val text = listOfNotNull(due, names.ifBlank { null }?.let { "with $it" }).joinToString(" · ")
                        if (text.isNotBlank()) Text(text)
                    },
                    trailingContent = { IconButton(onClick = { onDeleteReminder(r.id) }) { Icon(Icons.Rounded.Close, "Delete reminder") } },
                )
            }
            item(key = "bottom") { Spacer(Modifier.height(96.dp)) }
        }
    }
}
