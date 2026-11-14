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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.HeaderBackButton
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.weekdayShort
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.FileKind
import com.neteinstein.goodcompany.core.ui.platform.PlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.UpdateInstallResult
import com.neteinstein.goodcompany.core.ui.platform.currentPlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberAppUpdater
import com.neteinstein.goodcompany.core.ui.platform.rememberDeviceAuthenticator
import com.neteinstein.goodcompany.core.ui.platform.rememberFileSaver
import com.neteinstein.goodcompany.core.ui.platform.rememberGoogleDriveAuthorizer
import com.neteinstein.goodcompany.core.ui.platform.rememberNotificationsAllowed
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cadence_off
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.lock_reason
import com.neteinstein.goodcompany.core.ui.resources.remove
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.settings.resources.Res
import com.neteinstein.goodcompany.feature.settings.resources.activities
import com.neteinstein.goodcompany.feature.settings.resources.activities_count
import com.neteinstein.goodcompany.feature.settings.resources.ai
import com.neteinstein.goodcompany.feature.settings.resources.ai_hint
import com.neteinstein.goodcompany.feature.settings.resources.ai_off_note
import com.neteinstein.goodcompany.feature.settings.resources.ai_switch
import com.neteinstein.goodcompany.feature.settings.resources.ai_switch_hint
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
import com.neteinstein.goodcompany.feature.settings.resources.cal_row_off
import com.neteinstein.goodcompany.feature.settings.resources.cal_row_on
import com.neteinstein.goodcompany.feature.settings.resources.cal_row_web
import com.neteinstein.goodcompany.feature.settings.resources.cal_title
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
import com.neteinstein.goodcompany.feature.settings.resources.meetups
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
import com.neteinstein.goodcompany.feature.settings.resources.msg_update_failed
import com.neteinstein.goodcompany.feature.settings.resources.msg_update_permission
import com.neteinstein.goodcompany.feature.settings.resources.msg_vcf_empty
import com.neteinstein.goodcompany.feature.settings.resources.msg_vcf_result
import com.neteinstein.goodcompany.feature.settings.resources.pings_at
import com.neteinstein.goodcompany.feature.settings.resources.places
import com.neteinstein.goodcompany.feature.settings.resources.places_row_hint
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
import com.neteinstein.goodcompany.feature.settings.resources.tags_row
import com.neteinstein.goodcompany.feature.settings.resources.tags_row_hint
import com.neteinstein.goodcompany.feature.settings.resources.theme_auto
import com.neteinstein.goodcompany.feature.settings.resources.theme_dark
import com.neteinstein.goodcompany.feature.settings.resources.theme_light
import com.neteinstein.goodcompany.feature.settings.resources.title
import com.neteinstein.goodcompany.feature.settings.resources.update_available
import com.neteinstein.goodcompany.feature.settings.resources.update_available_hint
import com.neteinstein.goodcompany.feature.settings.resources.update_button
import com.neteinstein.goodcompany.feature.settings.resources.update_check_button
import com.neteinstein.goodcompany.feature.settings.resources.update_check_failed
import com.neteinstein.goodcompany.feature.settings.resources.update_checking
import com.neteinstein.goodcompany.feature.settings.resources.update_downloading
import com.neteinstein.goodcompany.feature.settings.resources.update_title
import com.neteinstein.goodcompany.feature.settings.resources.update_up_to_date
import com.neteinstein.goodcompany.feature.settings.resources.web_notifications
import com.neteinstein.goodcompany.feature.settings.resources.weekly_plans
import com.neteinstein.goodcompany.feature.settings.resources.weekly_plans_hint
import com.neteinstein.goodcompany.feature.settings.resources.your_data
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    SettingsMessage.UpdateNeedsPermission -> getString(Res.string.msg_update_permission)
    SettingsMessage.UpdateFailed -> getString(Res.string.msg_update_failed)
}

@Composable
fun SettingsScreen(
    appVersion: String,
    onOpenPlaces: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenTags: () -> Unit = {},
    onOpenActivities: () -> Unit = {},
    /** Settings is opened from a tab header (issue #65), so it has a back arrow; null hides it. */
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var notificationsAsked by remember { mutableIntStateOf(0) }
    val requestNotifications = rememberPermissionRequester(AppPermission.NOTIFICATIONS) { granted ->
        notificationsAsked++
        viewModel.onNotificationPermissionResult(granted)
    }
    val notificationsAllowed = rememberNotificationsAllowed(refreshKey = notificationsAsked)
    val pickVcf = rememberTextFilePicker(FileKind.VCARD, onPicked = viewModel::onContactsFilePicked)
    val pickBackup = rememberTextFilePicker(FileKind.JSON, onPicked = viewModel::onBackupPicked)
    val capabilities = currentPlatformCapabilities()
    val saveFile = rememberFileSaver("application/json", onDone = viewModel::onExportHandled)
    val authenticator = rememberDeviceAuthenticator()
    val authorizeDrive = rememberGoogleDriveAuthorizer(viewModel::onDriveAuthResult)
    val lockReason = stringResource(CoreRes.string.lock_reason)
    val updater = rememberAppUpdater()
    val scope = rememberCoroutineScope()
    // The sideload build looks for a newer build whenever Settings opens.
    LaunchedEffect(capabilities.hasSelfUpdate) {
        if (capabilities.hasSelfUpdate) viewModel.onCheckForUpdate(updater.installedVersionCode)
    }
    LaunchedEffect(state.export) { state.export?.let { saveFile.save(it.name, it.content) } }
    SettingsContent(
        state = state,
        capabilities = capabilities,
        canLock = authenticator.isAvailable || state.settings.appLock,
        notificationsAllowed = notificationsAllowed,
        appVersion = appVersion,
        actions = SettingsActions(
            onSaveApiKey = viewModel::onSaveApiKey,
            onClearApiKey = viewModel::onClearApiKey,
            onAiEnabledChange = viewModel::onAiEnabledChange,
            onOpenActivities = onOpenActivities,
            onBack = onBack,
            onThemeChange = viewModel::onThemeChange,
            onDynamicColorChange = viewModel::onDynamicColorChange,
            onWeeklyIdeasChange = viewModel::onWeeklyIdeasChange,
            onHomeAreaChange = viewModel::onHomeAreaChange,
            onReminderHourChange = viewModel::onReminderHourChange,
            onClosenessDaysChange = viewModel::onClosenessDaysChange,
            onImportContactsFile = pickVcf,
            onOpenPlaces = onOpenPlaces,
            onOpenTags = onOpenTags,
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
            onCheckForUpdate = { viewModel.onCheckForUpdate(updater.installedVersionCode) },
            onOpenCalendar = onOpenCalendar,
            onInstallUpdate = { update ->
                viewModel.onUpdateStarted(update)
                scope.launch {
                    val result = try {
                        updater.downloadAndInstall(update, viewModel::onUpdateProgress)
                    } catch (e: CancellationException) {
                        // Left Settings mid-download: the next visit checks again.
                        viewModel.onUpdateResult(UpdateInstallResult.HANDED_OVER)
                        throw e
                    }
                    viewModel.onUpdateResult(result)
                }
            },
        ),
    )
}

data class SettingsActions(
    val onBack: (() -> Unit)? = null,
    val onSaveApiKey: (String) -> Unit = {},
    val onClearApiKey: () -> Unit = {},
    val onAiEnabledChange: (Boolean) -> Unit = {},
    val onOpenActivities: () -> Unit = {},
    val onThemeChange: (ThemeMode) -> Unit = {},
    val onDynamicColorChange: (Boolean) -> Unit = {},
    val onWeeklyIdeasChange: (Boolean) -> Unit = {},
    val onHomeAreaChange: (String) -> Unit = {},
    val onReminderHourChange: (Int) -> Unit = {},
    /** A level's default rhythm; null turns it off (only Acquaintances can be off, issue #69). */
    val onClosenessDaysChange: (Closeness, Int?) -> Unit = { _, _ -> },
    val onImportContactsFile: () -> Unit = {},
    val onOpenTags: () -> Unit = {},
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
    val onCheckForUpdate: () -> Unit = {},
    val onInstallUpdate: (AppUpdate) -> Unit = {},
    /** Settings → Places (F11). */
    val onOpenPlaces: () -> Unit = {},
    val onOpenCalendar: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    capabilities: PlatformCapabilities,
    appVersion: String,
    actions: SettingsActions,
    canLock: Boolean = false,
    /** Null while unknown; the "Allow notifications" button only shows once it's known to be false. */
    notificationsAllowed: Boolean? = false,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showMessage(it.text())
            actions.onMessageShown()
        }
    }
    val s = state.settings
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ScreenHeader(
                eyebrow = stringResource(Res.string.eyebrow),
                title = stringResource(Res.string.title),
                navigation = actions.onBack?.let { back -> { HeaderBackButton(back) } },
            )

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
                    Closeness.INNER to s.innerCircleDays,
                    Closeness.CLOSE to s.closeCircleDays,
                    Closeness.GOOD to s.goodFriendsDays,
                    Closeness.ACQUAINTANCE to s.acquaintanceDays,
                ).forEach { (closeness, days) ->
                    Text(
                        closenessLabel(closeness),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Acquaintances have no rhythm by default (issue #69); the real-friend levels always have one.
                        if (!closeness.isRealFriend) {
                            FilterChip(
                                days == null,
                                { actions.onClosenessDaysChange(closeness, null) },
                                label = { Text(stringResource(CoreRes.string.cadence_off)) },
                            )
                        }
                        (CIRCLE_OPTIONS + listOfNotNull(days)).distinct().sorted().forEach { d ->
                            FilterChip(days == d, { actions.onClosenessDaysChange(closeness, d) }, label = { Text(everyNDays(d)) })
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
                SwitchRow(stringResource(Res.string.ai_switch), stringResource(Res.string.ai_switch_hint), s.aiEnabled, actions.onAiEnabledChange)
                if (!s.aiEnabled) {
                    Text(
                        stringResource(Res.string.ai_off_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                // The key, the area and the weekly plans only show while AI is on (F1).
                if (s.aiEnabled) AiDetails(state, actions)
            }

            SectionCard(stringResource(Res.string.meetups), icon = Icons.Rounded.FavoriteBorder) {
                ListItem(
                    modifier = Modifier.clickable(onClick = actions.onOpenActivities),
                    headlineContent = { Text(stringResource(Res.string.activities), style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(Res.string.activities_count, s.activities.size, HangoutActivity.entries.size)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            SectionCard(stringResource(Res.string.reminders), icon = Icons.Rounded.Notifications) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (capabilities.hasBackgroundNotifications) {
                        if (notificationsAllowed == false) {
                            FilledTonalButton(onClick = actions.onEnableNotifications) {
                                Icon(Icons.Rounded.Notifications, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(Res.string.allow_notifications))
                            }
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
                // Calendar suggestions (D3): Android and iOS only; the web says so here.
                ListItem(
                    modifier = if (capabilities.hasCalendar) Modifier.clickable(onClick = actions.onOpenCalendar) else Modifier,
                    leadingContent = { Icon(Icons.Rounded.CalendarMonth, null) },
                    headlineContent = { Text(stringResource(Res.string.cal_title), style = MaterialTheme.typography.titleSmall) },
                    supportingContent = {
                        Text(
                            stringResource(
                                when {
                                    !capabilities.hasCalendar -> Res.string.cal_row_web
                                    s.calendarSuggestions -> Res.string.cal_row_on
                                    else -> Res.string.cal_row_off
                                },
                            ),
                        )
                    },
                    trailingContent = if (capabilities.hasCalendar) {
                        { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
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
                    modifier = Modifier.clickable(onClick = actions.onOpenPlaces),
                    leadingContent = { Icon(Icons.Rounded.Place, null) },
                    headlineContent = { Text(stringResource(Res.string.places), style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(Res.string.places_row_hint)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ListItem(
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                    headlineContent = { Text(stringResource(Res.string.tags_row), style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(Res.string.tags_row_hint)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = actions.onOpenTags),
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

            if (capabilities.hasSelfUpdate) {
                SectionCard(stringResource(Res.string.update_title), icon = Icons.Rounded.SystemUpdate) {
                    UpdateRow(state.update, actions.onCheckForUpdate, actions.onInstallUpdate)
                }
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

/** Sideload build only: what the update check found, with the button that fits. */
@Composable
private fun UpdateRow(state: UpdateState, onCheck: () -> Unit, onInstall: (AppUpdate) -> Unit) {
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    ListItem(
        headlineContent = {
            Text(
                when (state) {
                    UpdateState.Idle, UpdateState.Checking -> stringResource(Res.string.update_checking)
                    UpdateState.UpToDate -> stringResource(Res.string.update_up_to_date)
                    UpdateState.CheckFailed -> stringResource(Res.string.update_check_failed)
                    is UpdateState.Available -> stringResource(Res.string.update_available, state.update.versionName)
                    is UpdateState.Downloading -> stringResource(Res.string.update_downloading)
                },
                style = MaterialTheme.typography.titleSmall,
            )
        },
        supportingContent = {
            when (state) {
                is UpdateState.Available -> Text(stringResource(Res.string.update_available_hint))
                is UpdateState.Downloading ->
                    state.progress
                        ?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(top = 8.dp)) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                else -> Unit
            }
        },
        trailingContent = {
            when (state) {
                is UpdateState.Available -> Button(onClick = { onInstall(state.update) }) { Text(stringResource(Res.string.update_button)) }
                UpdateState.Idle, UpdateState.UpToDate, UpdateState.CheckFailed ->
                    OutlinedButton(onClick = onCheck, enabled = !busy) { Text(stringResource(Res.string.update_check_button)) }
                UpdateState.Checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                is UpdateState.Downloading -> Unit
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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

/** What the AI card shows while AI is on: the key, the home area and the weekly plans. */
@Composable
private fun AiDetails(state: SettingsUiState, actions: SettingsActions) {
    val s = state.settings
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
