package com.neteinstein.goodcompany.feature.settings.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.weekdayShort
import com.neteinstein.goodcompany.core.ui.l10n.circleLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.FileKind
import com.neteinstein.goodcompany.core.ui.platform.PlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.currentPlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberDeviceAuthenticator
import com.neteinstein.goodcompany.core.ui.platform.rememberFileSaver
import com.neteinstein.goodcompany.core.ui.platform.rememberGoogleDriveAuthorizer
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.lock_reason
import com.neteinstein.goodcompany.core.ui.resources.remove
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.ai
import com.neteinstein.goodcompany.feature.settings.resources.ai_hint
import com.neteinstein.goodcompany.feature.settings.resources.allow_notifications
import com.neteinstein.goodcompany.feature.settings.resources.api_key
import com.neteinstein.goodcompany.feature.settings.resources.api_key_hide
import com.neteinstein.goodcompany.feature.settings.resources.api_key_show
import com.neteinstein.goodcompany.feature.settings.resources.app_lock
import com.neteinstein.goodcompany.feature.settings.resources.app_lock_hint
import com.neteinstein.goodcompany.feature.settings.resources.appearance
import com.neteinstein.goodcompany.feature.settings.resources.backup_confirm_body
import com.neteinstein.goodcompany.feature.settings.resources.backup_confirm_title
import com.neteinstein.goodcompany.feature.settings.resources.backup_export
import com.neteinstein.goodcompany.feature.settings.resources.backup_export_button
import com.neteinstein.goodcompany.feature.settings.resources.backup_export_hint
import com.neteinstein.goodcompany.feature.settings.resources.backup_restore
import com.neteinstein.goodcompany.feature.settings.resources.backup_restore_button
import com.neteinstein.goodcompany.feature.settings.resources.backup_restore_hint
import com.neteinstein.goodcompany.feature.settings.resources.circles
import com.neteinstein.goodcompany.feature.settings.resources.circles_hint
import com.neteinstein.goodcompany.feature.settings.resources.digest
import com.neteinstein.goodcompany.feature.settings.resources.digest_day
import com.neteinstein.goodcompany.feature.settings.resources.digest_hint
import com.neteinstein.goodcompany.feature.settings.resources.drive_backup_button
import com.neteinstein.goodcompany.feature.settings.resources.drive_confirm_body
import com.neteinstein.goodcompany.feature.settings.resources.drive_hint
import com.neteinstein.goodcompany.feature.settings.resources.drive_last
import com.neteinstein.goodcompany.feature.settings.resources.drive_restore_button
import com.neteinstein.goodcompany.feature.settings.resources.drive_title
import com.neteinstein.goodcompany.feature.settings.resources.dynamic_color
import com.neteinstein.goodcompany.feature.settings.resources.dynamic_color_hint
import com.neteinstein.goodcompany.feature.settings.resources.eyebrow
import com.neteinstein.goodcompany.feature.settings.resources.footer
import com.neteinstein.goodcompany.feature.settings.resources.home_area
import com.neteinstein.goodcompany.feature.settings.resources.home_area_placeholder
import com.neteinstein.goodcompany.feature.settings.resources.import
import com.neteinstein.goodcompany.feature.settings.resources.import_vcf
import com.neteinstein.goodcompany.feature.settings.resources.import_vcf_hint
import com.neteinstein.goodcompany.feature.settings.resources.language_note
import com.neteinstein.goodcompany.feature.settings.resources.msg_backup_invalid
import com.neteinstein.goodcompany.feature.settings.resources.msg_backup_restored
import com.neteinstein.goodcompany.feature.settings.resources.msg_backup_saved
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_failed
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_forbidden
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_full
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_none
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_not_configured
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_offline
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_saved
import com.neteinstein.goodcompany.feature.settings.resources.msg_drive_unauthorized
import com.neteinstein.goodcompany.feature.settings.resources.msg_key_removed
import com.neteinstein.goodcompany.feature.settings.resources.msg_key_saved
import com.neteinstein.goodcompany.feature.settings.resources.msg_lock_failed
import com.neteinstein.goodcompany.feature.settings.resources.msg_notifications_off
import com.neteinstein.goodcompany.feature.settings.resources.msg_notifications_on
import com.neteinstein.goodcompany.feature.settings.resources.msg_sample_added
import com.neteinstein.goodcompany.feature.settings.resources.msg_sample_removed
import com.neteinstein.goodcompany.feature.settings.resources.msg_vcf_empty
import com.neteinstein.goodcompany.feature.settings.resources.msg_vcf_result
import com.neteinstein.goodcompany.feature.settings.resources.pings_at
import com.neteinstein.goodcompany.feature.settings.resources.privacy
import com.neteinstein.goodcompany.feature.settings.resources.privacy_base
import com.neteinstein.goodcompany.feature.settings.resources.privacy_call_log
import com.neteinstein.goodcompany.feature.settings.resources.privacy_contacts
import com.neteinstein.goodcompany.feature.settings.resources.privacy_contacts_write
import com.neteinstein.goodcompany.feature.settings.resources.privacy_end
import com.neteinstein.goodcompany.feature.settings.resources.private_notifications
import com.neteinstein.goodcompany.feature.settings.resources.private_notifications_hint
import com.neteinstein.goodcompany.feature.settings.resources.quiet_note
import com.neteinstein.goodcompany.feature.settings.resources.reminders
import com.neteinstein.goodcompany.feature.settings.resources.replay_intro
import com.neteinstein.goodcompany.feature.settings.resources.sample
import com.neteinstein.goodcompany.feature.settings.resources.sample_add_hint
import com.neteinstein.goodcompany.feature.settings.resources.sample_loaded
import com.neteinstein.goodcompany.feature.settings.resources.sample_remove_hint
import com.neteinstein.goodcompany.feature.settings.resources.show_warmth
import com.neteinstein.goodcompany.feature.settings.resources.show_warmth_hint
import com.neteinstein.goodcompany.feature.settings.resources.theme_auto
import com.neteinstein.goodcompany.feature.settings.resources.theme_dark
import com.neteinstein.goodcompany.feature.settings.resources.theme_light
import com.neteinstein.goodcompany.feature.settings.resources.title
import com.neteinstein.goodcompany.feature.settings.resources.web_notifications
import com.neteinstein.goodcompany.feature.settings.resources.weekly_plans
import com.neteinstein.goodcompany.feature.settings.resources.weekly_plans_hint
import com.neteinstein.goodcompany.feature.settings.resources.your_data
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

private val CIRCLE_OPTIONS = listOf(7, 14, 30, 60, 90)

/** Localised snackbar text for a [SettingsMessage]. */
suspend fun SettingsMessage.text(): String = when (this) {
    SettingsMessage.KeySaved -> getString(Res.string.msg_key_saved)
    SettingsMessage.KeyRemoved -> getString(Res.string.msg_key_removed)
    is SettingsMessage.Notifications -> getString(if (granted) Res.string.msg_notifications_on else Res.string.msg_notifications_off)
    SettingsMessage.SampleAdded -> getString(Res.string.msg_sample_added)
    SettingsMessage.SampleRemoved -> getString(Res.string.msg_sample_removed)
    is SettingsMessage.FileImported -> if (result.added + result.updated + result.skipped == 0) {
        getString(Res.string.msg_vcf_empty)
    } else {
        getString(Res.string.msg_vcf_result, result.added, result.updated, result.skipped)
    }
    SettingsMessage.BackupSaved -> getString(Res.string.msg_backup_saved)
    is SettingsMessage.BackupRestored -> getPluralString(Res.plurals.msg_backup_restored, friends, friends)
    SettingsMessage.BackupInvalid -> getString(Res.string.msg_backup_invalid)
    SettingsMessage.DriveBackedUp -> getString(Res.string.msg_drive_saved)
    SettingsMessage.DriveNoBackup -> getString(Res.string.msg_drive_none)
    SettingsMessage.DriveNotConfigured -> getString(Res.string.msg_drive_not_configured)
    is SettingsMessage.DriveFailed -> getString(
        when (reason) {
            CloudBackupFailure.Reason.UNAUTHORIZED -> Res.string.msg_drive_unauthorized
            CloudBackupFailure.Reason.FORBIDDEN -> Res.string.msg_drive_forbidden
            CloudBackupFailure.Reason.STORAGE_FULL -> Res.string.msg_drive_full
            CloudBackupFailure.Reason.NETWORK -> Res.string.msg_drive_offline
            CloudBackupFailure.Reason.OTHER -> Res.string.msg_drive_failed
        },
    )
    SettingsMessage.LockFailed -> getString(Res.string.msg_lock_failed)
}

@Composable
fun SettingsScreen(appVersion: String, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestNotifications = rememberPermissionRequester(AppPermission.NOTIFICATIONS, viewModel::onNotificationPermissionResult)
    val pickVcf = rememberTextFilePicker(FileKind.VCARD, onPicked = viewModel::onContactsFilePicked)
    val pickBackup = rememberTextFilePicker(FileKind.JSON, onPicked = viewModel::onBackupPicked)
    val capabilities = currentPlatformCapabilities()
    val saveFile = rememberFileSaver("application/json", onDone = viewModel::onExportHandled)
    val authenticator = rememberDeviceAuthenticator()
    val authorizeDrive = rememberGoogleDriveAuthorizer(viewModel::onDriveAuthResult)
    val lockReason = stringResource(CoreRes.string.lock_reason)
    LaunchedEffect(state.export) { state.export?.let { saveFile.save(it.name, it.content) } }
    SettingsContent(
        state = state,
        capabilities = capabilities,
        canLock = authenticator.isAvailable || state.settings.appLock,
        appVersion = appVersion,
        actions = SettingsActions(
            onSaveApiKey = viewModel::onSaveApiKey,
            onClearApiKey = viewModel::onClearApiKey,
            onThemeChange = viewModel::onThemeChange,
            onDynamicColorChange = viewModel::onDynamicColorChange,
            onWeeklyIdeasChange = viewModel::onWeeklyIdeasChange,
            onHomeAreaChange = viewModel::onHomeAreaChange,
            onReminderHourChange = viewModel::onReminderHourChange,
            onCircleDaysChange = viewModel::onCircleDaysChange,
            onImportContactsFile = pickVcf,
            onEnableNotifications = requestNotifications,
            onLoadSampleData = viewModel::onLoadSampleData,
            onRemoveSampleData = viewModel::onRemoveSampleData,
            onReplayIntro = viewModel::onReplayIntro,
            onMessageShown = viewModel::onMessageShown,
            onWeeklyDigestChange = viewModel::onWeeklyDigestChange,
            onDigestDayChange = viewModel::onDigestDayChange,
            onPrivateNotificationsChange = viewModel::onPrivateNotificationsChange,
            onShowWarmthChange = viewModel::onShowWarmthChange,
            onAppLockChange = { enabled ->
                if (enabled) {
                    authenticator.authenticate(lockReason) { ok -> viewModel.onAppLockChange(true, ok) }
                } else {
                    viewModel.onAppLockChange(false, verified = true)
                }
            },
            onExportBackup = viewModel::onExportBackup,
            onRestoreBackup = pickBackup,
            onConfirmRestore = viewModel::onConfirmRestore,
            onCancelRestore = viewModel::onCancelRestore,
            onDriveBackup = {
                viewModel.onDriveRequested(DriveAction.BACKUP)
                authorizeDrive()
            },
            onDriveRestore = {
                viewModel.onDriveRequested(DriveAction.RESTORE)
                authorizeDrive()
            },
        ),
    )
}

data class SettingsActions(
    val onSaveApiKey: (String) -> Unit = {},
    val onClearApiKey: () -> Unit = {},
    val onThemeChange: (ThemeMode) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onWeeklyIdeasChange: (Boolean) -> Unit = {},
    val onHomeAreaChange: (String) -> Unit = {},
    val onReminderHourChange: (Int) -> Unit = {},
    val onCircleDaysChange: (Circle, Int) -> Unit = { _, _ -> },
    val onImportContactsFile: () -> Unit = {},
    val onEnableNotifications: () -> Unit = {},
    val onLoadSampleData: () -> Unit = {},
    val onRemoveSampleData: () -> Unit = {},
    val onReplayIntro: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onWeeklyDigestChange: (Boolean) -> Unit = {},
    val onDigestDayChange: (Int) -> Unit = {},
    val onPrivateNotificationsChange: (Boolean) -> Unit = {},
    val onShowWarmthChange: (Boolean) -> Unit = {},
    val onAppLockChange: (Boolean) -> Unit = {},
    val onExportBackup: () -> Unit = {},
    val onRestoreBackup: () -> Unit = {},
    val onConfirmRestore: () -> Unit = {},
    val onCancelRestore: () -> Unit = {},
    val onDriveBackup: () -> Unit = {},
    val onDriveRestore: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    capabilities: PlatformCapabilities,
    appVersion: String,
    actions: SettingsActions,
    canLock: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.text())
            actions.onMessageShown()
        }
    }
    val s = state.settings
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ScreenHeader(eyebrow = stringResource(Res.string.eyebrow), title = stringResource(Res.string.title))

            SectionCard(stringResource(Res.string.appearance), icon = Icons.Rounded.Palette) {
                val modes = listOf(
                    Triple(ThemeMode.SYSTEM, stringResource(Res.string.theme_auto), Icons.Rounded.SettingsBrightness),
                    Triple(ThemeMode.LIGHT, stringResource(Res.string.theme_light), Icons.Rounded.LightMode),
                    Triple(ThemeMode.DARK, stringResource(Res.string.theme_dark), Icons.Rounded.DarkMode),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    modes.forEachIndexed { i, (mode, label, icon) ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { actions.onThemeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = { Icon(icon, null, Modifier.size(18.dp)) },
                            label = { Text(label) },
                        )
                    }
                }
                if (capabilities.hasDynamicColor) {
                    SwitchRow(
                        stringResource(Res.string.dynamic_color),
                        stringResource(Res.string.dynamic_color_hint),
                        s.dynamicColor,
                        actions.onDynamicColorChange,
                    )
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.language_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(stringResource(Res.string.circles), icon = Icons.Rounded.Diversity3) {
                Text(
                    stringResource(Res.string.circles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                listOf(
                    Circle.INNER to s.innerCircleDays,
                    Circle.CLOSE to s.closeCircleDays,
                    Circle.GOOD to s.goodFriendsDays,
                ).forEach { (circle, days) ->
                    Text(
                        circleLabel(circle),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (CIRCLE_OPTIONS + days).distinct().sorted().forEach { d ->
                            FilterChip(days == d, { actions.onCircleDaysChange(circle, d) }, label = { Text(everyNDays(d)) })
                        }
                    }
                }
                SwitchRow(
                    stringResource(Res.string.show_warmth),
                    stringResource(Res.string.show_warmth_hint),
                    s.showWarmth,
                    actions.onShowWarmthChange,
                )
            }

            SectionCard(stringResource(Res.string.ai), icon = Icons.Rounded.AutoAwesome) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ApiKeyField(state.maskedKey, actions.onSaveApiKey, actions.onClearApiKey)
                    Text(
                        stringResource(Res.string.ai_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    var area by rememberSaveable(s.homeArea) { mutableStateOf(s.homeArea) }
                    OutlinedTextField(
                        value = area,
                        onValueChange = {
                            area = it
                            actions.onHomeAreaChange(it)
                        },
                        leadingIcon = { Icon(Icons.Rounded.Place, null) },
                        label = { Text(stringResource(Res.string.home_area)) },
                        placeholder = { Text(stringResource(Res.string.home_area_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                SwitchRow(
                    stringResource(Res.string.weekly_plans),
                    stringResource(Res.string.weekly_plans_hint),
                    s.weeklyIdeasEnabled,
                    actions.onWeeklyIdeasChange,
                )
            }

            SectionCard(stringResource(Res.string.reminders), icon = Icons.Rounded.Notifications) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (capabilities.hasBackgroundNotifications) {
                        FilledTonalButton(onClick = actions.onEnableNotifications) {
                            Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.allow_notifications))
                        }
                    } else {
                        Text(
                            stringResource(Res.string.web_notifications),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(stringResource(Res.string.pings_at), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 9, 10, 12, 18).forEach { h ->
                            FilterChip(s.reminderHour == h, { actions.onReminderHourChange(h) }, label = { Text("$h:00") })
                        }
                    }
                }
                if (capabilities.hasBackgroundNotifications) {
                    SwitchRow(stringResource(Res.string.digest), stringResource(Res.string.digest_hint), s.weeklyDigest, actions.onWeeklyDigestChange)
                    if (s.weeklyDigest) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(Res.string.digest_day), style = MaterialTheme.typography.labelLarge)
                            DayOfWeek.entries.forEach { day ->
                                val iso = day.isoDayNumber
                                FilterChip(s.digestDay == iso, { actions.onDigestDayChange(iso) }, label = { Text(weekdayShort(day)) })
                            }
                        }
                    }
                    SwitchRow(
                        stringResource(Res.string.private_notifications),
                        stringResource(Res.string.private_notifications_hint),
                        s.privateNotifications,
                        actions.onPrivateNotificationsChange,
                    )
                    Text(
                        stringResource(Res.string.quiet_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            SectionCard(stringResource(Res.string.your_data), icon = Icons.Rounded.Storage) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(if (state.hasSampleData) Res.string.sample_loaded else Res.string.sample),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(if (state.hasSampleData) Res.string.sample_remove_hint else Res.string.sample_add_hint),
                        )
                    },
                    trailingContent = {
                        AnimatedContent(state.hasSampleData) { loaded ->
                            if (loaded) {
                                OutlinedButton(onClick = actions.onRemoveSampleData) { Text(stringResource(CoreRes.string.remove)) }
                            } else {
                                FilledTonalButton(onClick = actions.onLoadSampleData) { Text(stringResource(CoreRes.string.add)) }
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.UploadFile, null) },
                    headlineContent = { Text(stringResource(Res.string.import_vcf), style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(Res.string.import_vcf_hint)) },
                    trailingContent = { FilledTonalButton(onClick = actions.onImportContactsFile) { Text(stringResource(Res.string.import)) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Download, null) },
                    headlineContent = { Text(stringResource(Res.string.backup_export), style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(Res.string.backup_export_hint)) },
                    trailingContent = {
                        FilledTonalButton(onClick = actions.onExportBackup) { Text(stringResource(Res.string.backup_export_button)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Restore, null) },
                    headlineContent = { Text(stringResource(Res.string.backup_restore), style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(Res.string.backup_restore_hint)) },
                    trailingContent = {
                        OutlinedButton(onClick = actions.onRestoreBackup) { Text(stringResource(Res.string.backup_restore_button)) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (capabilities.hasGoogleDrive) {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.CloudUpload, null) },
                        headlineContent = { Text(stringResource(Res.string.drive_title), style = MaterialTheme.typography.titleSmall) },
                        supportingContent = {
                            Text(
                                s.lastDriveBackupAt?.let { stringResource(Res.string.drive_last, relativePast(it, state.now, state.zone)) }
                                    ?: stringResource(Res.string.drive_hint),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(onClick = actions.onDriveBackup, enabled = !state.driveBusy) {
                            Text(stringResource(Res.string.drive_backup_button))
                        }
                        OutlinedButton(onClick = actions.onDriveRestore, enabled = !state.driveBusy) {
                            Text(stringResource(Res.string.drive_restore_button))
                        }
                        if (state.driveBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                ListItem(
                    modifier = Modifier.clickable(onClick = actions.onReplayIntro),
                    leadingContent = { Icon(Icons.Rounded.Replay, null) },
                    headlineContent = { Text(stringResource(Res.string.replay_intro), style = MaterialTheme.typography.titleSmall) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            SectionCard(stringResource(Res.string.privacy), icon = Icons.Rounded.Shield) {
                Text(
                    listOfNotNull(
                        stringResource(Res.string.privacy_base),
                        when {
                            capabilities.hasContactWriteBack -> stringResource(Res.string.privacy_contacts_write)
                            capabilities.hasAddressBook -> stringResource(Res.string.privacy_contacts)
                            else -> null
                        },
                        if (capabilities.hasCallLog) stringResource(Res.string.privacy_call_log) else null,
                        stringResource(Res.string.privacy_end),
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (canLock) {
                    SwitchRow(stringResource(Res.string.app_lock), stringResource(Res.string.app_lock_hint), s.appLock, actions.onAppLockChange)
                }
            }
            if (state.pendingRestore != null) {
                AlertDialog(
                    onDismissRequest = actions.onCancelRestore,
                    icon = { Icon(Icons.Rounded.Restore, null) },
                    title = { Text(stringResource(Res.string.backup_confirm_title)) },
                    text = {
                        Text(
                            state.pendingRestoreFrom?.let {
                                stringResource(Res.string.drive_confirm_body, relativePast(it, state.now, state.zone))
                            } ?: stringResource(Res.string.backup_confirm_body),
                        )
                    },
                    confirmButton = { TextButton(onClick = actions.onConfirmRestore) { Text(stringResource(Res.string.backup_restore_button)) } },
                    dismissButton = { TextButton(onClick = actions.onCancelRestore) { Text(stringResource(CoreRes.string.cancel)) } },
                )
            }
            Text(
                stringResource(Res.string.footer, appVersion, capabilities.name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp).align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ApiKeyField(maskedKey: String?, onSave: (String) -> Unit, onClear: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(maskedKey != null && !editing) { saved ->
        if (saved) {
            ListItem(
                modifier = Modifier.clickable { editing = true },
                leadingContent = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(Res.string.api_key), style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(maskedKey.orEmpty()) },
                trailingContent = { TextButton(onClick = onClear) { Text(stringResource(CoreRes.string.remove)) } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            )
        } else {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(Res.string.api_key)) },
                placeholder = { Text("sk-ant-…") },
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                stringResource(if (visible) Res.string.api_key_hide else Res.string.api_key_show),
                            )
                        }
                        TextButton(
                            onClick = {
                                onSave(draft)
                                draft = ""
                                editing = false
                            },
                            enabled = draft.isNotBlank(),
                        ) { Text(stringResource(CoreRes.string.save)) }
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = icon?.let { { Icon(it, null) } },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
