package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.platform.CalendarEntry
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.plan_add_calendar
import com.neteinstein.goodcompany.core.ui.resources.plan_cancel
import com.neteinstein.goodcompany.core.ui.resources.plan_change
import com.neteinstein.goodcompany.core.ui.resources.plan_counts_hint
import com.neteinstein.goodcompany.core.ui.resources.plan_did_you_meet
import com.neteinstein.goodcompany.core.ui.resources.plan_edit
import com.neteinstein.goodcompany.core.ui.resources.plan_new
import com.neteinstein.goodcompany.core.ui.resources.plan_no
import com.neteinstein.goodcompany.core.ui.resources.plan_title_label
import com.neteinstein.goodcompany.core.ui.resources.plan_title_placeholder
import com.neteinstein.goodcompany.core.ui.resources.plan_when
import com.neteinstein.goodcompany.core.ui.resources.plan_where
import com.neteinstein.goodcompany.core.ui.resources.plan_with
import com.neteinstein.goodcompany.core.ui.resources.plan_with_names
import com.neteinstein.goodcompany.core.ui.resources.plan_yes
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

/**
 * Create or change a planned meetup: what, with whom, which day and (optionally) where.
 * A plan doesn't count as "met" until the user says it happened.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanEditor(
    friends: List<Friend>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, friendIds: List<FriendId>, date: LocalDate, place: String?) -> Unit,
    preselected: Set<FriendId> = emptySet(),
    initial: Plan? = null,
    initialTitle: String = "",
) {
    var title by remember { mutableStateOf(initial?.title ?: initialTitle) }
    var selected by remember { mutableStateOf(initial?.friendIds?.toSet() ?: preselected) }
    var date by remember { mutableStateOf(initial?.date ?: today.plus(DEFAULT_DAYS_AHEAD, DateTimeUnit.DAY)) }
    var place by remember { mutableStateOf(initial?.place.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EventAvailable, null) },
        title = { Text(stringResource(if (initial == null) Res.string.plan_new else Res.string.plan_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(stringResource(Res.string.plan_title_label)) },
                    placeholder = { Text(stringResource(Res.string.plan_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.plan_with), style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    friends.sortedWith(compareByDescending<Friend> { it.id in selected }.thenByDescending { it.isCloseFriend })
                        .take(MAX_CHIPS)
                        .forEach { f ->
                            FilterChip(
                                f.id in selected,
                                { selected = if (f.id in selected) selected - f.id else selected + f.id },
                                label = { Text(f.name.substringBefore(' ')) },
                            )
                        }
                }
                Text(stringResource(Res.string.plan_when), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickDate = true }) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date.longLabel())
                }
                OutlinedTextField(
                    place,
                    { place = it },
                    label = { Text(stringResource(Res.string.plan_where)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.plan_counts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, selected.toList(), date, place.ifBlank { null }) },
                enabled = title.isNotBlank() && selected.isNotEmpty(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(date, onDismiss = { pickDate = false }, onPicked = { date = it })
}

/**
 * One plan: date, title, who and where. Past plans ask "Did you meet?"; upcoming ones offer add-to-calendar,
 * change and cancel.
 */
@Composable
fun PlanRow(
    plan: Plan,
    friends: List<Friend>,
    today: LocalDate,
    onConfirm: (happened: Boolean) -> Unit,
    onAddToCalendar: () -> Unit,
    onChange: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    showNames: Boolean = true,
) {
    val isPast = plan.date < today
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(plan.date, today)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleSmall)
                val details = listOfNotNull(
                    friends.takeIf { showNames && it.isNotEmpty() }?.let { fs ->
                        stringResource(Res.string.plan_with_names, fs.joinToString { it.name.substringBefore(' ') })
                    },
                    plan.place,
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isPast) {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.plan_change)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.plan_add_calendar)) },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                        onClick = {
                            menu = false
                            onAddToCalendar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.plan_change)) },
                        leadingIcon = { Icon(Icons.Rounded.EditCalendar, null) },
                        onClick = {
                            menu = false
                            onChange()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.plan_cancel)) },
                        leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        onClick = {
                            menu = false
                            onCancel()
                        },
                    )
                }
            }
        }
        if (isPast) {
            Text(
                stringResource(Res.string.plan_did_you_meet),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onConfirm(true) }) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.plan_yes))
                }
                TextButton(onClick = { onConfirm(false) }) { Text(stringResource(Res.string.plan_no)) }
            }
        }
    }
}

/** What "Add to calendar" hands to the system. */
fun Plan.calendarEntry(friends: List<Friend>): CalendarEntry =
    CalendarEntry(title = title, date = date, location = place, notes = friends.joinToString { it.name }.ifBlank { null })

private const val DEFAULT_DAYS_AHEAD = 3
private const val MAX_CHIPS = 40
