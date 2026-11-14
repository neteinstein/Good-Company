package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.neteinstein.goodcompany.core.ui.resources.log_kind_journal
import com.neteinstein.goodcompany.core.ui.resources.log_kind_message
import com.neteinstein.goodcompany.core.ui.resources.log_kind_met
import com.neteinstein.goodcompany.core.ui.resources.log_kind_video
import com.neteinstein.goodcompany.core.ui.resources.log_less
import com.neteinstein.goodcompany.core.ui.resources.log_more
import com.neteinstein.goodcompany.core.ui.resources.log_note_hint
import com.neteinstein.goodcompany.core.ui.resources.log_people_title
import com.neteinstein.goodcompany.core.ui.resources.log_pick_date
import com.neteinstein.goodcompany.core.ui.resources.log_place_hint
import com.neteinstein.goodcompany.core.ui.resources.log_search
import com.neteinstein.goodcompany.core.ui.resources.log_title
import com.neteinstein.goodcompany.core.ui.resources.log_title_circle
import com.neteinstein.goodcompany.core.ui.resources.log_today
import com.neteinstein.goodcompany.core.ui.resources.log_yesterday
import com.neteinstein.goodcompany.core.ui.resources.ok
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.stringResource
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
    /** Meetups only: where (F11), with "Usually here with". */
    val place: MeetupPlaceState = MeetupPlaceState(),
    val activity: HangoutActivity? = null,
    /** Most used first (F3): the first [ObserveActivitiesUseCase.SHOWN] are on show, the rest behind "More". */
    val activities: List<HangoutActivity> = HangoutActivity.entries,
    val saving: Boolean = false,
    /** Circles to pick from (decision D1). */
    val circles: List<FriendGroup> = emptyList(),
    /** The circle picked: its members are listed to tick who came, nobody ticked to start with (F20). */
    val circle: FriendGroup? = null,
    val circleMembers: List<Friend> = emptyList(),
) {
    /** Journal entries need text; everything else can be saved as it is. */
    val canSave: Boolean get() = people.isNotEmpty() && !saving && (kind != LogKind.JOURNAL || note.isNotBlank())

    /** Someone the "calls and chats count" hint is about: a picked friend whose switch is off. */
    val countsHintFor: Friend? get() = if (kind.interactionType?.isRemote == true) people.firstOrNull { !it.contactCountsAsCatchUp } else null
}

data class LogSheetActions(
    val onKind: (LogKind) -> Unit = {},
    val onPeople: (List<FriendId>) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onPlaceName: (String) -> Unit = {},
    val onUseLocation: () -> Unit = {},
    val onClearLocation: () -> Unit = {},
    /** "Change" on a place that named itself: type another name. */
    val onChangePlace: () -> Unit = {},
    val onActivity: (HangoutActivity?) -> Unit = {},
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
        LogKind.MEETUP -> Res.string.log_kind_met
        LogKind.CALL -> Res.string.log_kind_call
        LogKind.MESSAGE -> Res.string.log_kind_message
        LogKind.EMAIL -> Res.string.log_kind_email
        LogKind.VIDEO_CALL -> Res.string.log_kind_video
        LogKind.JOURNAL -> Res.string.log_kind_journal
    },
)

fun logKindIcon(kind: LogKind) = kind.interactionType?.let(::interactionIcon) ?: Icons.AutoMirrored.Rounded.JournalIcon

/**
 * The single "Log" sheet (issue #21, F2/F3): type chips, people, Today / Yesterday / a picked date, the activity and
 * place for a meetup, and a note. Drawn as a bottom sheet over a scrim that dismisses it, so it can live in a dialog
 * destination on every platform.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogInteractionSheet(state: LogSheetState, actions: LogSheetActions) {
    var pickingPeople by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var showAllActivities by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding()
                // Taps on the sheet itself must not reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                }
                val circle = state.circle
                Text(
                    if (circle != null) {
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
                        state.people.joinToString(", ") { it.name.substringBefore(' ') },
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
                    else -> state.countsHintFor?.let { stringResource(Res.string.log_counts_hint, it.name.substringBefore(' ')) }
                }
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = actions.onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.save))
                }
            }
        }
    }
    if (pickingDate) {
        LocalDatePickerDialog(state.date, onDismiss = { pickingDate = false }, onPicked = actions.onDate, latest = state.today)
    }
    if (pickingPeople) {
        PeoplePicker(state.everyone, state.people.map { it.id }, onDismiss = { pickingPeople = false }, onDone = {
            actions.onPeople(it)
            pickingPeople = false
        })
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
                Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** F2: Today is preselected; Yesterday and "Pick a date" log past meetups. Nothing hidden behind a long-press. */
@Composable
private fun DateChoice(state: LogSheetState, onDate: (LocalDate) -> Unit, onPick: () -> Unit) = LogDateChoice(state.date, state.today, onDate, onPick)

/** Today / Yesterday / a picked date, as on the Log sheet (also used by the share screen). */
@Composable
fun LogDateChoice(date: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit, onPick: () -> Unit) {
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val other = date != today && date != yesterday
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(date == today, onClick = { onDate(today) }, label = { Text(stringResource(Res.string.log_today)) })
        FilterChip(date == yesterday, onClick = { onDate(yesterday) }, label = { Text(stringResource(Res.string.log_yesterday)) })
        FilterChip(
            other,
            onClick = onPick,
            label = { Text(if (other) date.shortLabel() else stringResource(Res.string.log_pick_date)) },
            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
        )
    }
}

/** "+ Add people": tick who it was with (searchable). The people already picked stay ticked. */
@Composable
fun PeoplePicker(everyone: List<Friend>, picked: List<FriendId>, onDismiss: () -> Unit, onDone: (List<FriendId>) -> Unit) {
    var chosen by remember { mutableStateOf(picked) }
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.log_people_title)) },
        text = {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(Res.string.log_search)) },
                )
                val shown = everyone.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id.value }) { f ->
                        val on = f.id in chosen
                        ListItem(
                            modifier = Modifier.clickable { chosen = if (on) chosen - f.id else chosen + f.id },
                            leadingContent = { FriendAvatar(f, size = 36.dp) },
                            headlineContent = { Text(f.name) },
                            trailingContent = { Checkbox(on, onCheckedChange = { chosen = if (on) chosen - f.id else chosen + f.id }) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen) }, enabled = chosen.isNotEmpty()) { Text(stringResource(Res.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
