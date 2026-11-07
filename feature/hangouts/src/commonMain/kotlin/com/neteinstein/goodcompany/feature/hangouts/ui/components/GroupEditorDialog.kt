package com.neteinstein.goodcompany.feature.hangouts.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.resources.cadence_off
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.delete
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.group_editor_edit
import com.neteinstein.goodcompany.feature.hangouts.resources.group_editor_new
import com.neteinstein.goodcompany.feature.hangouts.resources.group_emoji
import com.neteinstein.goodcompany.feature.hangouts.resources.group_members
import com.neteinstein.goodcompany.feature.hangouts.resources.group_name
import com.neteinstein.goodcompany.feature.hangouts.resources.group_rhythm
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.GroupDraft
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

private val RHYTHMS = listOf(7, 14, 30, 90)

/** Create or edit a saved group: name, emoji, members and how often you'd like to all meet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupEditorDialog(
    draft: GroupDraft,
    friends: List<Friend>,
    onChange: (GroupDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Groups, null) },
        title = { Text(stringResource(if (draft.id == null) Res.string.group_editor_new else Res.string.group_editor_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        draft.emoji,
                        { onChange(draft.copy(emoji = it.take(4))) },
                        label = { Text(stringResource(Res.string.group_emoji)) },
                        singleLine = true,
                        modifier = Modifier.width(88.dp),
                    )
                    OutlinedTextField(
                        draft.name,
                        { onChange(draft.copy(name = it)) },
                        label = { Text(stringResource(Res.string.group_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(Res.string.group_members), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    friends.sortedByDescending { it.isCloseFriend || it.id in draft.members }.forEach { f ->
                        val on = f.id in draft.members
                        FilterChip(
                            selected = on,
                            onClick = { onChange(draft.copy(members = if (on) draft.members - f.id else draft.members + f.id)) },
                            label = { Text(f.name) },
                            leadingIcon = { FriendAvatar(f.name, f.initials, size = 20.dp) },
                        )
                    }
                }
                Text(stringResource(Res.string.group_rhythm), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.meetEveryDays == null, {
                        onChange(draft.copy(meetEveryDays = null))
                    }, label = { Text(stringResource(CoreRes.string.cadence_off)) })
                    RHYTHMS.forEach { d ->
                        FilterChip(draft.meetEveryDays == d, { onChange(draft.copy(meetEveryDays = d)) }, label = { Text(everyNDays(d)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = draft.canSave) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(CoreRes.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
            }
        },
    )
}
