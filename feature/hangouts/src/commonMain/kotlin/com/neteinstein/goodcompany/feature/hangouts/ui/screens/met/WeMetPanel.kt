package com.neteinstein.goodcompany.feature.hangouts.ui.screens.met

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.LogDateChoice
import com.neteinstein.goodcompany.core.ui.components.LogSheetState
import com.neteinstein.goodcompany.core.ui.components.MeetupPlace
import com.neteinstein.goodcompany.core.ui.components.PeoplePickerSheet
import com.neteinstein.goodcompany.core.ui.components.activityIcon
import com.neteinstein.goodcompany.core.ui.components.activityLabel
import com.neteinstein.goodcompany.core.ui.components.namesList
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.resources.log_add_people
import com.neteinstein.goodcompany.core.ui.resources.log_less
import com.neteinstein.goodcompany.core.ui.resources.log_more
import com.neteinstein.goodcompany.core.ui.resources.log_note_hint
import com.neteinstein.goodcompany.core.ui.resources.log_people_title
import com.neteinstein.goodcompany.core.ui.resources.log_place_hint
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.met_add_note
import com.neteinstein.goodcompany.feature.hangouts.resources.met_add_place
import com.neteinstein.goodcompany.feature.hangouts.resources.met_log_people
import com.neteinstein.goodcompany.feature.hangouts.resources.met_more_details
import com.neteinstein.goodcompany.feature.hangouts.resources.met_more_people
import com.neteinstein.goodcompany.feature.hangouts.resources.met_save_on
import com.neteinstein.goodcompany.feature.hangouts.resources.met_title
import com.neteinstein.goodcompany.feature.hangouts.resources.met_title_circle
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

data class WeMetActions(
    val onDate: (LocalDate) -> Unit = {},
    val onActivity: (HangoutActivity?) -> Unit = {},
    val onTogglePerson: (FriendId) -> Unit = {},
    val onPeople: (List<FriendId>) -> Unit = {},
    val onOpenNote: () -> Unit = {},
    val onOpenPlace: () -> Unit = {},
    val onPlaceName: (String) -> Unit = {},
    val onUseLocation: () -> Unit = {},
    val onClearLocation: () -> Unit = {},
    val onChangePlace: () -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onMoreDetails: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * The small "We met" panel as a screen: shown by the `WeMetRoute` dialog destination. [onClose] runs after a save too;
 * [onMoreDetails] swaps it for the full Log sheet with what was chosen so far.
 */
@Composable
fun WeMetPanelScreen(
    friendIds: List<FriendId>,
    circleId: String?,
    /** Answering a plan's "Did you meet?" (`<planId>@<date>`, see [PlanOccurrence]). */
    plan: String?,
    onClose: () -> Unit,
    onMoreDetails: (WeMetDetails) -> Unit,
    viewModel: WeMetViewModel = koinViewModel(key = "met-${friendIds.joinToString { it.value }}-$circleId-$plan") {
        parametersOf(friendIds, circleId, plan?.let(PlanOccurrence::decode))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    WeMetPanel(
        state,
        WeMetActions(
            onDate = viewModel::onDate,
            onActivity = viewModel::onActivity,
            onTogglePerson = viewModel::onTogglePerson,
            onPeople = viewModel::onPeople,
            onOpenNote = viewModel::onOpenNote,
            onOpenPlace = viewModel::onOpenPlace,
            onPlaceName = viewModel::onPlaceName,
            onUseLocation = viewModel::onUseLocation,
            onClearLocation = viewModel::onClearLocation,
            onChangePlace = viewModel::onChangePlace,
            onNote = viewModel::onNote,
            onSave = viewModel::onSave,
            onMoreDetails = { onMoreDetails(viewModel.details()) },
            onDismiss = onClose,
        ),
    )
}

/**
 * Issue #47 (F2, F3): who, Today / Yesterday / a picked day, the activity as one tap, "+ Add a note", and Save within
 * reach, so "We met" → Save logs today in two taps. No hidden gestures. Drawn like the Log sheet: a bottom panel over
 * a scrim that dismisses it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeMetPanel(state: WeMetUiState, actions: WeMetActions) {
    val sheet = state.sheet
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingPeople by rememberSaveable { mutableStateOf(false) }
    var showAllActivities by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding()
                // Taps on the panel itself must not reach the scrim.
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val circle = sheet.circle
                    Text(
                        if (circle != null) {
                            stringResource(Res.string.met_title_circle, "${circle.emoji} ${circle.name}".trim())
                        } else {
                            stringResource(Res.string.met_title, namesList(sheet.people))
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = actions.onMoreDetails) { Text(stringResource(Res.string.met_more_details)) }
                }
                LogDateChoice(sheet.date, sheet.today, actions.onDate, onPick = { pickingDate = true })
                People(sheet, actions.onTogglePerson, onAdd = { pickingPeople = true })
                Activities(sheet, showAllActivities, onToggleMore = { showAllActivities = !showAllActivities }, onActivity = actions.onActivity)
                if (state.placeOpen) {
                    // The Log sheet's place field (F11): a named place recognises itself and suggests who's usually there.
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
                if (state.noteOpen) {
                    OutlinedTextField(
                        sheet.note,
                        actions.onNote,
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(CoreRes.string.log_note_hint)) },
                    )
                }
                if (!state.noteOpen || !state.placeOpen) {
                    Row {
                        if (!state.noteOpen) AddButton(stringResource(Res.string.met_add_note), actions.onOpenNote)
                        if (!state.placeOpen) AddButton(stringResource(Res.string.met_add_place), actions.onOpenPlace)
                    }
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onSave()
                    },
                    enabled = sheet.canSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(saveLabel(sheet)) }
            }
        }
    }
    if (pickingDate) {
        LocalDatePickerDialog(sheet.date, onDismiss = { pickingDate = false }, onPicked = actions.onDate, latest = sheet.today)
    }
    if (pickingPeople) {
        PeoplePickerSheet(
            sheet.everyone,
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

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
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

/** F3: the six most used activities as tiles, then "More". Tapping the chosen one again clears it. */
@Composable
private fun Activities(sheet: LogSheetState, showAll: Boolean, onToggleMore: () -> Unit, onActivity: (HangoutActivity?) -> Unit) {
    val shown = if (showAll) sheet.activities else sheet.activities.take(ObserveActivitiesUseCase.SHOWN)
    // null is the "More" / "Less" tile.
    val tiles: List<HangoutActivity?> = shown + listOfNotNull(sheet.activity?.takeIf { it !in shown }) +
        (if (sheet.activities.size > ObserveActivitiesUseCase.SHOWN) listOf(null) else emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    if (a == null) {
                        Tile(
                            label = stringResource(if (showAll) CoreRes.string.log_less else CoreRes.string.log_more),
                            icon = { Icon(Icons.Rounded.MoreHoriz, null) },
                            selected = false,
                            onClick = onToggleMore,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Tile(
                            label = activityLabel(a),
                            icon = { Icon(activityIcon(a), null) },
                            selected = sheet.activity == a,
                            onClick = { onActivity(a.takeIf { sheet.activity != a }) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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
