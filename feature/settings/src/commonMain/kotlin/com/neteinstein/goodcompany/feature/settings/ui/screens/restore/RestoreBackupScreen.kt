package com.neteinstein.goodcompany.feature.settings.ui.screens.restore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.HeaderBackButton
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.platform.FileKind
import com.neteinstein.goodcompany.core.ui.platform.PlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.currentPlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberGoogleDriveAuthorizer
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.backup_confirm_body
import com.neteinstein.goodcompany.feature.settings.resources.backup_confirm_title
import com.neteinstein.goodcompany.feature.settings.resources.backup_restore_button
import com.neteinstein.goodcompany.feature.settings.resources.drive_confirm_body
import com.neteinstein.goodcompany.feature.settings.resources.restore_intro_body
import com.neteinstein.goodcompany.feature.settings.resources.restore_intro_drive
import com.neteinstein.goodcompany.feature.settings.resources.restore_intro_file
import com.neteinstein.goodcompany.feature.settings.resources.restore_intro_title
import com.neteinstein.goodcompany.feature.settings.ui.screens.DriveSetupDialog
import com.neteinstein.goodcompany.feature.settings.ui.screens.UnlockBackupDialog
import com.neteinstein.goodcompany.feature.settings.ui.screens.text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/**
 * Issue #166: "Restore a backup" from the intro guide. Restoring marks the intro done, so the app opens on the
 * restored data by itself; [onBack] returns to the guide.
 */
@Composable
fun RestoreBackupScreen(onBack: () -> Unit, viewModel: RestoreBackupViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberTextFilePicker(FileKind.JSON, onPicked = viewModel::onFilePicked)
    val authorizeDrive = rememberGoogleDriveAuthorizer(viewModel::onDriveAuthResult)
    RestoreBackupContent(
        state = state,
        capabilities = currentPlatformCapabilities(),
        actions = RestoreBackupActions(
            onBack = onBack,
            onFromDrive = {
                viewModel.onDriveRequested()
                authorizeDrive()
            },
            onFromFile = pickFile,
            onUnlock = viewModel::onUnlock,
            onCancel = viewModel::onCancel,
            onConfirm = viewModel::onConfirm,
            onDriveSetupShown = viewModel::onDriveSetupShown,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class RestoreBackupActions(
    val onBack: () -> Unit = {},
    val onFromDrive: () -> Unit = {},
    val onFromFile: () -> Unit = {},
    val onUnlock: (String) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDriveSetupShown: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun RestoreBackupContent(state: RestoreBackupUiState, capabilities: PlatformCapabilities, actions: RestoreBackupActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showMessage(it.text())
            actions.onMessageShown()
        }
    }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(Res.string.restore_intro_title), navigation = { HeaderBackButton(actions.onBack) })
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.restore_intro_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (capabilities.hasGoogleDrive) {
                    Button(
                        onClick = actions.onFromDrive,
                        enabled = !state.driveBusy && !state.restored,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (state.driveBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.restore_intro_drive))
                    }
                }
                OutlinedButton(onClick = actions.onFromFile, enabled = !state.restored, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.restore_intro_file))
                    }
                }
            }
        }
    }
    if (state.lockedRestore != null) UnlockBackupDialog(state.cryptoBusy, state.unlockFailed, actions.onUnlock, actions.onCancel)
    state.driveSetup?.let { DriveSetupDialog(it, actions.onDriveSetupShown) }
    if (state.pendingRestore != null) {
        AlertDialog(
            onDismissRequest = actions.onCancel,
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text(stringResource(Res.string.backup_confirm_title)) },
            text = {
                Text(
                    state.pendingRestoreFrom?.let { stringResource(Res.string.drive_confirm_body, relativePast(it, state.now, state.zone)) }
                        ?: stringResource(Res.string.backup_confirm_body),
                )
            },
            confirmButton = { TextButton(onClick = actions.onConfirm) { Text(stringResource(Res.string.backup_restore_button)) } },
            dismissButton = { TextButton(onClick = actions.onCancel) { Text(stringResource(CoreRes.string.cancel)) } },
        )
    }
}
