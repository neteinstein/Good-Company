package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.ui.components.BirthdayDraft
import com.neteinstein.goodcompany.core.ui.components.BirthdayFields
import com.neteinstein.goodcompany.core.ui.components.SaveSheetHeader
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.birthday_remind
import com.neteinstein.goodcompany.feature.friends.resources.birthday_remind_hint
import com.neteinstein.goodcompany.feature.friends.resources.birthday_remove
import com.neteinstein.goodcompany.feature.friends.resources.birthday_title
import com.neteinstein.goodcompany.feature.friends.resources.birthday_year_hint
import com.neteinstein.goodcompany.feature.friends.resources.name_contact_hint
import com.neteinstein.goodcompany.feature.friends.resources.name_restore
import com.neteinstein.goodcompany.feature.friends.resources.name_title
import com.neteinstein.goodcompany.feature.friends.resources.photo_adjust
import com.neteinstein.goodcompany.feature.friends.resources.photo_contact_untouched
import com.neteinstein.goodcompany.feature.friends.resources.photo_remove
import com.neteinstein.goodcompany.feature.friends.resources.photo_remove_hint_contact
import com.neteinstein.goodcompany.feature.friends.resources.photo_remove_hint_initials
import com.neteinstein.goodcompany.feature.friends.resources.photo_replace
import com.neteinstein.goodcompany.feature.friends.resources.photo_replace_hint
import com.neteinstein.goodcompany.feature.friends.resources.photo_sheet_title
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** "<name>'s photo" (F14): replace (pick, then crop), adjust the crop, or remove the photo set in the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoOptionsSheet(f: Friend, onReplace: () -> Unit, onAdjust: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(
                stringResource(Res.string.photo_sheet_title, f.displayName.substringBefore(' ')),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.photo_replace)) },
                supportingContent = { Text(stringResource(Res.string.photo_replace_hint)) },
                leadingContent = { Icon(Icons.Rounded.Image, null) },
                colors = transparent,
                modifier = Modifier.clickable(onClick = onReplace),
            )
            if (f.avatar != null) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.photo_adjust)) },
                    leadingContent = { Icon(Icons.Rounded.Crop, null) },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onAdjust),
                )
            }
            if (f.customPhoto != null) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.photo_remove)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (f.photo != null) Res.string.photo_remove_hint_contact else Res.string.photo_remove_hint_initials,
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = onRemove),
                )
            }
            if (f.contactId != null) {
                Text(
                    stringResource(Res.string.photo_contact_untouched),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Edits the name shown everywhere (F19). For someone from contacts it says what the contact is called, and
 * "Use the contact's name" (only while the name differs) goes back to it.
 */
@Composable
internal fun NameDialog(f: Friend, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(f.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (f.contactId != null) {
                    Text(stringResource(Res.string.name_contact_hint, f.name), style = MaterialTheme.typography.bodyMedium)
                    if (f.customName != null) {
                        TextButton(onClick = {
                            onSave(null)
                            onDismiss()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.padding(end = 8.dp))
                            Text(stringResource(Res.string.name_restore))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name)
                onDismiss()
            }, enabled = name.isNotBlank()) { Text(stringResource(CoreRes.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/**
 * The birthday: day and month, an optional year (F4), and "Remind me of <name>'s birthday", on by default (F10).
 * Saving it empty removes the birthday. Save sits at the top, and closing the sheet saves a valid change (#64); the
 * screen offers Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdaySheet(f: Friend, latestYear: Int, onSave: (Birthday?, Boolean) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(BirthdayDraft.of(f.birthday)) }
    var notify by remember { mutableStateOf(f.notifyBirthday) }
    val firstName = f.displayName.substringBefore(' ')
    val valid = draft.isValid(latestYear)
    val save = {
        onSave(draft.toBirthday(latestYear), notify)
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = {
            if (valid && (draft.toBirthday(latestYear) != f.birthday || notify != f.notifyBirthday)) save() else onDismiss()
        },
    ) {
        SaveSheetHeader(
            stringResource(Res.string.birthday_title),
            onSave = save,
            saveEnabled = valid,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BirthdayFields(draft, { draft = it }, latestYear)
            Text(
                stringResource(Res.string.birthday_year_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.birthday_remind, firstName), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = notify, onCheckedChange = { notify = it })
            }
            Text(
                stringResource(Res.string.birthday_remind_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (f.birthday != null) {
                TextButton(
                    onClick = {
                        onSave(null, notify)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(Res.string.birthday_remove), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
