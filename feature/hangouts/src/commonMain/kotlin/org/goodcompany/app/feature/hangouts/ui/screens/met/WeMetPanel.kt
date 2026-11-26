package org.goodcompany.app.feature.hangouts.ui.screens.met

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.goodcompany.app.core.domain.model.ActivityChoice
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.PlanOccurrence
import org.goodcompany.app.core.domain.usecase.LogKind
import org.goodcompany.app.core.domain.usecase.LogKindOption
import org.goodcompany.app.core.domain.usecase.ObserveActivitiesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveLogKindsUseCase
import org.goodcompany.app.core.ui.components.DialogSheet
import org.goodcompany.app.core.ui.components.FriendAvatar
import org.goodcompany.app.core.ui.components.LocalDatePickerDialog
import org.goodcompany.app.core.ui.components.LocalTimePickerDialog
import org.goodcompany.app.core.ui.components.LogDateChoice
import org.goodcompany.app.core.ui.components.LogLastTogether
import org.goodcompany.app.core.ui.components.LogOtherLabel
import org.goodcompany.app.core.ui.components.LogSheetState
import org.goodcompany.app.core.ui.components.LogTimeChoice
import org.goodcompany.app.core.ui.components.MeetupPlace
import org.goodcompany.app.core.ui.components.PeoplePickerSheet
import org.goodcompany.app.core.ui.components.SaveBar
import org.goodcompany.app.core.ui.components.activityIcon
import org.goodcompany.app.core.ui.components.activityLabel
import org.goodcompany.app.core.ui.components.logKindIcon
import org.goodcompany.app.core.ui.components.logKindLabel
import org.goodcompany.app.core.ui.components.namesList
import org.goodcompany.app.core.ui.format.shortLabel
import org.goodcompany.app.core.ui.platform.AppPermission
import org.goodcompany.app.core.ui.platform.rememberPermissionRequester
import org.goodcompany.app.core.ui.resources.log_action
import org.goodcompany.app.core.ui.resources.log_add_people
import org.goodcompany.app.core.ui.resources.log_counts_hint
import org.goodcompany.app.core.ui.resources.log_journal_footer
import org.goodcompany.app.core.ui.resources.log_journal_hint
import org.goodcompany.app.core.ui.resources.log_less
import org.goodcompany.app.core.ui.resources.log_note_hint
import org.goodcompany.app.core.ui.resources.log_people_title
import org.goodcompany.app.core.ui.resources.log_place_hint
import org.goodcompany.app.core.ui.resources.log_title_edit
import org.goodcompany.app.core.ui.resources.save
import org.goodcompany.app.feature.hangouts.resources.Res
import org.goodcompany.app.feature.hangouts.resources.met_how
import org.goodcompany.app.feature.hangouts.resources.met_log_people
import org.goodcompany.app.feature.hangouts.resources.met_more_people
import org.goodcompany.app.feature.hangouts.resources.met_note
import org.goodcompany.app.feature.hangouts.resources.met_save_on
import org.goodcompany.app.feature.hangouts.resources.met_see_all
import org.goodcompany.app.feature.hangouts.resources.met_title
import org.goodcompany.app.feature.hangouts.resources.met_title_circle
import org.goodcompany.app.feature.hangouts.resources.met_what
import org.goodcompany.app.feature.hangouts.resources.met_when
import org.goodcompany.app.feature.hangouts.resources.met_where_note
import org.goodcompany.app.feature.hangouts.resources.met_who
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.goodcompany.app.core.ui.resources.Res as CoreRes

data class WeMetActions(
    val onKind: (LogKindOption) -> Unit = {},
    val onLabel: (String) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onTime: (LocalTime?) -> Unit = {},
    val onActivity: (ActivityChoice?) -> Unit = {},
    val onTogglePerson: (FriendId) -> Unit = {},
    val onPeople: (List<FriendId>) -> Unit = {},
    /** "Where · Note" (issue #151). */
    val onOpenDetails: () -> Unit = {},
    val onPlaceName: (String) -> Unit = {},
    val onUseLocation: () -> Unit = {},
    val onClearLocation: () -> Unit = {},
    val onChangePlace: () -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** The Log popup as a screen: shown by the `WeMetRoute` dialog destination. [onClose] runs after a save too. */
@Composable
fun WeMetPanelScreen(
    friendIds: List<FriendId>,
    circleId: String?,
    /** Answering a plan's "Did you meet?" (`<planId>@<date>`, see [PlanOccurrence]). */
    plan: String?,
    onClose: () -> Unit,
    /** A kind, a day and an activity to open on, or a timeline entry to edit (issue #151: the full Log popup's). */
    prefill: LogPrefill = LogPrefill(),
    viewModel: WeMetViewModel = koinViewModel(key = "met-${friendIds.joinToString { it.value }}-$circleId-$plan-$prefill") {
        parametersOf(friendIds, circleId, plan?.let(PlanOccurrence::decode), prefill)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    // Ask for location first (issue #114): without the permission the provider just returns nothing.
    val requestLocation = rememberPermissionRequester(AppPermission.LOCATION, viewModel::onLocationPermissionResult)
    WeMetPanel(
        state,
        WeMetActions(
            onKind = viewModel::onKind,
            onLabel = viewModel::onLabel,
            onDate = viewModel::onDate,
            onTime = viewModel::onTime,
            onActivity = viewModel::onActivity,
            onTogglePerson = viewModel::onTogglePerson,
            onPeople = viewModel::onPeople,
            onOpenDetails = viewModel::onOpenDetails,
            onPlaceName = viewModel::onPlaceName,
            onUseLocation = requestLocation,
            onClearLocation = viewModel::onClearLocation,
            onChangePlace = viewModel::onChangePlace,
            onNote = viewModel::onNote,
            onSave = viewModel::onSave,
            onDismiss = onClose,
        ),
    )
}

/**
 * Issues #47, #101 and #151 (F2, F3): Who, "How?", When and What, each with its small title, then a collapsed
 * "Where · Note" row, and Save within reach, so "Caught up" → Save logs today in two taps. "How?" and What show the
 * four most used and "See all". A call, a chat or a game hides What and the place; "Other" asks what it was; a journal
 * entry opens the note. No hidden gestures. Drawn with [DialogSheet]: a scrim, drag down to close.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeMetPanel(state: WeMetUiState, actions: WeMetActions) {
    val sheet = state.sheet
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    // Issue #116: opened with nobody (Today's Log button, a shortcut, the daily reminder): the people picker comes first.
    var pickingPeople by rememberSaveable { mutableStateOf(state.startsWithNobody) }
    var showAllKinds by rememberSaveable { mutableStateOf(false) }
    var showAllActivities by rememberSaveable { mutableStateOf(false) }
    DialogSheet(
        onDismiss = actions.onDismiss,
        bottomBar = {
            SaveBar(actions.onSave, enabled = sheet.canSave, label = saveLabel(sheet), color = MaterialTheme.colorScheme.surfaceContainerLow)
        },
    ) {
        val circle = sheet.circle
        Text(
            when {
                sheet.editing -> stringResource(CoreRes.string.log_title_edit)
                circle != null -> stringResource(Res.string.met_title_circle, "${circle.emoji} ${circle.name}".trim())
                sheet.people.isEmpty() -> stringResource(CoreRes.string.log_action)
                else -> stringResource(Res.string.met_title, namesList(sheet.people))
            },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Section(stringResource(Res.string.met_who)) { People(sheet, actions.onTogglePerson, onAdd = { pickingPeople = true }) }
        Section(stringResource(Res.string.met_how)) {
            HowRow(state.kinds, state.selected, showAllKinds, onToggleAll = { showAllKinds = !showAllKinds }, onKind = actions.onKind)
        }
        // Issue #152: one of your own How options is already named; only the built-in "Other" asks what it was.
        if (sheet.kind == LogKind.OTHER && state.selected.name == null) LogOtherLabel(sheet.label, actions.onLabel)
        Section(stringResource(Res.string.met_when)) {
            LogDateChoice(sheet.date, sheet.today, actions.onDate, onPick = { pickingDate = true })
            LogTimeChoice(sheet.time, sheet.date, sheet.now, sheet.zone, actions.onTime, onPick = { pickingTime = true })
        }
        if (sheet.kind == LogKind.MEETUP) {
            LogLastTogether(sheet.lastTogether, sheet.now, sheet.zone)
            Section(stringResource(Res.string.met_what)) {
                Activities(sheet, showAllActivities, onToggleMore = { showAllActivities = !showAllActivities }, onActivity = actions.onActivity)
            }
        }
        if (state.detailsOpen) {
            if (sheet.kind == LogKind.MEETUP) {
                // The place field (F11): a named place recognises itself and suggests who's usually there.
                MeetupPlace(
                    sheet.place,
                    freeTextLabel = stringResource(CoreRes.string.log_place_hint),
                    onName = actions.onPlaceName,
                    onUseLocation = actions.onUseLocation,
                    onClearLocation = actions.onClearLocation,
                    onChange = actions.onChangePlace,
                    onAddPerson = actions.onTogglePerson,
                )
            }
            OutlinedTextField(
                sheet.note,
                actions.onNote,
                Modifier.fillMaxWidth(),
                label = {
                    Text(stringResource(if (sheet.kind == LogKind.JOURNAL) CoreRes.string.log_journal_hint else CoreRes.string.log_note_hint))
                },
            )
        } else {
            DetailsRow(
                stringResource(if (sheet.kind == LogKind.MEETUP) Res.string.met_where_note else Res.string.met_note),
                actions.onOpenDetails,
            )
        }
        val hint = if (sheet.kind == LogKind.JOURNAL) {
            stringResource(CoreRes.string.log_journal_footer)
        } else {
            sheet.countsHintFor?.let { stringResource(CoreRes.string.log_counts_hint, it.displayName.substringBefore(' ')) }
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (pickingDate) {
        LocalDatePickerDialog(sheet.date, onDismiss = { pickingDate = false }, onPicked = actions.onDate, latest = sheet.today)
    }
    if (pickingTime) {
        LocalTimePickerDialog(sheet.time ?: sheet.nowTime, onDismiss = { pickingTime = false }, onPicked = actions.onTime)
    }
    if (pickingPeople) {
        PeoplePickerSheet(
            sheet.recentFirst,
            sheet.circles,
            chosen = sheet.people.map { it.id },
            onDismiss = { pickingPeople = false },
            onDone = {
                actions.onPeople(it)
                pickingPeople = false
            },
            title = stringResource(CoreRes.string.log_people_title),
            requireSomeone = true,
        )
    }
}

/** A small title over one part of the popup (issue #151: Who, How?, When, What). */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * "How?" (issues #101, #151): one chip per way of keeping in touch, most used first; the first
 * [ObserveLogKindsUseCase.SHOWN] (and the chosen one) on show, the rest behind "See all".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HowRow(
    kinds: List<LogKindOption>,
    selected: LogKindOption,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    onKind: (LogKindOption) -> Unit,
) {
    val shown = if (showAll) kinds else kinds.take(ObserveLogKindsUseCase.SHOWN)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The chosen one always shows, even when it is turned off in Settings (issue #152: e.g. editing an old entry).
        (shown + listOf(selected).filter { it !in shown }).forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onKind(option) },
                label = { Text(option.name ?: logKindLabel(option.kind)) },
                leadingIcon = { Icon(logKindIcon(option.kind), null, Modifier.size(18.dp)) },
            )
        }
        if (kinds.size > ObserveLogKindsUseCase.SHOWN) SeeAllChip(showAll, onToggleAll)
    }
}

/** "See all" (issue #151), or "Less" once everything shows. */
@Composable
private fun SeeAllChip(showingAll: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(if (showingAll) CoreRes.string.log_less else Res.string.met_see_all)) },
        leadingIcon = if (showingAll) null else ({ Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(18.dp)) }),
    )
}

/** The collapsed "Where · Note" row (issue #151, owner decision 2026-11-21): a tap opens the place and the note. */
@Composable
private fun DetailsRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

/** "Save", "Save · Fri, Nov 6" for another day, or "Log 2 people" for a circle. */
@Composable
private fun saveLabel(sheet: LogSheetState): String = when {
    sheet.circle != null && sheet.people.isNotEmpty() -> pluralStringResource(Res.plurals.met_log_people, sheet.people.size, sheet.people.size)
    sheet.date != sheet.today -> stringResource(Res.string.met_save_on, sheet.date.shortLabel())
    else -> stringResource(CoreRes.string.save)
}

/**
 * Who: the person (or people) the button belongs to; a circle's members as chips with nobody ticked (F20), the first
 * [CIRCLE_SHOWN] on show. "+ Add people" for anyone else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun People(sheet: LogSheetState, onToggle: (FriendId) -> Unit, onAdd: () -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val ticked = sheet.people.map { it.id }.toSet()
    val memberIds = sheet.circleMembers.map { it.id }.toSet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sheet.circle != null) {
            val members = sheet.circleMembers
            val shown = if (showAll) members else members.take(CIRCLE_SHOWN)
            shown.forEach { f -> PersonChip(f, selected = f.id in ticked, onClick = { onToggle(f.id) }) }
            if (shown.size < members.size) {
                AssistChip(
                    onClick = { showAll = true },
                    label = { Text(stringResource(Res.string.met_more_people, members.size - shown.size)) },
                )
            }
        }
        // Anyone ticked who isn't in the circle (or everyone, without a circle): tap to untick.
        sheet.people.filter { it.id !in memberIds }.forEach { f -> PersonChip(f, selected = true, onClick = { onToggle(f.id) }) }
        AssistChip(
            onClick = onAdd,
            label = { Text(stringResource(CoreRes.string.log_add_people)) },
            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun PersonChip(friend: Friend, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(friend.displayName.substringBefore(' ')) },
        leadingIcon = { FriendAvatar(friend, size = 24.dp) },
    )
}

/**
 * F3 and issue #151: the [ObserveActivitiesUseCase.SHOWN] most used activities as tiles, then "See all". Tapping the
 * chosen one again clears it.
 */
@Composable
private fun Activities(sheet: LogSheetState, showAll: Boolean, onToggleMore: () -> Unit, onActivity: (ActivityChoice?) -> Unit) {
    val shown = if (showAll) sheet.activities else sheet.activities.take(ObserveActivitiesUseCase.SHOWN)
    val tiles = shown + listOfNotNull(sheet.activity?.takeIf { it !in shown })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    Tile(
                        label = activityLabel(a),
                        icon = { Icon(activityIcon(a), null) },
                        selected = sheet.activity == a,
                        onClick = { onActivity(a.takeIf { sheet.activity != a }) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (sheet.activities.size > ObserveActivitiesUseCase.SHOWN) SeeAllChip(showAll, onToggleMore)
    }
}

@Composable
private fun Tile(label: String, icon: @Composable () -> Unit, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            this.selected = selected
        },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val TILES_PER_ROW = 4
private const val CIRCLE_SHOWN = 8
