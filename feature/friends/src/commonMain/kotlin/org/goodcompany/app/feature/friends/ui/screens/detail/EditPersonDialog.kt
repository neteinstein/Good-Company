package org.goodcompany.app.feature.friends.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.goodcompany.app.core.domain.model.CatchUpCounts
import org.goodcompany.app.core.domain.model.Closeness
import org.goodcompany.app.core.domain.model.ContactChannel
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.usecase.CadenceRules
import org.goodcompany.app.core.domain.usecase.SnoozeOption
import org.goodcompany.app.core.ui.components.SectionCard
import org.goodcompany.app.core.ui.components.closenessIcon
import org.goodcompany.app.core.ui.format.shortLabel
import org.goodcompany.app.core.ui.l10n.closenessLabel
import org.goodcompany.app.core.ui.l10n.everyNDays
import org.goodcompany.app.core.ui.l10n.relationToMeLabel
import org.goodcompany.app.core.ui.resources.cadence_off
import org.goodcompany.app.feature.friends.resources.Res
import org.goodcompany.app.feature.friends.resources.about_preferred
import org.goodcompany.app.feature.friends.resources.about_preferred_none
import org.goodcompany.app.feature.friends.resources.detail_edit_name
import org.goodcompany.app.feature.friends.resources.edit_close
import org.goodcompany.app.feature.friends.resources.edit_person
import org.goodcompany.app.feature.friends.resources.edit_person_hint
import org.goodcompany.app.feature.friends.resources.name_title
import org.goodcompany.app.feature.friends.resources.prefs_birthday
import org.goodcompany.app.feature.friends.resources.prefs_birthday_hint
import org.goodcompany.app.feature.friends.resources.prefs_catch_up_counts
import org.goodcompany.app.feature.friends.resources.prefs_counts_any
import org.goodcompany.app.feature.friends.resources.prefs_counts_any_hint
import org.goodcompany.app.feature.friends.resources.prefs_counts_default
import org.goodcompany.app.feature.friends.resources.prefs_counts_default_hint
import org.goodcompany.app.feature.friends.resources.prefs_counts_in_person
import org.goodcompany.app.feature.friends.resources.prefs_counts_in_person_hint
import org.goodcompany.app.feature.friends.resources.prefs_events
import org.goodcompany.app.feature.friends.resources.prefs_events_hint
import org.goodcompany.app.feature.friends.resources.prefs_pause
import org.goodcompany.app.feature.friends.resources.prefs_pause_hint
import org.goodcompany.app.feature.friends.resources.prefs_title
import org.goodcompany.app.feature.friends.resources.prefs_tracking
import org.goodcompany.app.feature.friends.resources.prefs_tracking_hint
import org.goodcompany.app.feature.friends.resources.relation_to_me_add
import org.goodcompany.app.feature.friends.resources.relation_to_me_pick
import org.goodcompany.app.feature.friends.resources.remove_person
import org.goodcompany.app.feature.friends.resources.show_in_my_list
import org.goodcompany.app.feature.friends.resources.show_in_my_list_hint
import org.goodcompany.app.feature.friends.resources.show_in_my_list_hint_any
import org.goodcompany.app.feature.friends.resources.snooze_week
import org.goodcompany.app.feature.friends.resources.snooze_weekend
import org.goodcompany.app.feature.friends.resources.touch_circle
import org.goodcompany.app.feature.friends.resources.touch_custom
import org.goodcompany.app.feature.friends.resources.touch_from_circle
import org.goodcompany.app.feature.friends.resources.touch_from_closeness
import org.goodcompany.app.feature.friends.resources.touch_from_closeness_none
import org.goodcompany.app.feature.friends.resources.touch_rhythm_off_hint
import org.goodcompany.app.feature.friends.resources.touch_snooze_title
import org.goodcompany.app.feature.friends.resources.touch_snoozed
import org.goodcompany.app.feature.friends.resources.touch_unsnooze
import org.jetbrains.compose.resources.stringResource
import org.goodcompany.app.core.ui.resources.Res as CoreRes

/**
 * "Edit <name>" (issue #103): a full-screen popup from the pencil, so editing the person isn't confused with the page
 * and the in-place editing on About. Name, "Related to you?", the preferred channel, closeness, meet-every with snooze
 * and pause (issue #130), the person's switches and Remove. Every choice saves at once (as edit mode did); Close goes back to the page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditPersonDialog(
    f: Friend,
    state: FriendDetailUiState,
    actions: FriendDetailActions,
    onEditName: () -> Unit,
    onEditRelationToMe: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val detail = state.detail ?: return
    val firstName = f.displayName.substringBefore(' ')
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(Res.string.edit_close)) }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(Res.string.edit_person, firstName),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Hint(stringResource(Res.string.edit_person_hint))
                    SectionCard(stringResource(Res.string.edit_person, firstName), icon = Icons.Rounded.Edit) {
                        ListItem(
                            modifier = Modifier.clickable(onClick = onEditName),
                            headlineContent = { Text(f.displayName, style = MaterialTheme.typography.titleSmall) },
                            overlineContent = { Text(stringResource(Res.string.name_title)) },
                            trailingContent = { Icon(Icons.Rounded.Edit, stringResource(Res.string.detail_edit_name)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        ListItem(
                            modifier = Modifier.clickable(onClick = onEditRelationToMe),
                            headlineContent = {
                                Text(
                                    relationToMeLabel(f) ?: stringResource(Res.string.relation_to_me_pick),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            },
                            overlineContent = { Text(stringResource(Res.string.relation_to_me_add)) },
                            trailingContent = { Icon(Icons.Rounded.FavoriteBorder, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        Label(stringResource(Res.string.about_preferred), Icons.AutoMirrored.Rounded.Chat)
                        FlowRow(
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                f.preferredChannel == null,
                                { actions.onSetPreferredChannel(null) },
                                label = { Text(stringResource(Res.string.about_preferred_none)) },
                            )
                            ContactChannel.entries.forEach { c ->
                                FilterChip(f.preferredChannel == c, { actions.onSetPreferredChannel(c) }, label = { Text(channelLabel(c)) })
                            }
                        }
                        Label(stringResource(Res.string.touch_circle), Icons.Rounded.Groups)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Closeness.entries.forEach { c ->
                                FilterChip(
                                    f.closeness == c,
                                    { actions.onClosenessChange(c) },
                                    label = { Text(closenessLabel(c)) },
                                    leadingIcon = { Icon(closenessIcon(c), null, Modifier.size(18.dp)) },
                                )
                            }
                        }
                        // Issue #115: with keep-in-touch tracking off there is no rhythm to pick.
                        if (f.tracksKeepInTouch) {
                            Label(stringResource(Res.string.touch_custom), Icons.Rounded.Schedule)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Issue #139: From closeness (null) · Off (no rhythm) · a custom rhythm.
                                val level = state.cadenceRules.levelDays(f)
                                FilterChip(
                                    f.meetEveryDays == null,
                                    { actions.onMeetEveryChange(null) },
                                    label = {
                                        Text(
                                            level?.let { stringResource(Res.string.touch_from_closeness, everyNDays(it).lowercaseFirst()) }
                                                ?: stringResource(Res.string.touch_from_closeness_none),
                                        )
                                    },
                                )
                                FilterChip(
                                    f.rhythmOff,
                                    { actions.onMeetEveryChange(Friend.RHYTHM_OFF) },
                                    label = { Text(stringResource(CoreRes.string.cadence_off)) },
                                )
                                RHYTHMS.forEach { d ->
                                    FilterChip(f.meetEveryDays == d, { actions.onMeetEveryChange(d) }, label = { Text(everyNDays(d)) })
                                }
                            }
                            val goal = detail.cadenceDays
                            if (f.rhythmOff) {
                                Hint(stringResource(Res.string.touch_rhythm_off_hint))
                            } else if (detail.cadenceSource == CadenceRules.Source.CLOSENESS && goal != null) {
                                Hint(stringResource(Res.string.touch_from_circle, everyNDays(goal)))
                            }
                            CatchUpPause(f, goal, state, actions)
                        }
                    }
                    PersonSwitches(f, state, actions)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.remove_person, f.displayName))
                    }
                }
            }
        }
    }
}

/**
 * The person's own switches: show in my list (for a kid), birthday notifications, AI events, keep-in-touch tracking, and
 * (while tracking is on) what counts as catching up. Pause sits with the rhythm (issue #130).
 */
@Composable
private fun PersonSwitches(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions) {
    val detail = state.detail ?: return
    SectionCard(stringResource(Res.string.prefs_title), icon = Icons.Rounded.Tune) {
        // F9: a kid can be kept out of the main list (they stay on their parent's page and in birthdays).
        val parent = detail.parents.firstOrNull()
        if (parent != null || !f.showInMyList) {
            ToggleRow(
                stringResource(Res.string.show_in_my_list),
                parent?.let { stringResource(Res.string.show_in_my_list_hint, it.displayName.substringBefore(' ')) }
                    ?: stringResource(Res.string.show_in_my_list_hint_any),
                f.showInMyList,
            ) { actions.onSetShowInMyList(!f.showInMyList) }
        }
        ToggleRow(
            stringResource(Res.string.prefs_birthday),
            stringResource(Res.string.prefs_birthday_hint),
            f.notifyBirthday,
            actions.onToggleBirthdayNotifications,
        )
        if (state.aiEnabled) {
            ToggleRow(
                stringResource(Res.string.prefs_events),
                stringResource(Res.string.prefs_events_hint),
                f.suggestEvents,
                actions.onToggleSuggestEvents,
            )
        }
        // Issue #115: the permanent switch. Pause (with the rhythm) stays the temporary one, and it and what counts
        // only matter while tracking is on, so they're hidden while it is off.
        ToggleRow(
            stringResource(Res.string.prefs_tracking),
            stringResource(Res.string.prefs_tracking_hint),
            f.tracksKeepInTouch,
            actions.onToggleTracking,
        )
        if (f.tracksKeepInTouch) CatchUpCountsChoice(f.catchUpCounts, actions.onSetCatchUpCounts)
    }
}

/**
 * What restarts this person's catch-up countdown (decision 2026-11-21 "Catch-up · what counts"): what Settings → How
 * ticks (the default), in person only, or any contact. The hint under the chips says what the chosen one means.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatchUpCountsChoice(selected: CatchUpCounts, onSelect: (CatchUpCounts) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(Res.string.prefs_catch_up_counts), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CatchUpCounts.entries.forEach { c ->
                FilterChip(
                    selected = c == selected,
                    onClick = { onSelect(c) },
                    label = {
                        Text(
                            stringResource(
                                when (c) {
                                    CatchUpCounts.DEFAULT -> Res.string.prefs_counts_default
                                    CatchUpCounts.IN_PERSON_ONLY -> Res.string.prefs_counts_in_person
                                    CatchUpCounts.ANY_CONTACT -> Res.string.prefs_counts_any
                                },
                            ),
                        )
                    },
                )
            }
        }
        Text(
            stringResource(
                when (selected) {
                    CatchUpCounts.DEFAULT -> Res.string.prefs_counts_default_hint
                    CatchUpCounts.IN_PERSON_ONLY -> Res.string.prefs_counts_in_person_hint
                    CatchUpCounts.ANY_CONTACT -> Res.string.prefs_counts_any_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Next to the rhythm (issue #130, moved from Together): snooze for the weekend or a week, or "Snoozed until …" with
 * Resume, and the pause switch (its state is the switch).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatchUpPause(f: Friend, goal: Int?, state: FriendDetailUiState, actions: FriendDetailActions) {
    val snoozedUntil = f.snoozedUntil?.takeIf { it >= state.today }
    if (!f.catchUpPaused && (snoozedUntil != null || goal != null)) {
        Label(stringResource(Res.string.touch_snooze_title), Icons.Rounded.Snooze)
        if (snoozedUntil != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.touch_snoozed, snoozedUntil.shortLabel()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onClearSnooze) { Text(stringResource(Res.string.touch_unsnooze)) }
            }
        } else {
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = { actions.onSnooze(SnoozeOption.WEEKEND) },
                    label = { Text(stringResource(Res.string.snooze_weekend)) },
                    leadingIcon = { Icon(Icons.Rounded.Weekend, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { actions.onSnooze(SnoozeOption.ONE_WEEK) },
                    label = { Text(stringResource(Res.string.snooze_week)) },
                    leadingIcon = { Icon(Icons.Rounded.Snooze, null, Modifier.size(18.dp)) },
                )
            }
        }
    }
    ToggleRow(
        stringResource(Res.string.prefs_pause),
        stringResource(Res.string.prefs_pause_hint),
        f.catchUpPaused,
        actions.onTogglePause,
    )
}

/** "Every 2 weeks" → "every 2 weeks", for the From closeness chip. */
private fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercaseChar() }
