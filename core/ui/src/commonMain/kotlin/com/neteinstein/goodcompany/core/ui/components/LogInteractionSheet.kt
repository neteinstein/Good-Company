package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.circle_tick_who_came
import com.neteinstein.goodcompany.core.ui.resources.log_activity
import com.neteinstein.goodcompany.core.ui.resources.log_add_people
import com.neteinstein.goodcompany.core.ui.resources.log_counts_hint
import com.neteinstein.goodcompany.core.ui.resources.log_journal_footer
import com.neteinstein.goodcompany.core.ui.resources.log_journal_hint
import com.neteinstein.goodcompany.core.ui.resources.log_kind_call
import com.neteinstein.goodcompany.core.ui.resources.log_kind_email
import com.neteinstein.goodcompany.core.ui.resources.log_kind_game
import com.neteinstein.goodcompany.core.ui.resources.log_kind_in_person
import com.neteinstein.goodcompany.core.ui.resources.log_kind_journal
import com.neteinstein.goodcompany.core.ui.resources.log_kind_long_chat
import com.neteinstein.goodcompany.core.ui.resources.log_kind_other
import com.neteinstein.goodcompany.core.ui.resources.log_kind_said_hi
import com.neteinstein.goodcompany.core.ui.resources.log_kind_video
import com.neteinstein.goodcompany.core.ui.resources.log_less
import com.neteinstein.goodcompany.core.ui.resources.log_more
import com.neteinstein.goodcompany.core.ui.resources.log_note_hint
import com.neteinstein.goodcompany.core.ui.resources.log_other_label
import com.neteinstein.goodcompany.core.ui.resources.log_people_title
import com.neteinstein.goodcompany.core.ui.resources.log_pick_date
import com.neteinstein.goodcompany.core.ui.resources.log_place_hint
import com.neteinstein.goodcompany.core.ui.resources.log_search
import com.neteinstein.goodcompany.core.ui.resources.log_title
import com.neteinstein.goodcompany.core.ui.resources.log_title_circle
import com.neteinstein.goodcompany.core.ui.resources.log_title_edit
import com.neteinstein.goodcompany.core.ui.resources.log_today
import com.neteinstein.goodcompany.core.ui.resources.log_yesterday
import com.neteinstein.goodcompany.core.ui.resources.ok
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import androidx.compose.material.icons.automirrored.rounded.MenuBook as JournalIcon

/** What the "Log" sheet shows. Built by its ViewModel; the sheet itself is stateless apart from open dialogs. */
data class LogSheetState(
    val kind: LogKind = LogKind.MEETUP,
    /** Everyone the log is for, in the order picked. */
    val people: List<Friend> = emptyList(),
    /** Everyone who can be added, A–Z. */
    val everyone: List<Friend> = emptyList(),
    val date: LocalDate,
    val today: LocalDate,
    val note: String = "",
    /** [LogKind.OTHER] only: what it was, in the user's words. */
    val label: String = "",
    /** Meetups only: where (F11), with "Usually here with". */
    val place: MeetupPlaceState = MeetupPlaceState(),
    val activity: ActivityChoice? = null,
    /** Most used first (F3): the first [ObserveActivitiesUseCase.SHOWN] are on show, the rest behind "More". */
    val activities: List<ActivityChoice> = HangoutActivity.entries.map { ActivityChoice.BuiltIn(it) },
    val saving: Boolean = false,
    /** Circles to pick from (decision D1). */
    val circles: List<FriendGroup> = emptyList(),
    /** The circle picked: its members are listed to tick who came, nobody ticked to start with (F20). */
    val circle: FriendGroup? = null,
    val circleMembers: List<Friend> = emptyList(),
    /** Editing a timeline entry (issue #102): the title says so. */
    val editing: Boolean = false,
    /** Issue #116: everyone, whoever you were last in touch with first (the people picker's order). */
    val recentFirst: List<Friend> = everyone,
    /** Issue #116: the time of day chosen (null = now today, noon on an earlier day). */
    val time: LocalTime? = null,
    val now: Instant = Instant.DISTANT_PAST,
    val zone: TimeZone = TimeZone.UTC,
    /** In person only: the last meetup with everyone chosen ("Last time together: …"). */
    val lastTogether: Instant? = null,
) {
    /** Journal entries need text; everything else can be saved as it is. */
    val canSave: Boolean get() = people.isNotEmpty() && !saving && (kind != LogKind.JOURNAL || note.isNotBlank())

    /** The current time of day, to the minute (where "Pick a time" starts). */
    val nowTime: LocalTime get() = now.toLocalDateTime(zone).let { LocalTime(it.hour, it.minute) }

    /** Someone the "calls and chats count" hint is about: a picked friend whose switch is off. */
    val countsHintFor: Friend? get() = if (kind.interactionType?.isRemote == true) people.firstOrNull { !it.contactCountsAsCatchUp } else null
}

data class LogSheetActions(
    val onKind: (LogKind) -> Unit = {},
    val onPeople: (List<FriendId>) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    /** Issue #116: Now (null), a quick time of day or a picked time. */
    val onTime: (LocalTime?) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onLabel: (String) -> Unit = {},
    val onPlaceName: (String) -> Unit = {},
    val onUseLocation: () -> Unit = {},
    val onClearLocation: () -> Unit = {},
    /** "Change" on a place that named itself: type another name. */
    val onChangePlace: () -> Unit = {},
    val onActivity: (ActivityChoice?) -> Unit = {},
    /** Picks a circle (null or the same one again = none). */
    val onCircle: (String?) -> Unit = {},
    /** Ticks or unticks someone (a circle member who came). */
    val onTogglePerson: (FriendId) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

@Composable
fun logKindLabel(kind: LogKind): String = stringResource(
    when (kind) {
        LogKind.MEETUP -> Res.string.log_kind_in_person
        LogKind.CALL -> Res.string.log_kind_call
        LogKind.MESSAGE -> Res.string.log_kind_said_hi
        LogKind.EMAIL -> Res.string.log_kind_email
        LogKind.VIDEO_CALL -> Res.string.log_kind_video
        LogKind.LONG_CHAT -> Res.string.log_kind_long_chat
        LogKind.GAME -> Res.string.log_kind_game
        LogKind.OTHER -> Res.string.log_kind_other
        LogKind.JOURNAL -> Res.string.log_kind_journal
    },
)

fun logKindIcon(kind: LogKind) = kind.interactionType?.let(::interactionIcon) ?: Icons.AutoMirrored.Rounded.JournalIcon

/**
 * The single "Log" sheet (issue #21, F2/F3): type chips, people, Today / Yesterday / a picked date, the activity and
 * place for a meetup, and a note. Drawn with [DialogSheet] (a scrim, drag down to close), so it can live in a dialog
 * destination on every platform.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogInteractionSheet(state: LogSheetState, actions: LogSheetActions) {
    var pickingPeople by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var showAllActivities by rememberSaveable { mutableStateOf(false) }
    DialogSheet(onDismiss = actions.onDismiss) {
        val circle = state.circle
        Text(
            if (state.editing) {
                stringResource(Res.string.log_title_edit)
            } else if (circle != null) {
                stringResource(Res.string.log_title_circle, circle.name)
            } else {
                stringResource(Res.string.log_title, namesList(state.people))
            },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LogKind.entries.forEach { kind ->
                FilterChip(
                    selected = state.kind == kind,
                    onClick = { actions.onKind(kind) },
                    label = { Text(logKindLabel(kind)) },
                    leadingIcon = { Icon(logKindIcon(kind), null, Modifier.size(18.dp)) },
                )
            }
        }
        if (state.circles.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.circles.forEach { g ->
                    CircleChip(g, selected = circle?.id == g.id, onClick = { actions.onCircle(g.id.takeUnless { circle?.id == it }) })
                }
            }
        }
        if (circle != null) {
            CircleMembers(circle, state.circleMembers, state.people.map { it.id }.toSet(), actions.onTogglePerson)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StackedAvatars(state.people, size = 32.dp, max = 4)
            Spacer(Modifier.width(10.dp))
            Text(
                state.people.joinToString(", ") { it.displayName.substringBefore(' ') },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            TextButton(onClick = { pickingPeople = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.log_add_people))
            }
        }
        DateChoice(state, onDate = actions.onDate, onPick = { pickingDate = true })
        LogTimeChoice(state.time, state.date, state.now, state.zone, actions.onTime, onPick = { pickingTime = true })
        if (state.kind == LogKind.MEETUP) LogLastTogether(state.lastTogether, state.now, state.zone)
        if (state.kind == LogKind.MEETUP) {
            Text(stringResource(Res.string.log_activity), style = MaterialTheme.typography.labelLarge)
            val shown = if (showAllActivities) state.activities else state.activities.take(ObserveActivitiesUseCase.SHOWN)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (shown + listOfNotNull(state.activity?.takeIf { it !in shown })).forEach { a ->
                    FilterChip(
                        selected = state.activity == a,
                        onClick = { actions.onActivity(a.takeIf { state.activity != a }) },
                        label = { Text(activityLabel(a)) },
                        leadingIcon = { Icon(activityIcon(a), null, Modifier.size(18.dp)) },
                    )
                }
                if (state.activities.size > ObserveActivitiesUseCase.SHOWN) {
                    TextButton(onClick = { showAllActivities = !showAllActivities }) {
                        Text(stringResource(if (showAllActivities) Res.string.log_less else Res.string.log_more))
                    }
                }
            }
            MeetupPlace(
                state.place,
                freeTextLabel = stringResource(Res.string.log_place_hint),
                onName = actions.onPlaceName,
                onUseLocation = actions.onUseLocation,
                onClearLocation = actions.onClearLocation,
                onChange = actions.onChangePlace,
                onAddPerson = actions.onTogglePerson,
            )
        }
        if (state.kind == LogKind.OTHER) LogOtherLabel(state.label, actions.onLabel)
        OutlinedTextField(
            state.note,
            actions.onNote,
            Modifier.fillMaxWidth().heightIn(min = 96.dp),
            label = {
                Text(stringResource(if (state.kind == LogKind.JOURNAL) Res.string.log_journal_hint else Res.string.log_note_hint))
            },
        )
        val hint = when {
            state.kind == LogKind.JOURNAL -> stringResource(Res.string.log_journal_footer)
            else -> state.countsHintFor?.let { stringResource(Res.string.log_counts_hint, it.displayName.substringBefore(' ')) }
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Button(onClick = actions.onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.save))
        }
    }
    if (pickingDate) {
        LocalDatePickerDialog(state.date, onDismiss = { pickingDate = false }, onPicked = actions.onDate, latest = state.today)
    }
    if (pickingTime) {
        LocalTimePickerDialog(state.time ?: state.nowTime, onDismiss = { pickingTime = false }, onPicked = actions.onTime)
    }
    if (pickingPeople) {
        PeoplePickerSheet(
            state.recentFirst,
            state.circles,
            chosen = state.people.map { it.id },
            onDismiss = { pickingPeople = false },
            onDone = {
                actions.onPeople(it)
                pickingPeople = false
            },
            title = stringResource(Res.string.log_people_title),
            requireSomeone = true,
        )
    }
}

/** A circle's members with a tick each (F20: nobody is ticked to start with; tick who came). */
@Composable
private fun CircleMembers(circle: FriendGroup, members: List<Friend>, ticked: Set<FriendId>, onToggle: (FriendId) -> Unit) {
    Column {
        Text(
            stringResource(Res.string.circle_tick_who_came, circle.name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        members.forEach { f ->
            val on = f.id in ticked
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(f.id) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(on, onCheckedChange = { onToggle(f.id) })
                FriendAvatar(f, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(f.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** F2: Today is preselected; Yesterday and "Pick a date" log past meetups. Nothing hidden behind a long-press. */
@Composable
private fun DateChoice(state: LogSheetState, onDate: (LocalDate) -> Unit, onPick: () -> Unit) = LogDateChoice(state.date, state.today, onDate, onPick)

/**
 * Today / Yesterday / a picked date, as on the Log sheet and the "Caught up" panel (also used by the share screen).
 * Issue #114: one line and one height in every language; the third chip says "Other day" and, if even that doesn't
 * fit, shows only its calendar icon (its label stays as the content description).
 */
@Composable
fun LogDateChoice(date: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit, onPick: () -> Unit) {
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val other = date != today && date != yesterday
    val chip = Modifier.height(FilterChipDefaults.Height)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(date == today, onClick = { onDate(today) }, label = { OneLine(stringResource(Res.string.log_today)) }, modifier = chip)
        FilterChip(date == yesterday, onClick = { onDate(yesterday) }, label = { OneLine(stringResource(Res.string.log_yesterday)) }, modifier = chip)
        val pickLabel = stringResource(Res.string.log_pick_date)
        var iconOnly by remember { mutableStateOf(false) }
        FilterChip(
            other,
            onClick = onPick,
            label = {
                when {
                    other -> OneLine(date.shortLabel())
                    !iconOnly -> OneLine(pickLabel, onOverflow = { iconOnly = true })
                }
            },
            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
            modifier = chip.semantics { contentDescription = pickLabel },
        )
    }
}

/** A chip label that never wraps; [onOverflow] runs when it doesn't fit. */
@Composable
private fun OneLine(text: String, onOverflow: () -> Unit = {}) {
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow) onOverflow() },
    )
}

/** "What was it?" for [LogKind.OTHER] (issue #101): a short free label, shown on the timeline instead of "Other". */
@Composable
fun LogOtherLabel(label: String, onLabel: (String) -> Unit) {
    OutlinedTextField(
        label,
        onLabel,
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.log_other_label)) },
        singleLine = true,
    )
}
