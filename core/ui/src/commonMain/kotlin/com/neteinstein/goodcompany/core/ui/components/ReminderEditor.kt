package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ReminderRepeat
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_never
import com.neteinstein.goodcompany.core.ui.resources.reminder_edit
import com.neteinstein.goodcompany.core.ui.resources.reminder_evening
import com.neteinstein.goodcompany.core.ui.resources.reminder_important
import com.neteinstein.goodcompany.core.ui.resources.reminder_important_hint
import com.neteinstein.goodcompany.core.ui.resources.reminder_label
import com.neteinstein.goodcompany.core.ui.resources.reminder_lunch
import com.neteinstein.goodcompany.core.ui.resources.reminder_morning
import com.neteinstein.goodcompany.core.ui.resources.reminder_new
import com.neteinstein.goodcompany.core.ui.resources.reminder_night
import com.neteinstein.goodcompany.core.ui.resources.reminder_no_date
import com.neteinstein.goodcompany.core.ui.resources.reminder_placeholder
import com.neteinstein.goodcompany.core.ui.resources.reminder_repeat
import com.neteinstein.goodcompany.core.ui.resources.reminder_repeat_daily
import com.neteinstein.goodcompany.core.ui.resources.reminder_repeat_monthly
import com.neteinstein.goodcompany.core.ui.resources.reminder_repeat_weekly
import com.neteinstein.goodcompany.core.ui.resources.reminder_repeat_yearly
import com.neteinstein.goodcompany.core.ui.resources.reminder_slot
import com.neteinstein.goodcompany.core.ui.resources.reminder_when
import com.neteinstein.goodcompany.core.ui.resources.reminder_with
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The times a reminder (or a snooze picked by hand) can be at. */
internal val REMINDER_HOURS = listOf(
    9 to Res.string.reminder_morning,
    13 to Res.string.reminder_lunch,
    18 to Res.string.reminder_evening,
    21 to Res.string.reminder_night,
)

/**
 * What the reminder editor hands back; [circleId] is the circle it belongs to (issue #88), if any. Issue #99 adds how
 * often it comes back ([repeat], only with a date) and [important] ("keep on screen").
 */
data class ReminderDraft(
    val title: String,
    val friendIds: List<FriendId>,
    val date: LocalDate?,
    val hour: Int,
    val circleId: String? = null,
    val repeat: ReminderRepeat = ReminderRepeat.NONE,
    val important: Boolean = false,
) {
    /** The due time in [zone]; null without a date. */
    fun dueAt(zone: TimeZone): Instant? = date?.atTime(LocalTime(hour.coerceIn(0, 23), 0))?.toInstant(zone)
}

/**
 * "Remind me to book tickets for X to go with Y." A new reminder, or [initial] to change one (issue #99: Edit is offered
 * wherever a reminder shows). A reminder that belongs to a circle keeps it, with its chip, when the circle is in [circles].
 */
@Composable
fun ReminderEditor(
    friends: List<Friend>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit,
    preselected: Set<FriendId> = emptySet(),
    initialTitle: String = "",
    circles: List<FriendGroup> = emptyList(),
    initial: Reminder? = null,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val due = initial?.dueAt?.toLocalDateTime(zone)
    val circle = initial?.circleId?.let { id -> circles.firstOrNull { it.id == id } }
    ReminderEditorDialog(
        friends = friends,
        today = today,
        onDismiss = onDismiss,
        // A circle that isn't in the list (deleted, or not loaded) stays on the reminder as it was.
        onSave = { draft -> onSave(if (circle == null && initial?.circleId != null) draft.copy(circleId = initial.circleId) else draft) },
        preselected = initial?.friendIds?.toSet() ?: preselected,
        initialTitle = initial?.title ?: initialTitle,
        circles = circles,
        circle = circle,
        initialCircle = circle != null,
        initialDate = if (initial == null) today else due?.date,
        initialHour = due?.hour ?: DEFAULT_HOUR,
        initial = initial,
    )
}

/**
 * The reminder editor on a circle's page (issue #88): [circle] is picked (its chip can be tapped off), and [initial]
 * fills it in to change an existing reminder ([initialDate] / [initialHour] are its due time, null date = none).
 */
@Composable
fun CircleReminderEditor(
    friends: List<Friend>,
    today: LocalDate,
    circle: FriendGroup,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit,
    initial: Reminder? = null,
    initialDate: LocalDate? = today,
    initialHour: Int = DEFAULT_HOUR,
    circles: List<FriendGroup> = emptyList(),
) = ReminderEditorDialog(
    friends = friends,
    today = today,
    onDismiss = onDismiss,
    onSave = onSave,
    circles = circles,
    preselected = initial?.friendIds?.toSet().orEmpty(),
    initialTitle = initial?.title.orEmpty(),
    circle = circle,
    initialCircle = initial?.let { it.circleId == circle.id } ?: true,
    initialDate = if (initial == null) today else initialDate,
    initialHour = initialHour,
    initial = initial,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditorDialog(
    friends: List<Friend>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit,
    preselected: Set<FriendId> = emptySet(),
    initialTitle: String = "",
    circle: FriendGroup? = null,
    initialCircle: Boolean = circle != null,
    initialDate: LocalDate? = today,
    initialHour: Int = DEFAULT_HOUR,
    circles: List<FriendGroup> = emptyList(),
    initial: Reminder? = null,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selected by remember { mutableStateOf(preselected.toList()) }
    var picking by remember { mutableStateOf(false) }
    var withCircle by remember { mutableStateOf(initialCircle) }
    var date by remember { mutableStateOf(initialDate) }
    var hour by remember { mutableStateOf(initialHour) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: ReminderRepeat.NONE) }
    var important by remember { mutableStateOf(initial?.important ?: false) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AlarmAdd, null) },
        title = { Text(stringResource(if (initial == null) Res.string.reminder_new else Res.string.reminder_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(stringResource(Res.string.reminder_label)) },
                    placeholder = { Text(stringResource(Res.string.reminder_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.reminder_with), style = MaterialTheme.typography.labelLarge)
                if (circle != null) {
                    // A reminder on a circle's page belongs to the circle (issue #88): its members, now and later.
                    FilterChip(
                        withCircle,
                        { withCircle = !withCircle },
                        label = { Text("${circle.emoji} ${circle.name}") },
                        leadingIcon = { CircleDot(circle.color) },
                    )
                }
                // Issue #89: the chosen people with ×, and "+ People" opens the one people picker.
                val byId = friends.associateBy { it.id }
                ChosenPeople(selected.mapNotNull(byId::get), onRemove = { selected = selected - it }, onAdd = { picking = true })
                Text(stringResource(Res.string.reminder_when), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text(date?.longLabel() ?: stringResource(Res.string.reminder_no_date)) }
                    if (date != null) TextButton(onClick = { date = null }) { Text(stringResource(Res.string.reminder_no_date)) }
                }
                if (date != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REMINDER_HOURS.forEach { (h, label) ->
                            FilterChip(hour == h, { hour = h }, label = { Text(stringResource(Res.string.reminder_slot, stringResource(label), h)) })
                        }
                    }
                    // Issue #99: how often it comes back. Done then rolls it to the next date.
                    Text(stringResource(Res.string.reminder_repeat), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderRepeat.entries.forEach { r ->
                            FilterChip(repeat == r, { repeat = r }, label = { Text(reminderRepeatLabel(r)) })
                        }
                    }
                }
                // Issue #99 / #98: kept on screen while it is due (when "Keep important ones on screen" is on).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.reminder_important), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.reminder_important_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(important, { important = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ReminderDraft(
                        title,
                        selected,
                        date,
                        hour,
                        circle?.id?.takeIf { withCircle },
                        repeat = if (date == null) ReminderRepeat.NONE else repeat,
                        important = important,
                    ),
                )
            }, enabled = title.isNotBlank()) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(date ?: today, onDismiss = { pickDate = false }, onPicked = { date = it })
    if (picking) {
        PeoplePickerSheet(friends, circles, chosen = selected, onDismiss = { picking = false }, onDone = {
            selected = it
            picking = false
        })
    }
}

/** "Never", "Every day", "Every week", "Every month", "Every year". */
@Composable
fun reminderRepeatLabel(repeat: ReminderRepeat): String = stringResource(
    when (repeat) {
        ReminderRepeat.NONE -> Res.string.plan_repeat_never
        ReminderRepeat.DAILY -> Res.string.reminder_repeat_daily
        ReminderRepeat.WEEKLY -> Res.string.reminder_repeat_weekly
        ReminderRepeat.MONTHLY -> Res.string.reminder_repeat_monthly
        ReminderRepeat.YEARLY -> Res.string.reminder_repeat_yearly
    },
)

/** The time a new reminder starts at (evening). */
const val DEFAULT_HOUR = 18
