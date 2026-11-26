package org.goodcompany.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.model.ReminderSnooze
import org.goodcompany.app.core.domain.usecase.SnoozeReminderUseCase
import org.goodcompany.app.core.ui.format.longLabel
import org.goodcompany.app.core.ui.format.relativeDay
import org.goodcompany.app.core.ui.format.relativeDayText
import org.goodcompany.app.core.ui.format.timeLabel
import org.goodcompany.app.core.ui.resources.Res
import org.goodcompany.app.core.ui.resources.cancel
import org.goodcompany.app.core.ui.resources.plan_with_names
import org.goodcompany.app.core.ui.resources.reminder_action_delete
import org.goodcompany.app.core.ui.resources.reminder_action_done
import org.goodcompany.app.core.ui.resources.reminder_action_edit
import org.goodcompany.app.core.ui.resources.reminder_action_not_done
import org.goodcompany.app.core.ui.resources.reminder_action_snooze
import org.goodcompany.app.core.ui.resources.reminder_important_short
import org.goodcompany.app.core.ui.resources.reminder_msg_deleted
import org.goodcompany.app.core.ui.resources.reminder_msg_done
import org.goodcompany.app.core.ui.resources.reminder_msg_next
import org.goodcompany.app.core.ui.resources.reminder_msg_snoozed
import org.goodcompany.app.core.ui.resources.reminder_options
import org.goodcompany.app.core.ui.resources.reminder_slot
import org.goodcompany.app.core.ui.resources.reminder_snooze_hour
import org.goodcompany.app.core.ui.resources.reminder_snooze_next_week
import org.goodcompany.app.core.ui.resources.reminder_snooze_pick
import org.goodcompany.app.core.ui.resources.reminder_snooze_pick_title
import org.goodcompany.app.core.ui.resources.reminder_snooze_tomorrow
import org.goodcompany.app.core.ui.resources.reminder_snooze_tonight
import org.goodcompany.app.core.ui.resources.reminder_snoozed_until
import org.goodcompany.app.core.ui.resources.reminder_when_at
import org.goodcompany.app.core.ui.resources.save
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * What a reminder offers wherever it shows (issue #99): Done (or "Not done" in the Done group), Snooze, Edit and Delete.
 * People and the circle it is about open their pages; nothing here asks "did you meet?".
 */
data class ReminderCallbacks(
    val onDone: (Reminder) -> Unit,
    val onSnooze: (Reminder, ReminderSnooze) -> Unit,
    val onEdit: (Reminder) -> Unit,
    val onDelete: (Reminder) -> Unit,
    val onReopen: (Reminder) -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onOpenCircle: ((String) -> Unit)? = null,
    /**
     * A tap on the reminder itself (issue #135): its circle, its one person or its editor
     * ([org.goodcompany.app.core.domain.usecase.ReminderTarget]). Null where the row isn't tappable.
     */
    val onOpen: ((Reminder) -> Unit)? = null,
)

/** The snackbar line after a reminder action; Done and Delete come with Undo. */
sealed interface ReminderNotice {
    /** Closed; [next] is set when it repeats and rolled on to that date instead. */
    data class Done(val next: Instant?) : ReminderNotice

    data class Snoozed(val until: Instant) : ReminderNotice

    data object Deleted : ReminderNotice

    val offersUndo: Boolean get() = this !is Snoozed
}

/** Localised text for a [ReminderNotice]. */
suspend fun ReminderNotice.text(zone: TimeZone, today: LocalDate): String = when (this) {
    is ReminderNotice.Done -> next?.let { getString(Res.string.reminder_msg_next, whenText(it, zone, today)) }
        ?: getString(Res.string.reminder_msg_done)
    is ReminderNotice.Snoozed -> getString(Res.string.reminder_msg_snoozed, whenText(until, zone, today))
    ReminderNotice.Deleted -> getString(Res.string.reminder_msg_deleted)
}

/** "Tomorrow · 09:00". */
private suspend fun whenText(at: Instant, zone: TimeZone, today: LocalDate): String {
    val local = at.toLocalDateTime(zone)
    return getString(Res.string.reminder_when_at, relativeDayText(local.date, today), local.timeLabel())
}

/**
 * One reminder as a card: title, when (snoozed, repeating, important), who it's about as chips that open them, and
 * the actions. A done one is struck through and offers "Not done" and Delete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderRow(
    reminder: Reminder,
    people: List<Friend>,
    today: LocalDate,
    zone: TimeZone,
    now: Instant,
    callbacks: ReminderCallbacks,
    modifier: Modifier = Modifier,
    circle: FriendGroup? = null,
) {
    Surface(
        onClick = { callbacks.onOpen?.invoke(reminder) },
        enabled = callbacks.onOpen != null,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (reminder.done) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
            Text(
                reminder.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (reminder.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (reminder.done) TextDecoration.LineThrough else null,
            )
            ReminderWhen(reminder, today, zone)
            if (circle != null || people.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    circle?.let { c ->
                        AssistChip(
                            onClick = { callbacks.onOpenCircle?.invoke(c.id) },
                            enabled = callbacks.onOpenCircle != null,
                            label = { Text("${c.emoji} ${c.name}") },
                            leadingIcon = { CircleDot(c.color) },
                        )
                    }
                    people.forEach { f ->
                        AssistChip(
                            onClick = { callbacks.onOpenFriend(f.id) },
                            label = { Text(f.displayName.substringBefore(' ')) },
                            leadingIcon = { FriendAvatar(f, size = 20.dp) },
                        )
                    }
                }
            }
            ReminderActionsRow(reminder, callbacks, today, zone, now)
        }
    }
}

/**
 * A reminder as a timeline line (issue #130), laid out like [PlanRow]: the day where a plan's date goes (a bell when it
 * has none), the title, then the time, repeat, importance and who it's about in one line, and Done · Snooze · Edit ·
 * Delete in a ⋮ menu (the caller offers Undo on Delete).
 */
@Composable
fun ReminderTimelineRow(
    reminder: Reminder,
    people: List<Friend>,
    today: LocalDate,
    zone: TimeZone,
    now: Instant,
    callbacks: ReminderCallbacks,
    modifier: Modifier = Modifier,
    circle: FriendGroup? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var snoozing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val shownAt = reminder.snoozedUntil ?: reminder.dueAt
    val open = callbacks.onOpen
    Row(
        modifier.fillMaxWidth().then(if (open != null) Modifier.clickable { open(reminder) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (shownAt != null) {
            DateBadge(shownAt.toLocalDateTime(zone).date, today)
        } else {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.NotificationsNone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(reminder.title, style = MaterialTheme.typography.titleSmall)
            val details = listOfNotNull(
                reminder.snoozedUntil?.let { stringResource(Res.string.reminder_snoozed_until, it.toLocalDateTime(zone).timeLabel()) }
                    ?: reminder.dueAt?.toLocalDateTime(zone)?.timeLabel(),
                reminder.repeat.takeIf { reminder.isRepeating }?.let { reminderRepeatLabel(it) },
                stringResource(Res.string.reminder_important_short).takeIf { reminder.important },
                circle?.let { "${it.emoji} ${it.name}" },
                people.takeIf { circle == null && it.isNotEmpty() }?.let { fs ->
                    stringResource(Res.string.plan_with_names, fs.joinToString { it.displayName.substringBefore(' ') })
                },
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, stringResource(Res.string.reminder_options), Modifier.size(20.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                fun item(label: StringResource, icon: ImageVector, onClick: () -> Unit): @Composable () -> Unit = {
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { Icon(icon, null) },
                        onClick = {
                            menu = false
                            onClick()
                        },
                    )
                }
                item(Res.string.reminder_action_done, Icons.Rounded.Check) { callbacks.onDone(reminder) }()
                item(Res.string.reminder_action_snooze, Icons.Rounded.Snooze) { snoozing = true }()
                item(Res.string.reminder_action_edit, Icons.Rounded.Edit) { callbacks.onEdit(reminder) }()
                item(Res.string.reminder_action_delete, Icons.Rounded.DeleteOutline) { callbacks.onDelete(reminder) }()
            }
            SnoozeMenu(
                expanded = snoozing,
                tonight = now < today.atTime(LocalTime(SnoozeReminderUseCase.TONIGHT_HOUR, 0)).toInstant(zone),
                onDismiss = { snoozing = false },
                onPick = { snooze ->
                    snoozing = false
                    if (snooze == null) picking = true else callbacks.onSnooze(reminder, snooze)
                },
            )
        }
    }
    if (picking) {
        SnoozeTimeDialog(today, zone, onDismiss = { picking = false }, onPicked = { at ->
            picking = false
            callbacks.onSnooze(reminder, ReminderSnooze.Until(at))
        })
    }
}

/** "Tomorrow · 18:00 · Every week · Important", or "Snoozed until …". Nothing for an undated, plain reminder. */
@Composable
fun ReminderWhen(reminder: Reminder, today: LocalDate, zone: TimeZone, modifier: Modifier = Modifier) {
    val parts = buildList {
        val snooze = reminder.snoozedUntil
        if (snooze != null && !reminder.done) {
            add(Icons.Rounded.Snooze to stringResource(Res.string.reminder_snoozed_until, whenLabel(snooze, zone, today)))
        } else {
            reminder.dueAt?.let { add(null to whenLabel(it, zone, today)) }
        }
        if (reminder.isRepeating) add(Icons.Rounded.EventRepeat to reminderRepeatLabel(reminder.repeat))
        if (reminder.important) add(Icons.Rounded.PriorityHigh to stringResource(Res.string.reminder_important_short))
    }
    if (parts.isEmpty()) return
    Row(modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, (icon, text) ->
            if (i > 0) Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            icon?.let { Icon(it, null, Modifier.size(14.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun whenLabel(at: Instant, zone: TimeZone, today: LocalDate): String {
    val local = at.toLocalDateTime(zone)
    return stringResource(Res.string.reminder_when_at, relativeDay(local.date, today), local.timeLabel())
}

/** Done · Snooze · Edit · Delete (a done reminder: Not done · Delete). */
@Composable
fun ReminderActionsRow(
    reminder: Reminder,
    callbacks: ReminderCallbacks,
    today: LocalDate,
    zone: TimeZone,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (reminder.done) {
            ActionButton(Icons.Rounded.Undo, stringResource(Res.string.reminder_action_not_done)) { callbacks.onReopen(reminder) }
        } else {
            ActionButton(Icons.Rounded.Check, stringResource(Res.string.reminder_action_done)) { callbacks.onDone(reminder) }
            Box {
                ActionButton(Icons.Rounded.Snooze, stringResource(Res.string.reminder_action_snooze)) { menu = true }
                SnoozeMenu(
                    expanded = menu,
                    tonight = now < today.atTime(LocalTime(SnoozeReminderUseCase.TONIGHT_HOUR, 0)).toInstant(zone),
                    onDismiss = { menu = false },
                    onPick = { snooze ->
                        menu = false
                        if (snooze == null) picking = true else callbacks.onSnooze(reminder, snooze)
                    },
                )
            }
            ActionButton(Icons.Rounded.Edit, stringResource(Res.string.reminder_action_edit)) { callbacks.onEdit(reminder) }
        }
        ActionButton(Icons.Rounded.DeleteOutline, stringResource(Res.string.reminder_action_delete)) { callbacks.onDelete(reminder) }
    }
    if (picking) {
        SnoozeTimeDialog(today, zone, onDismiss = { picking = false }, onPicked = { at ->
            picking = false
            callbacks.onSnooze(reminder, ReminderSnooze.Until(at))
        })
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(36.dp)) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** 1 hour · Tonight (before 20:00) · Tomorrow · Next week · Pick a time… ([onPick] with null). */
@Composable
fun SnoozeMenu(expanded: Boolean, tonight: Boolean, onDismiss: () -> Unit, onPick: (ReminderSnooze?) -> Unit) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        listOfNotNull(
            ReminderSnooze.OneHour to Res.string.reminder_snooze_hour,
            (ReminderSnooze.Tonight to Res.string.reminder_snooze_tonight).takeIf { tonight },
            ReminderSnooze.Tomorrow to Res.string.reminder_snooze_tomorrow,
            ReminderSnooze.NextWeek to Res.string.reminder_snooze_next_week,
        ).forEach { (snooze, label) ->
            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { onPick(snooze) })
        }
        DropdownMenuItem(text = { Text(stringResource(Res.string.reminder_snooze_pick)) }, onClick = { onPick(null) })
    }
}

/** "Pick a time…": a day and one of the reminder editor's times. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeTimeDialog(today: LocalDate, zone: TimeZone, onDismiss: () -> Unit, onPicked: (Instant) -> Unit) {
    var date by remember { mutableStateOf(today) }
    var hour by remember { mutableStateOf(SnoozeReminderUseCase.MORNING_HOUR) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Snooze, null) },
        title = { Text(stringResource(Res.string.reminder_snooze_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(date.longLabel()) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REMINDER_HOURS.forEach { (h, label) ->
                        FilterChip(hour == h, { hour = h }, label = { Text(stringResource(Res.string.reminder_slot, stringResource(label), h)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(date.atTime(LocalTime(hour, 0)).toInstant(zone)) }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(date, onDismiss = { pickDate = false }, onPicked = { date = it })
}
