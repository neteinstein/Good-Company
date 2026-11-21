package com.neteinstein.goodcompany.core.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ReminderSnooze
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeReminderUseCase
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativeDayText
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.reminder_action_delete
import com.neteinstein.goodcompany.core.ui.resources.reminder_action_done
import com.neteinstein.goodcompany.core.ui.resources.reminder_action_edit
import com.neteinstein.goodcompany.core.ui.resources.reminder_action_not_done
import com.neteinstein.goodcompany.core.ui.resources.reminder_action_snooze
import com.neteinstein.goodcompany.core.ui.resources.reminder_important_short
import com.neteinstein.goodcompany.core.ui.resources.reminder_msg_deleted
import com.neteinstein.goodcompany.core.ui.resources.reminder_msg_done
import com.neteinstein.goodcompany.core.ui.resources.reminder_msg_next
import com.neteinstein.goodcompany.core.ui.resources.reminder_msg_snoozed
import com.neteinstein.goodcompany.core.ui.resources.reminder_slot
import com.neteinstein.goodcompany.core.ui.resources.reminder_snooze_hour
import com.neteinstein.goodcompany.core.ui.resources.reminder_snooze_next_week
import com.neteinstein.goodcompany.core.ui.resources.reminder_snooze_pick
import com.neteinstein.goodcompany.core.ui.resources.reminder_snooze_pick_title
import com.neteinstein.goodcompany.core.ui.resources.reminder_snooze_tomorrow
import com.neteinstein.goodcompany.core.ui.resources.reminder_snooze_tonight
import com.neteinstein.goodcompany.core.ui.resources.reminder_snoozed_until
import com.neteinstein.goodcompany.core.ui.resources.reminder_when_at
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
        modifier.fillMaxWidth(),
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
