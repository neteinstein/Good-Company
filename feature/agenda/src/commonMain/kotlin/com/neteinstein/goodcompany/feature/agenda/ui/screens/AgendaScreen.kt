package com.neteinstein.goodcompany.feature.agenda.ui.screens

import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_logged
import com.neteinstein.goodcompany.core.ui.platform.rememberCalendarAdder
import com.neteinstein.goodcompany.core.ui.components.calendarEntry
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
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
import androidx.compose.material.icons.rounded.CardGiftcard
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
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.ReminderEditor
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.SendMessageSheet
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.core.ui.format.weekdayInitial
import com.neteinstein.goodcompany.core.ui.format.weekdayLong
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.core.ui.resources.wish_default
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.empty_body
import com.neteinstein.goodcompany.feature.agenda.resources.empty_title
import com.neteinstein.goodcompany.feature.agenda.resources.eyebrow
import com.neteinstein.goodcompany.feature.agenda.resources.fab_reminder
import com.neteinstein.goodcompany.feature.agenda.resources.msg_group_met
import com.neteinstein.goodcompany.feature.agenda.resources.msg_reminder_deleted
import com.neteinstein.goodcompany.feature.agenda.resources.msg_reminder_saved
import com.neteinstein.goodcompany.feature.agenda.resources.nothing_ahead
import com.neteinstein.goodcompany.feature.agenda.resources.reminder_delete
import com.neteinstein.goodcompany.feature.agenda.resources.reminder_with
import com.neteinstein.goodcompany.feature.agenda.resources.reminders_empty
import com.neteinstein.goodcompany.feature.agenda.resources.reminders_header
import com.neteinstein.goodcompany.feature.agenda.resources.things_ahead
import com.neteinstein.goodcompany.feature.agenda.resources.title
import com.neteinstein.goodcompany.feature.agenda.resources.wish_gift_hint
import com.neteinstein.goodcompany.feature.agenda.resources.wish_title
import com.neteinstein.goodcompany.feature.agenda.ui.components.AgendaRow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** Localised snackbar text for an [AgendaMessage]. */
suspend fun AgendaMessage.text(): String = when (this) {
    AgendaMessage.ReminderSaved -> getString(Res.string.msg_reminder_saved)
    AgendaMessage.ReminderDeleted -> getString(Res.string.msg_reminder_deleted)
    is AgendaMessage.GroupMet -> getString(Res.string.msg_group_met, name)
    is AgendaMessage.AiError -> aiErrorText(error)
    AgendaMessage.PlanLogged -> getString(CoreRes.string.plan_logged)
    AgendaMessage.PlanClosed -> getString(CoreRes.string.plan_closed)
}

@Composable
fun AgendaScreen(onOpenFriend: (FriendId) -> Unit, viewModel: AgendaViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addToCalendar = rememberCalendarAdder()
    AgendaContent(
        state = state,
        onOpenFriend = onOpenFriend,
        onNewReminder = viewModel::onNewReminder,
        onToggleDone = viewModel::onToggleDone,
        onDeleteReminder = viewModel::onDeleteReminder,
        onMessageShown = viewModel::onMessageShown,
        onUndoDelete = viewModel::onUndoDelete,
        onOpenWish = viewModel::onOpenWish,
        onGroupMet = viewModel::onGroupMet,
        onConfirmPlan = viewModel::onConfirmPlan,
        onAddPlanToCalendar = { addToCalendar(it.plan.calendarEntry(it.friends)) },
    )
    if (state.showReminderEditor) {
        ReminderEditor(state.friends, state.today, viewModel::onDismissReminderEditor, viewModel::onSaveReminder)
    }
    state.wishFor?.let { friend ->
        val firstName = friend.name.substringBefore(' ')
        val ideas = friend.gifts.filter { it.isIdea && it.forKidId == null }
        SendMessageSheet(
            title = stringResource(Res.string.wish_title, firstName),
            suggestion = state.wishDraft ?: stringResource(CoreRes.string.wish_default, firstName),
            phone = friend.phoneNumbers.firstOrNull(),
            onDismiss = viewModel::onDismissWish,
            isGenerating = state.isDraftingWish,
            onPersonalise = if (state.hasAiKey) viewModel::onPersonaliseWish else null,
            header = if (ideas.isEmpty()) {
                null
            } else {
                { InfoPill(stringResource(Res.string.wish_gift_hint, ideas.joinToString(limit = 3) { it.title }), icon = Icons.Rounded.CardGiftcard) }
            },
        )
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
    onOpenWish: (FriendId) -> Unit = {},
    onGroupMet: (String) -> Unit = {},
    onConfirmPlan: (String, Boolean) -> Unit = { _, _ -> },
    onAddPlanToCalendar: (AgendaItem.PlanUpcoming) -> Unit = {},
) {
    val undo = stringResource(CoreRes.string.undo)
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message.text(), actionLabel = if (state.recentlyDeleted != null) undo else null)
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
            ExtendedFloatingActionButton(onClick = onNewReminder, icon = {
                Icon(Icons.Rounded.AlarmAdd, null)
            }, text = { Text(stringResource(Res.string.fab_reminder)) })
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
                    eyebrow = stringResource(Res.string.eyebrow, weekdayLong(state.today.dayOfWeek), state.today.shortLabel()),
                    title = stringResource(Res.string.title),
                    subtitle = when (val n = state.agenda.size) {
                        0 -> stringResource(Res.string.nothing_ahead)
                        else -> pluralStringResource(Res.plurals.things_ahead, n, n)
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
                        title = stringResource(Res.string.empty_title),
                        message = stringResource(Res.string.empty_body),
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
                items(items, key = { it.key }) {
                    AgendaRow(
                        it,
                        state.today,
                        onOpenFriend,
                        onOpenWish = onOpenWish,
                        onGroupMet = onGroupMet,
                        onConfirmPlan = onConfirmPlan,
                        onAddPlanToCalendar = onAddPlanToCalendar,
                        modifier = Modifier.padding(start = 84.dp, end = 16.dp).animateItem(),
                    )
                }
            }

            item(key = "reminders-h") { SectionHeader(stringResource(Res.string.reminders_header, state.openReminders)) }
            if (state.reminders.isEmpty()) {
                item(key = "reminders-empty") {
                    Text(
                        stringResource(Res.string.reminders_empty),
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
                                }.ifBlank { null }?.let { stringResource(Res.string.reminder_with, it) },
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
                        IconButton(onClick = { onDeleteReminder(r.id) }) { Icon(Icons.Rounded.Close, stringResource(Res.string.reminder_delete)) }
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
                Text(weekdayInitial(day.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
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
