package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.reminder_evening
import com.neteinstein.goodcompany.core.ui.resources.reminder_label
import com.neteinstein.goodcompany.core.ui.resources.reminder_lunch
import com.neteinstein.goodcompany.core.ui.resources.reminder_morning
import com.neteinstein.goodcompany.core.ui.resources.reminder_new
import com.neteinstein.goodcompany.core.ui.resources.reminder_night
import com.neteinstein.goodcompany.core.ui.resources.reminder_no_date
import com.neteinstein.goodcompany.core.ui.resources.reminder_placeholder
import com.neteinstein.goodcompany.core.ui.resources.reminder_slot
import com.neteinstein.goodcompany.core.ui.resources.reminder_when
import com.neteinstein.goodcompany.core.ui.resources.reminder_with
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private val HOURS = listOf(
    9 to Res.string.reminder_morning,
    13 to Res.string.reminder_lunch,
    18 to Res.string.reminder_evening,
    21 to Res.string.reminder_night,
)

/** What the reminder editor hands back; [circleId] is the circle it belongs to (issue #88), if any. */
data class ReminderDraft(
    val title: String,
    val friendIds: List<FriendId>,
    val date: LocalDate?,
    val hour: Int,
    val circleId: String? = null,
)

/** "Remind me to book tickets for X to go with Y." */
@Composable
fun ReminderEditor(
    friends: List<Friend>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, friendIds: List<FriendId>, date: LocalDate?, hour: Int) -> Unit,
    preselected: Set<FriendId> = emptySet(),
    initialTitle: String = "",
    circles: List<FriendGroup> = emptyList(),
) = ReminderEditorDialog(
    friends = friends,
    today = today,
    onDismiss = onDismiss,
    onSave = { onSave(it.title, it.friendIds, it.date, it.hour) },
    preselected = preselected,
    initialTitle = initialTitle,
    circles = circles,
)

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
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selected by remember { mutableStateOf(preselected.toList()) }
    var picking by remember { mutableStateOf(false) }
    var withCircle by remember { mutableStateOf(initialCircle) }
    var date by remember { mutableStateOf(initialDate) }
    var hour by remember { mutableStateOf(initialHour) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AlarmAdd, null) },
        title = { Text(stringResource(Res.string.reminder_new)) },
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
                        HOURS.forEach { (h, label) ->
                            FilterChip(hour == h, { hour = h }, label = { Text(stringResource(Res.string.reminder_slot, stringResource(label), h)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(ReminderDraft(title, selected, date, hour, circle?.id?.takeIf { withCircle }))
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

/** The time a new reminder starts at (evening). */
const val DEFAULT_HOUR = 18
