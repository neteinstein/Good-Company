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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.platform.CalendarEntry
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.every_n_days
import com.neteinstein.goodcompany.core.ui.resources.plan_activity
import com.neteinstein.goodcompany.core.ui.resources.plan_add_calendar
import com.neteinstein.goodcompany.core.ui.resources.plan_cancel
import com.neteinstein.goodcompany.core.ui.resources.plan_change
import com.neteinstein.goodcompany.core.ui.resources.plan_counts_hint
import com.neteinstein.goodcompany.core.ui.resources.plan_did_you_meet
import com.neteinstein.goodcompany.core.ui.resources.plan_edit
import com.neteinstein.goodcompany.core.ui.resources.plan_every_n_months
import com.neteinstein.goodcompany.core.ui.resources.plan_every_n_weeks
import com.neteinstein.goodcompany.core.ui.resources.plan_new
import com.neteinstein.goodcompany.core.ui.resources.plan_no
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_add_end
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_custom
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_every_label
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_forever
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_never
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_remove_end
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_unit_days
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_unit_months
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_unit_weeks
import com.neteinstein.goodcompany.core.ui.resources.plan_repeat_until
import com.neteinstein.goodcompany.core.ui.resources.plan_skip_one
import com.neteinstein.goodcompany.core.ui.resources.plan_title_label
import com.neteinstein.goodcompany.core.ui.resources.plan_title_placeholder
import com.neteinstein.goodcompany.core.ui.resources.plan_when
import com.neteinstein.goodcompany.core.ui.resources.plan_where
import com.neteinstein.goodcompany.core.ui.resources.plan_with
import com.neteinstein.goodcompany.core.ui.resources.plan_with_circle
import com.neteinstein.goodcompany.core.ui.resources.plan_with_names
import com.neteinstein.goodcompany.core.ui.resources.plan_with_people
import com.neteinstein.goodcompany.core.ui.resources.plan_yes
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Create or change a planned meetup: what (title and activity), with whom (a circle, people or both), which day,
 * whether it repeats and (optionally) where. A plan doesn't count as "met" until the user says it happened.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanEditor(
    friends: List<Friend>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (PlanInput) -> Unit,
    preselected: Set<FriendId> = emptySet(),
    initial: Plan? = null,
    initialTitle: String = "",
    circles: List<FriendGroup> = emptyList(),
    preselectedCircle: String? = null,
    activities: List<HangoutActivity> = HangoutActivity.entries,
) {
    var title by remember { mutableStateOf(initial?.title ?: initialTitle) }
    var selected by remember { mutableStateOf(initial?.friendIds?.toSet() ?: preselected) }
    var circleId by remember { mutableStateOf(initial?.circleId ?: preselectedCircle) }
    var withCircle by remember { mutableStateOf(circleId != null) }
    var activity by remember { mutableStateOf(initial?.activity) }
    var date by remember { mutableStateOf(initial?.date ?: today.plus(DEFAULT_DAYS_AHEAD, DateTimeUnit.DAY)) }
    var place by remember { mutableStateOf(initial?.place.orEmpty()) }
    var repeat by remember { mutableStateOf(initial?.repeat?.let { RepeatDraft(it.every, it.unit, it.until) }) }
    var pickDate by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }
    val circle = circles.firstOrNull { it.id == circleId }.takeIf { withCircle }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.EventAvailable, null) },
        title = { Text(stringResource(if (initial == null) Res.string.plan_new else Res.string.plan_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.plan_with), style = MaterialTheme.typography.labelLarge)
                if (circles.isNotEmpty()) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(withCircle, { withCircle = true }, SegmentedButtonDefaults.itemShape(0, 2)) {
                            Text(stringResource(Res.string.plan_with_circle))
                        }
                        SegmentedButton(!withCircle, { withCircle = false }, SegmentedButtonDefaults.itemShape(1, 2)) {
                            Text(stringResource(Res.string.plan_with_people))
                        }
                    }
                }
                if (withCircle && circles.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        circles.forEach { c ->
                            CircleChip(
                                c,
                                selected = c.id == circleId,
                                onClick = { circleId = c.id.takeIf { it != circleId } },
                                trailing = c.memberIds.size.toString(),
                            )
                        }
                    }
                } else {
                    FlowRow(
                        Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        friends.sortedWith(compareByDescending<Friend> { it.id in selected }.thenByDescending { it.isCloseFriend })
                            .take(MAX_CHIPS)
                            .forEach { f ->
                                FilterChip(
                                    f.id in selected,
                                    { selected = if (f.id in selected) selected - f.id else selected + f.id },
                                    label = { Text(f.displayName.substringBefore(' ')) },
                                )
                            }
                    }
                }
                Text(stringResource(Res.string.plan_activity), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shown = activities.take(SHOWN_ACTIVITIES)
                    (shown + listOfNotNull(activity?.takeIf { it !in shown })).forEach { a ->
                        FilterChip(
                            selected = activity == a,
                            onClick = { activity = a.takeIf { activity != a } },
                            label = { Text(activityLabel(a)) },
                            leadingIcon = { Icon(activityIcon(a), null, Modifier.size(18.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(stringResource(Res.string.plan_title_label)) },
                    placeholder = { Text(stringResource(Res.string.plan_title_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.plan_when), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { pickDate = true }) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date.longLabel())
                }
                RepeatChoice(repeat, onChange = { repeat = it }, onPickUntil = { pickUntil = true })
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
            val people = if (circle != null) emptyList() else selected.toList()
            TextButton(
                onClick = {
                    onSave(
                        PlanInput(
                            title = title,
                            friendIds = people,
                            date = date,
                            place = place.ifBlank { null },
                            circleId = circle?.id,
                            activity = activity,
                            repeat = repeat?.let { Repeat(it.every, it.unit, date, it.until) },
                        ),
                    )
                },
                enabled = title.isNotBlank() && (circle != null || people.isNotEmpty()),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(date, onDismiss = { pickDate = false }, onPicked = { date = it })
    if (pickUntil) {
        LocalDatePickerDialog(repeat?.until ?: date, onDismiss = { pickUntil = false }, onPicked = { d ->
            repeat = repeat?.copy(until = d.takeIf { it >= date })
        })
    }
}

/** The repeat being edited: every [every] [unit], until [until] (null = until you stop it). */
private data class RepeatDraft(val every: Int, val unit: RepeatUnit, val until: LocalDate? = null)

/** Never / Every 15 days (the common one-tap choice) / Custom (a number and days, weeks or months), plus an end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatChoice(repeat: RepeatDraft?, onChange: (RepeatDraft?) -> Unit, onPickUntil: () -> Unit) {
    Text(stringResource(Res.string.plan_repeat), style = MaterialTheme.typography.labelLarge)
    val quick = RepeatDraft(QUICK_EVERY_DAYS, RepeatUnit.DAY)
    val isQuick = repeat != null && repeat.every == quick.every && repeat.unit == quick.unit
    var custom by remember { mutableStateOf(repeat != null && !isQuick) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(repeat == null, {
            custom = false
            onChange(null)
        }, label = { Text(stringResource(Res.string.plan_repeat_never)) })
        FilterChip(isQuick && !custom, {
            custom = false
            onChange(quick.copy(until = repeat?.until))
        }, label = { Text(repeatLabel(quick.every, quick.unit)) }, leadingIcon = { Icon(Icons.Rounded.Repeat, null, Modifier.size(18.dp)) })
        FilterChip(custom, {
            custom = true
            if (repeat == null) onChange(RepeatDraft(1, RepeatUnit.WEEK))
        }, label = { Text(stringResource(Res.string.plan_repeat_custom)) })
    }
    if (repeat == null) return
    if (custom) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var text by remember(repeat.every) { mutableStateOf(repeat.every.toString()) }
            OutlinedTextField(
                text,
                { v ->
                    text = v.filter(Char::isDigit).take(3)
                    text.toIntOrNull()?.takeIf { it >= 1 }?.let { onChange(repeat.copy(every = it)) }
                },
                label = { Text(stringResource(Res.string.plan_repeat_every_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RepeatUnit.entries.forEach { u ->
                    FilterChip(repeat.unit == u, { onChange(repeat.copy(unit = u)) }, label = { Text(stringResource(unitLabel(u))) })
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            repeat.until?.let { stringResource(Res.string.plan_repeat_until, it.longLabel()) }
                ?: stringResource(Res.string.plan_repeat_forever),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (repeat.until == null) {
            TextButton(onClick = onPickUntil) { Text(stringResource(Res.string.plan_repeat_add_end)) }
        } else {
            TextButton(onClick = { onChange(repeat.copy(until = null)) }) { Text(stringResource(Res.string.plan_repeat_remove_end)) }
        }
    }
}

private fun unitLabel(unit: RepeatUnit) = when (unit) {
    RepeatUnit.DAY -> Res.string.plan_repeat_unit_days
    RepeatUnit.WEEK -> Res.string.plan_repeat_unit_weeks
    RepeatUnit.MONTH -> Res.string.plan_repeat_unit_months
}

/** "Every 15 days", "Every week", "Every 2 months" (sentence case). */
@Composable
fun repeatLabel(every: Int, unit: RepeatUnit): String = when (unit) {
    RepeatUnit.DAY -> pluralStringResource(Res.plurals.every_n_days, every, every)
    RepeatUnit.WEEK -> pluralStringResource(Res.plurals.plan_every_n_weeks, every, every)
    RepeatUnit.MONTH -> pluralStringResource(Res.plurals.plan_every_n_months, every, every)
}.replaceFirstChar { it.uppercaseChar() }

@Composable
fun repeatLabel(repeat: Repeat): String = repeatLabel(repeat.every, repeat.unit)

/**
 * One plan (one occurrence of a recurring plan): date, title, who, how often and where. Past ones ask "Did you meet?";
 * upcoming ones offer add-to-calendar, change, "Skip this one" (a series) and cancel.
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
    occurrence: LocalDate = plan.date,
    circle: FriendGroup? = null,
    onSkip: () -> Unit = {},
) {
    val isPast = occurrence < today
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(occurrence, today)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleSmall)
                val details = listOfNotNull(
                    circle?.let { "${it.emoji} ${it.name}" },
                    friends.takeIf { showNames && circle == null && it.isNotEmpty() }?.let { fs ->
                        stringResource(Res.string.plan_with_names, fs.joinToString { it.displayName.substringBefore(' ') })
                    },
                    plan.activity?.let { activityLabel(it) },
                    plan.repeat?.let { repeatLabel(it) },
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
                    if (plan.isRecurring) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plan_skip_one)) },
                            leadingIcon = { Icon(Icons.Rounded.EventBusy, null) },
                            onClick = {
                                menu = false
                                onSkip()
                            },
                        )
                    }
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

/** What "Add to calendar" hands to the system: one occurrence ([on], the open one by default). */
fun Plan.calendarEntry(friends: List<Friend>, on: LocalDate = date, circle: FriendGroup? = null): CalendarEntry = CalendarEntry(
    title = title,
    date = on,
    location = place,
    notes = (listOfNotNull(circle?.name) + friends.map { it.displayName }).joinToString().ifBlank { null },
)

private const val DEFAULT_DAYS_AHEAD = 3
private const val MAX_CHIPS = 40
private const val SHOWN_ACTIVITIES = 6
private const val QUICK_EVERY_DAYS = 15
