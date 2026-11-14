package com.neteinstein.goodcompany.feature.settings.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.usecase.BackupToDriveUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.ExportBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FetchDriveBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMyPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveMyPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.SetMyPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.platform.DriveAuthResult
import com.neteinstein.goodcompany.core.ui.platform.UpdateInstallResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface SettingsMessage {
    data object KeySaved : SettingsMessage

    data object KeyRemoved : SettingsMessage

    data class Notifications(val granted: Boolean) : SettingsMessage

    data object SampleAdded : SettingsMessage

    data object SampleRemoved : SettingsMessage

    data class FileImported(val result: ContactsFileImportResult) : SettingsMessage

    data object BackupSaved : SettingsMessage

    data class BackupRestored(val friends: Int) : SettingsMessage

    data object BackupInvalid : SettingsMessage

    data object LockFailed : SettingsMessage

    data object DriveBackedUp : SettingsMessage

    data object DriveNoBackup : SettingsMessage

    data object DriveNotConfigured : SettingsMessage

    data class DriveFailed(val reason: CloudBackupFailure.Reason) : SettingsMessage

    /** Android sent the user to the "install unknown apps" switch for this app. */
    data object UpdateNeedsPermission : SettingsMessage

    data object UpdateFailed : SettingsMessage

    /** The picked picture couldn't be read; your photo stays as it was (issue #71). */
    data object MyPhotoUnreadable : SettingsMessage

    /** Your photo was removed; the snackbar offers Undo. */
    data object MyPhotoRemoved : SettingsMessage
}

/** Where the sideload build's "App updates" row stands. */
sealed interface UpdateState {
    /** Not checked yet (or this build doesn't update itself). */
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    /** The check itself failed (offline, GitHub unreachable). */
    data object CheckFailed : UpdateState

    data class Available(val update: AppUpdate) : UpdateState

    /** [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(val update: AppUpdate, val progress: Float? = null) : UpdateState
}

/** What to do once Google hands back an access token. */
enum class DriveAction { BACKUP, RESTORE }

/** A backup ready to be handed to the platform "save file" UI. */
data class ExportFile(val name: String, val content: String)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Your own photo (issue #71), the first card; null while none is set. */
    val myPhoto: ContactPhoto? = null,
    /** Masked form of the saved key, e.g. "sk-ant-…9f3a". */
    val maskedKey: String? = null,
    val hasSampleData: Boolean = false,
    val message: SettingsMessage? = null,
    val export: ExportFile? = null,
    /** A picked backup waiting for the "Replace everything?" confirmation. */
    val pendingRestore: String? = null,
    /** When [pendingRestore] came from Google Drive: the backup's date, shown in the confirmation. */
    val pendingRestoreFrom: Instant? = null,
    /** Waiting for Google, or uploading/downloading. */
    val driveBusy: Boolean = false,
    val driveAction: DriveAction? = null,
    val update: UpdateState = UpdateState.Idle,
    val now: Instant = Instant.DISTANT_PAST,
    val zone: TimeZone = TimeZone.UTC,
)

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    hasSampleData: HasSampleDataUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
    private val removeSampleData: RemoveSampleDataUseCase,
    private val importContactsFile: ImportContactsFileUseCase,
    private val exportBackup: ExportBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val backupToDrive: BackupToDriveUseCase,
    private val fetchDriveBackup: FetchDriveBackupUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    observeMyPhoto: ObserveMyPhotoUseCase,
    private val setMyPhoto: SetMyPhotoUseCase,
    private val removeMyPhoto: RemoveMyPhotoUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState())

    /** The photo just removed, for the snackbar's Undo. */
    private var removedPhoto: ContactPhoto? = null

    val state: StateFlow<SettingsUiState> = combine(observeSettings(), hasSampleData(), observeMyPhoto(), local) { settings, sample, photo, ui ->
        ui.copy(
            settings = settings,
            myPhoto = photo,
            maskedKey = settings.aiApiKey?.takeIf { it.isNotBlank() }?.let(::mask),
            hasSampleData = sample,
            now = clock.now(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { updateSettings(transform) }
    }

    // --- Your photo (issue #71) --------------------------------------------------------------------------------------

    /** A picked and cropped picture; replaces the photo you had. One that can't be read changes nothing. */
    fun onMyPhotoPicked(image: ByteArray?) {
        viewModelScope.launch {
            if (!setMyPhoto(image)) local.update { it.copy(message = SettingsMessage.MyPhotoUnreadable) }
        }
    }

    fun onRemoveMyPhoto() {
        val photo = state.value.myPhoto ?: return
        removedPhoto = photo
        viewModelScope.launch {
            removeMyPhoto()
            local.update { it.copy(message = SettingsMessage.MyPhotoRemoved) }
        }
    }

    /** Puts back the photo [onRemoveMyPhoto] took away. */
    fun onUndoRemoveMyPhoto() {
        val photo = removedPhoto ?: return
        removedPhoto = null
        viewModelScope.launch { setMyPhoto(photo.bytes()) }
    }

    fun onSaveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        update { it.copy(aiApiKey = trimmed) }
        local.update { it.copy(message = SettingsMessage.KeySaved) }
    }

    fun onClearApiKey() {
        update { it.copy(aiApiKey = null) }
        local.update { it.copy(message = SettingsMessage.KeyRemoved) }
    }

    /** The AI switch (F1). Off hides every AI entry point; the saved key stays, so turning it back on needs nothing more. */
    fun onAiEnabledChange(enabled: Boolean) = update { it.copy(aiEnabled = enabled) }

    fun onThemeChange(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun onDynamicColorChange(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun onWeeklyIdeasChange(enabled: Boolean) = update { it.copy(weeklyIdeasEnabled = enabled) }

    fun onHomeAreaChange(area: String) = update { it.copy(homeArea = area) }

    /** Default "meet every N days" for a closeness level; null (off) only applies to Acquaintances (issue #69). */
    fun onClosenessDaysChange(closeness: Closeness, days: Int?) = update {
        val d = days?.coerceIn(1, 365)
        when (closeness) {
            Closeness.INNER -> d?.let { v -> it.copy(innerCircleDays = v) } ?: it
            Closeness.CLOSE -> d?.let { v -> it.copy(closeCircleDays = v) } ?: it
            Closeness.GOOD -> d?.let { v -> it.copy(goodFriendsDays = v) } ?: it
            Closeness.ACQUAINTANCE -> it.copy(acquaintanceDays = d)
        }
    }

    fun onContactsFilePicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch {
            val result = importContactsFile(text)
            local.update { it.copy(message = SettingsMessage.FileImported(result)) }
        }
    }

    fun onReminderHourChange(hour: Int) = update { it.copy(reminderHour = hour.coerceIn(0, 23)) }

    fun onNotificationPermissionResult(granted: Boolean) = local.update {
        it.copy(message = SettingsMessage.Notifications(granted))
    }

    fun onLoadSampleData() {
        viewModelScope.launch {
            loadSampleData()
            local.update { it.copy(message = SettingsMessage.SampleAdded) }
        }
    }

    fun onRemoveSampleData() {
        viewModelScope.launch {
            removeSampleData()
            local.update { it.copy(message = SettingsMessage.SampleRemoved) }
        }
    }

    /** Shows the intro again on next launch of the main screen. */
    fun onReplayIntro() = update { it.copy(onboardingDone = false) }

    // --- Nudges & privacy ------------------------------------------------------------------------------------------

    fun onWeeklyDigestChange(enabled: Boolean) = update { it.copy(weeklyDigest = enabled) }

    /** ISO day of week, 1 = Monday … 7 = Sunday. */
    fun onDigestDayChange(day: Int) = update { it.copy(digestDay = day.coerceIn(1, 7)) }

    fun onPrivateNotificationsChange(enabled: Boolean) = update { it.copy(privateNotifications = enabled) }

    fun onShowWarmthChange(enabled: Boolean) = update { it.copy(showWarmth = enabled) }

    /** Called after the device lock confirmed (or refused) the user; turning the lock off needs no check. */
    fun onAppLockChange(enabled: Boolean, verified: Boolean) {
        if (enabled && !verified) {
            local.update { it.copy(message = SettingsMessage.LockFailed) }
            return
        }
        update { it.copy(appLock = enabled) }
    }

    // --- Backup --------------------------------------------------------------------------------------------------

    fun onExportBackup() {
        viewModelScope.launch {
            val content = exportBackup()
            local.update { it.copy(export = ExportFile(exportBackup.fileName(), content)) }
        }
    }

    /** The platform save UI took the file (or the user cancelled). */
    fun onExportHandled(saved: Boolean) = local.update { it.copy(export = null, message = if (saved) SettingsMessage.BackupSaved else it.message) }

    fun onBackupPicked(text: String?) {
        if (text.isNullOrBlank()) return
        local.update { it.copy(pendingRestore = text) }
    }

    fun onCancelRestore() = local.update { it.copy(pendingRestore = null, pendingRestoreFrom = null) }

    // --- Google Drive --------------------------------------------------------------------------------------------

    /** Called right before the screen asks Google for a token. */
    fun onDriveRequested(action: DriveAction) = local.update { it.copy(driveAction = action, driveBusy = true) }

    fun onDriveAuthResult(result: DriveAuthResult) {
        val action = local.value.driveAction
        when (result) {
            is DriveAuthResult.Token -> when (action) {
                DriveAction.BACKUP -> viewModelScope.launch {
                    val outcome = backupToDrive(result.accessToken)
                    local.update {
                        it.copy(driveBusy = false, driveAction = null, message = outcome.fold({ SettingsMessage.DriveBackedUp }, ::driveFailure))
                    }
                }
                DriveAction.RESTORE -> viewModelScope.launch {
                    val outcome = fetchDriveBackup(result.accessToken)
                    local.update { s ->
                        outcome.fold(
                            { backup ->
                                if (backup == null) {
                                    s.copy(driveBusy = false, driveAction = null, message = SettingsMessage.DriveNoBackup)
                                } else {
                                    s.copy(
                                        driveBusy = false,
                                        driveAction = null,
                                        pendingRestore = backup.content,
                                        pendingRestoreFrom = backup.info.modifiedAt,
                                    )
                                }
                            },
                            { s.copy(driveBusy = false, driveAction = null, message = driveFailure(it)) },
                        )
                    }
                }
                null -> local.update { it.copy(driveBusy = false) }
            }
            DriveAuthResult.Cancelled -> local.update { it.copy(driveBusy = false, driveAction = null) }
            DriveAuthResult.NotConfigured -> local.update {
                it.copy(driveBusy = false, driveAction = null, message = SettingsMessage.DriveNotConfigured)
            }
            is DriveAuthResult.Failed -> local.update {
                it.copy(driveBusy = false, driveAction = null, message = SettingsMessage.DriveFailed(CloudBackupFailure.Reason.OTHER))
            }
        }
    }

    private fun driveFailure(e: Throwable): SettingsMessage =
        SettingsMessage.DriveFailed((e as? CloudBackupFailure)?.reason ?: CloudBackupFailure.Reason.OTHER)

    fun onConfirmRestore() {
        val raw = local.value.pendingRestore ?: return
        viewModelScope.launch {
            val result = restoreBackup(raw)
            local.update {
                it.copy(
                    pendingRestore = null,
                    pendingRestoreFrom = null,
                    message = when (result) {
                        is RestoreResult.Restored -> SettingsMessage.BackupRestored(result.friends)
                        RestoreResult.NotABackup -> SettingsMessage.BackupInvalid
                    },
                )
            }
        }
    }

    // --- App updates (sideload build) ----------------------------------------------------------------------------

    /** Asks whether a build newer than [installedVersionCode] is published. Ignored while one is being checked or downloaded. */
    fun onCheckForUpdate(installedVersionCode: Long) {
        if (local.value.update is UpdateState.Checking || local.value.update is UpdateState.Downloading) return
        local.update { it.copy(update = UpdateState.Checking) }
        viewModelScope.launch {
            val outcome = checkForUpdate(installedVersionCode)
            local.update { s ->
                // The user may have started the download meanwhile; don't clobber that.
                if (s.update !is UpdateState.Checking) {
                    s
                } else {
                    s.copy(
                        update = outcome.fold(
                            { found -> found?.let(UpdateState::Available) ?: UpdateState.UpToDate },
                            { UpdateState.CheckFailed },
                        ),
                    )
                }
            }
        }
    }

    /** The screen is about to download [update] and open the installer. */
    fun onUpdateStarted(update: AppUpdate) = local.update { it.copy(update = UpdateState.Downloading(update)) }

    fun onUpdateProgress(progress: Float) = local.update { s ->
        val current = s.update as? UpdateState.Downloading ?: return@update s
        s.copy(update = current.copy(progress = progress.coerceIn(0f, 1f)))
    }

    /** Download and hand-over finished; the installer (or the permission switch) is on screen now. */
    fun onUpdateResult(result: UpdateInstallResult) = local.update { s ->
        val current = s.update as? UpdateState.Downloading ?: return@update s
        s.copy(
            update = UpdateState.Available(current.update),
            message = when (result) {
                UpdateInstallResult.HANDED_OVER -> s.message
                UpdateInstallResult.NEEDS_INSTALL_PERMISSION -> SettingsMessage.UpdateNeedsPermission
                UpdateInstallResult.FAILED -> SettingsMessage.UpdateFailed
            },
        )
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    companion object {
        fun mask(key: String): String = if (key.length <= 10) "••••" else key.take(7) + "…" + key.takeLast(4)
    }
}
