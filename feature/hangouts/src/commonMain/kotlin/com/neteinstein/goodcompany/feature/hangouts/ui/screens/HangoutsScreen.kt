package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.isBuiltIn
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.CircleBackdrop
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.HeaderSettingsButton
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.circlePalette
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.monthLong
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.core.ui.resources.circle_built_in
import com.neteinstein.goodcompany.core.ui.resources.circle_next_plan
import com.neteinstein.goodcompany.core.ui.resources.plan_circle_people
import com.neteinstein.goodcompany.core.ui.resources.plan_make
import com.neteinstein.goodcompany.core.ui.resources.plan_people_count
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.group_delete
import com.neteinstein.goodcompany.feature.hangouts.resources.group_due
import com.neteinstein.goodcompany.feature.hangouts.resources.group_edit
import com.neteinstein.goodcompany.feature.hangouts.resources.group_last_together
import com.neteinstein.goodcompany.feature.hangouts.resources.group_met
import com.neteinstein.goodcompany.feature.hangouts.resources.group_more
import com.neteinstein.goodcompany.feature.hangouts.resources.group_never
import com.neteinstein.goodcompany.feature.hangouts.resources.group_overdue
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snooze_week
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snooze_weekend
import com.neteinstein.goodcompany.feature.hangouts.resources.group_snoozed
import com.neteinstein.goodcompany.feature.hangouts.resources.groups_hint
import com.neteinstein.goodcompany.feature.hangouts.resources.groups_new
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_group_deleted
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_group_snoozed
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_location_denied
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_location_unavailable
import com.neteinstein.goodcompany.feature.hangouts.resources.msg_logged_with
import com.neteinstein.goodcompany.feature.hangouts.resources.title
import com.neteinstein.goodcompany.feature.hangouts.ui.components.CheckInSheet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/**
 * The Circles tab (issue #67, was Together): a card per circle, nothing else (issue #88). Tapping a card opens the
 * circle's page; meetups show there and on each person's timeline.
 */
@Composable
fun HangoutsScreen(
    /** Tapping a circle's card: `CirclePageRoute` (the built-in Family circle too). */
    onOpenCircle: (String) -> Unit,
    /** "+" (a new circle, null) and "Edit" in a card's menu (its id): `CircleEditorRoute`. */
    onEditCircle: (String?) -> Unit = {},
    /** "We met" on a circle card: opens the small "We met" panel on that circle, nobody ticked (issue #47, F20). */
    onCircleMet: (String) -> Unit = {},
    /** The header's Settings button (issue #65). */
    onOpenSettings: () -> Unit = {},
    /** A circle deleted from its page (issue #88): deleted here, so this tab's snackbar offers the Undo. */
    pendingDelete: String? = null,
    onPendingDeleteHandled: () -> Unit = {},
    viewModel: HangoutsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDelete) {
        val id = pendingDelete ?: return@LaunchedEffect
        viewModel.onDeleteGroup(id)
        onPendingDeleteHandled()
    }
    val requestLocation = rememberPermissionRequester(AppPermission.LOCATION, viewModel::onLocationPermissionResult)
    HangoutsContent(
        state = state,
        actions = HangoutsActions(
            onOpenCircle = onOpenCircle,
            onUndoDelete = viewModel::onUndoDelete,
            onNewGroup = { onEditCircle(null) },
            onEditGroup = onEditCircle,
            onCheckInGroup = onCircleMet,
            onSnoozeGroup = viewModel::onSnoozeGroup,
            onDeleteGroup = viewModel::onDeleteGroup,
            onMessageShown = viewModel::onMessageShown,
            onPlanWithCircle = viewModel::onPlanWithCircle,
            onOpenSettings = onOpenSettings,
        ),
    )
    state.planDraft?.let { draft ->
        PlanEditor(
            friends = state.friends,
            today = state.today,
            onDismiss = viewModel::onDismissPlan,
            onSave = viewModel::onSavePlan,
            circles = state.groups.map { it.group },
            preselectedCircle = draft.circleId,
        )
    }
    state.draft?.let { draft ->
        CheckInSheet(
            draft = draft,
            place = state.draftPlace,
            friends = state.friends,
            groups = state.groups.map { it.group },
            onPickGroup = viewModel::onPickDraftGroup,
            onToggleFriend = viewModel::onToggleDraftFriend,
            onQueryChange = viewModel::onDraftQueryChange,
            onPlaceChange = viewModel::onDraftPlaceChange,
            onNoteChange = viewModel::onDraftNoteChange,
            onUseLocation = requestLocation,
            onClearLocation = viewModel::onClearDraftLocation,
            onChangePlace = viewModel::onChangeDraftPlace,
            onSave = viewModel::onSaveCheckIn,
            onDismiss = viewModel::onDismissCheckIn,
        )
    }
}

data class HangoutsActions(
    val onOpenCircle: (String) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onNewGroup: () -> Unit = {},
    val onEditGroup: (String) -> Unit = {},
    val onCheckInGroup: (String) -> Unit = {},
    val onSnoozeGroup: (String, SnoozeOption) -> Unit = { _, _ -> },
    val onDeleteGroup: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    /** "Plan" on a circle's card. */
    val onPlanWithCircle: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/** Localised snackbar text for a [HangoutsMessage]. */
suspend fun HangoutsMessage.text(): String = when (this) {
    HangoutsMessage.LocationDenied -> getString(Res.string.msg_location_denied)
    HangoutsMessage.LocationUnavailable -> getString(Res.string.msg_location_unavailable)
    is HangoutsMessage.LoggedWith -> getString(Res.string.msg_logged_with, names.joinToString())
    is HangoutsMessage.GroupDeleted -> getString(Res.string.msg_group_deleted, name)
    is HangoutsMessage.GroupSnoozed -> getString(Res.string.msg_group_snoozed, name, until.shortLabelText())
    HangoutsMessage.PlanSaved -> getString(CoreRes.string.plan_saved)
}

@Composable
fun HangoutsContent(state: HangoutsUiState, actions: HangoutsActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showMessage(message.text(), if (state.recentlyDeletedGroup != null) undo else null)
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete()
        actions.onMessageShown()
    }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(Res.string.title),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(onClick = actions.onNewGroup) {
                                Icon(Icons.Rounded.Add, stringResource(Res.string.groups_new))
                            }
                            HeaderSettingsButton(actions.onOpenSettings)
                        }
                    },
                )
            }
            if (!state.isLoading && state.groups.isEmpty()) {
                item(key = "groups-empty") {
                    Text(
                        stringResource(Res.string.groups_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            items(state.groups, key = { "g-${it.group.id}" }) { g ->
                CircleCard(g, state, actions, Modifier.animateItem())
            }

            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** One circle as a big card in its colour (issue #67): emoji, name, how many and when, the members and "We met". */
@Composable
private fun CircleCard(status: Relationships.GroupStatus, state: HangoutsUiState, actions: HangoutsActions, modifier: Modifier = Modifier) {
    val g = status.group
    val snoozed = g.snoozedUntil?.takeIf { it >= state.today }
    val overdue = status.isOverdue(state.today) && snoozed == null
    val palette = circlePalette(g.color)
    var menu by remember { mutableStateOf(false) }
    // The cover photo (issue #88) sits behind the card under a veil in the circle's colour; no cover = the colour.
    CircleBackdrop(
        g,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable { actions.onOpenCircle(g.id) },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(palette.onAccent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(g.emoji, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        circleStatusLine(status, state.today, state.now, state.zone),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onAccent.copy(alpha = 0.9f),
                    )
                }
                if (!g.isBuiltIn) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.group_more)) }
                        CircleMenu(g.id, g.meetEveryDays != null, menu, onDismiss = { menu = false }, actions = actions)
                    }
                }
            }
            state.circlePlans[g.id]?.let { plan ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (plan.isRecurring) Icons.Rounded.Repeat else Icons.Rounded.EventAvailable, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(CoreRes.string.circle_next_plan, plan.title, plan.date.shortLabel()),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StackedAvatars(status.members, size = 32.dp, max = 6)
                Spacer(Modifier.weight(1f))
                // Overdue: the button stands out (the card's text colour as its fill).
                Button(
                    onClick = { actions.onCheckInGroup(g.id) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (overdue) palette.onAccent else palette.onAccent.copy(alpha = 0.2f),
                        contentColor = if (overdue) palette.accent else palette.onAccent,
                    ),
                ) { Text(stringResource(Res.string.group_met), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
    }
}

/**
 * "6 people · last met 3 days ago" (or the rhythm, the snooze, "Built in", "Never met"): the line under a circle's
 * name, the same on its card and on its page (issue #88).
 */
@Composable
internal fun circleStatusLine(status: Relationships.GroupStatus, today: LocalDate, now: Instant, zone: TimeZone): String {
    val g = status.group
    val snoozed = g.snoozedUntil?.takeIf { it >= today }
    val overdue = status.isOverdue(today) && snoozed == null
    val people = pluralStringResource(CoreRes.plurals.plan_people_count, status.members.size, status.members.size)
    val detail = when {
        snoozed != null -> stringResource(Res.string.group_snoozed, snoozed.shortLabel())
        overdue && g.meetEveryDays != null -> stringResource(Res.string.group_overdue, everyNDays(g.meetEveryDays!!))
        status.dueDate != null && g.meetEveryDays != null ->
            stringResource(Res.string.group_due, relativeDayPhrase(status.dueDate!!, today), everyNDays(g.meetEveryDays!!))
        status.lastTogether != null -> stringResource(Res.string.group_last_together, relativePast(status.lastTogether!!, now, zone))
        g.isBuiltIn -> stringResource(CoreRes.string.circle_built_in)
        else -> stringResource(Res.string.group_never)
    }
    return stringResource(CoreRes.string.plan_circle_people, people, detail)
}

@Composable
private fun CircleMenu(id: String, hasRhythm: Boolean, open: Boolean, onDismiss: () -> Unit, actions: HangoutsActions) {
    DropdownMenu(open, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(CoreRes.string.plan_make)) },
            leadingIcon = { Icon(Icons.Rounded.EventAvailable, null) },
            onClick = {
                onDismiss()
                actions.onPlanWithCircle(id)
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.group_edit)) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = {
                onDismiss()
                actions.onEditGroup(id)
            },
        )
        if (hasRhythm) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.group_snooze_weekend)) },
                leadingIcon = { Icon(Icons.Rounded.Weekend, null) },
                onClick = {
                    onDismiss()
                    actions.onSnoozeGroup(id, SnoozeOption.WEEKEND)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.group_snooze_week)) },
                leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                onClick = {
                    onDismiss()
                    actions.onSnoozeGroup(id, SnoozeOption.ONE_WEEK)
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.group_delete)) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
            onClick = {
                onDismiss()
                actions.onDeleteGroup(id)
            },
        )
    }
}
