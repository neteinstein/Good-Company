package org.goodcompany.app.feature.settings.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.goodcompany.app.core.domain.usecase.BACKUP_PASSWORD_MIN_LENGTH
import org.goodcompany.app.core.ui.resources.cancel
import org.goodcompany.app.core.ui.resources.save
import org.goodcompany.app.feature.settings.resources.Res
import org.goodcompany.app.feature.settings.resources.api_key_hide
import org.goodcompany.app.feature.settings.resources.api_key_show
import org.goodcompany.app.feature.settings.resources.backup_password_change_note
import org.goodcompany.app.feature.settings.resources.backup_password_change_title
import org.goodcompany.app.feature.settings.resources.backup_password_confirm
import org.goodcompany.app.feature.settings.resources.backup_password_current
import org.goodcompany.app.feature.settings.resources.backup_password_mismatch
import org.goodcompany.app.feature.settings.resources.backup_password_new
import org.goodcompany.app.feature.settings.resources.backup_password_set_body
import org.goodcompany.app.feature.settings.resources.backup_password_set_title
import org.goodcompany.app.feature.settings.resources.backup_password_too_short
import org.goodcompany.app.feature.settings.resources.backup_password_wrong
import org.goodcompany.app.feature.settings.resources.backup_recovery_body
import org.goodcompany.app.feature.settings.resources.backup_recovery_saved
import org.goodcompany.app.feature.settings.resources.backup_recovery_title
import org.goodcompany.app.feature.settings.resources.backup_unlock_body
import org.goodcompany.app.feature.settings.resources.backup_unlock_button
import org.goodcompany.app.feature.settings.resources.backup_unlock_field
import org.goodcompany.app.feature.settings.resources.backup_unlock_title
import org.goodcompany.app.feature.settings.resources.backup_unlock_wrong
import org.goodcompany.app.feature.settings.resources.drive_old_file_body
import org.goodcompany.app.feature.settings.resources.drive_old_file_delete
import org.goodcompany.app.feature.settings.resources.drive_old_file_keep
import org.goodcompany.app.feature.settings.resources.drive_old_file_title
import org.jetbrains.compose.resources.stringResource
import org.goodcompany.app.core.ui.resources.Res as CoreRes

/** D4: "Set a backup password", with the warning that nobody can recover the data without it or the recovery key. */
@Composable
internal fun SetBackupPasswordDialog(busy: Boolean, error: PasswordError?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(stringResource(Res.string.backup_password_set_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.backup_password_set_body))
                PasswordField(password, { password = it }, stringResource(Res.string.backup_password_new))
                PasswordField(confirm, { confirm = it }, stringResource(Res.string.backup_password_confirm))
                ErrorLine(error)
            }
        },
        confirmButton = { SaveButton(busy, enabled = password.isNotEmpty() && confirm.isNotEmpty()) { onSave(password, confirm) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** D4 as built: changing the password asks for the current one or the recovery key. */
@Composable
internal fun ChangeBackupPasswordDialog(
    busy: Boolean,
    error: PasswordError?,
    onSave: (current: String, password: String, confirm: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(stringResource(Res.string.backup_password_change_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PasswordField(current, { current = it }, stringResource(Res.string.backup_password_current))
                PasswordField(password, { password = it }, stringResource(Res.string.backup_password_new))
                PasswordField(confirm, { confirm = it }, stringResource(Res.string.backup_password_confirm))
                ErrorLine(error)
                Text(
                    stringResource(Res.string.backup_password_change_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            SaveButton(busy, enabled = current.isNotEmpty() && password.isNotEmpty() && confirm.isNotEmpty()) {
                onSave(current, password, confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** D4: the recovery key, shown once. It can only be closed with "I’ve saved it". */
@Composable
internal fun RecoveryKeyDialog(recoveryKey: String, onSaved: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text(stringResource(Res.string.backup_recovery_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer {
                    Text(
                        recoveryKey,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(stringResource(Res.string.backup_recovery_body))
            }
        },
        confirmButton = { TextButton(onClick = onSaved) { Text(stringResource(Res.string.backup_recovery_saved)) } },
    )
}

/** D4: an encrypted backup asks for its password or the recovery key before the "Replace everything?" step. */
@Composable
internal fun UnlockBackupDialog(busy: Boolean, failed: Boolean, onUnlock: (String) -> Unit, onDismiss: () -> Unit) {
    var secret by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(stringResource(Res.string.backup_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.backup_unlock_body))
                PasswordField(secret, { secret = it }, stringResource(Res.string.backup_unlock_field))
                if (failed) Text(stringResource(Res.string.backup_unlock_wrong), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onUnlock(secret) }, enabled = !busy && secret.isNotBlank()) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.backup_unlock_button))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** D5: after the first encrypted Drive backup, offer to delete the old plain, visible file. Never without asking. */
@Composable
internal fun OldDriveFileDialog(onDelete: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.CloudOff, null) },
        title = { Text(stringResource(Res.string.drive_old_file_title)) },
        text = { Text(stringResource(Res.string.drive_old_file_body)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(Res.string.drive_old_file_delete)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(stringResource(Res.string.drive_old_file_keep)) } },
    )
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (visible) Res.string.api_key_hide else Res.string.api_key_show),
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun ErrorLine(error: PasswordError?) {
    val text = when (error) {
        PasswordError.TOO_SHORT -> stringResource(Res.string.backup_password_too_short, BACKUP_PASSWORD_MIN_LENGTH)
        PasswordError.MISMATCH -> stringResource(Res.string.backup_password_mismatch)
        PasswordError.WRONG_SECRET -> stringResource(Res.string.backup_password_wrong)
        null -> return
    }
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SaveButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled && !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(CoreRes.string.save))
    }
}
