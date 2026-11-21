package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

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
import androidx.compose.material.icons.rounded.Tune
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
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.closenessIcon
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.l10n.relationToMeLabel
import com.neteinstein.goodcompany.core.ui.resources.cadence_off
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.about_preferred
import com.neteinstein.goodcompany.feature.friends.resources.about_preferred_none
import com.neteinstein.goodcompany.feature.friends.resources.detail_edit_name
import com.neteinstein.goodcompany.feature.friends.resources.edit_close
import com.neteinstein.goodcompany.feature.friends.resources.edit_person
import com.neteinstein.goodcompany.feature.friends.resources.edit_person_hint
import com.neteinstein.goodcompany.feature.friends.resources.name_title
import com.neteinstein.goodcompany.feature.friends.resources.prefs_birthday
import com.neteinstein.goodcompany.feature.friends.resources.prefs_birthday_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_contact_counts
import com.neteinstein.goodcompany.feature.friends.resources.prefs_contact_counts_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_events
import com.neteinstein.goodcompany.feature.friends.resources.prefs_events_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_pause
import com.neteinstein.goodcompany.feature.friends.resources.prefs_pause_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_title
import com.neteinstein.goodcompany.feature.friends.resources.relation_to_me_add
import com.neteinstein.goodcompany.feature.friends.resources.relation_to_me_pick
import com.neteinstein.goodcompany.feature.friends.resources.remove_person
import com.neteinstein.goodcompany.feature.friends.resources.show_in_my_list
import com.neteinstein.goodcompany.feature.friends.resources.show_in_my_list_hint
import com.neteinstein.goodcompany.feature.friends.resources.show_in_my_list_hint_any
import com.neteinstein.goodcompany.feature.friends.resources.touch_circle
import com.neteinstein.goodcompany.feature.friends.resources.touch_custom
import com.neteinstein.goodcompany.feature.friends.resources.touch_from_circle
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/**
 * "Edit <name>" (issue #103): a full-screen popup from the pencil, so editing the person isn't confused with the page
 * and the in-place editing on About. Name, "Related to you?", the preferred channel, closeness, meet-every, the
 * person's switches and Remove. Every choice saves at once (as edit mode did); Close goes back to the page.
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
                        Label(stringResource(Res.string.touch_custom), Icons.Rounded.Schedule)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                f.meetEveryDays == null,
                                { actions.onMeetEveryChange(null) },
                                label = { Text(stringResource(CoreRes.string.cadence_off)) },
                            )
                            RHYTHMS.forEach { d ->
                                FilterChip(f.meetEveryDays == d, { actions.onMeetEveryChange(d) }, label = { Text(everyNDays(d)) })
                            }
                        }
                        val goal = detail.cadenceDays
                        if (detail.cadenceSource == CadenceRules.Source.CLOSENESS && goal != null) {
                            Hint(stringResource(Res.string.touch_from_circle, everyNDays(goal)))
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

/** The person's own switches: show in my list (for a kid), birthday notifications, AI events, calls count, pause. */
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
        ToggleRow(
            stringResource(Res.string.prefs_contact_counts),
            stringResource(Res.string.prefs_contact_counts_hint),
            f.contactCountsAsCatchUp,
            actions.onToggleContactCounts,
        )
        ToggleRow(
            stringResource(Res.string.prefs_pause),
            stringResource(Res.string.prefs_pause_hint),
            f.catchUpPaused,
            actions.onTogglePause,
        )
    }
}
