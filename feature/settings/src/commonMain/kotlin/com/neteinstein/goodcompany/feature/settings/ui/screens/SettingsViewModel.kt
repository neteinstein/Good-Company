package com.neteinstein.goodcompany.feature.settings.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.model.AutoBackupFrequency
import com.neteinstein.goodcompany.core.domain.model.BackupKey
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupInfo
import com.neteinstein.goodcompany.core.domain.usecase.AdoptBackupKeyUseCase
import com.neteinstein.goodcompany.core.domain.usecase.BackupToDriveUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ChangeBackupPasswordResult
import com.neteinstein.goodcompany.core.domain.usecase.ChangeBackupPasswordUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.EncryptBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FetchDriveBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ForgetDriveTokenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepOldDriveBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveBackupProtectionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMyPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.OpenBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.OpenedBackup
import com.neteinstein.goodcompany.core.domain.usecase.RemoveMyPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.SetAutoBackupFrequencyUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetBackupPasswordResult
import com.neteinstein.goodcompany.core.domain.usecase.SetBackupPasswordUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetMyPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.TrashOldDriveBackupUseCase
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

    data class DriveFailed(val reason: CloudBackupFailure.Reason) : SettingsMessage

    /** Issue #166: Google's sign-in itself failed; [detail] is Google's reason, shown so it can be looked into. */
    data class DriveSignInFailed(val detail: String?) : SettingsMessage

    /** D4: the backup password was changed. */
    data object BackupPasswordChanged : SettingsMessage

    /** D5: the old plain backup file went to the Drive bin. */
    data object OldDriveFileTrashed : SettingsMessage

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

/** Google Drive isn't set up for this build; [app] is what to register in Google Cloud, when known. */
data class DriveSetup(val app: String?)

/** A backup ready to be handed to the platform "save file" UI. */
data class ExportFile(val name: String, val content: String)

/** D4: the backup-password dialog on screen. */
enum class PasswordDialog { SET, CHANGE }

/** Why the backup-password dialog didn't accept what was typed. */
enum class PasswordError { TOO_SHORT, MISMATCH, WRONG_SECRET }

/** What to do once a password is set: the backup the user asked for before there was one. */
enum class AfterPassword { EXPORT, DRIVE_BACKUP }

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Your own photo (issue #71), the first card; null while none is set. */
    val myPhoto: ContactPhoto? = null,
    /** Masked form of the saved key, e.g. "sk-ant-…9f3a". */
    val maskedKey: String? = null,
    val hasSampleData: Boolean = false,
    val message: SettingsMessage? = null,
    val export: ExportFile? = null,
    /** A picked (and opened) backup document waiting for the "Replace everything?" confirmation. */
    val pendingRestore: String? = null,
    /** When [pendingRestore] came from Google Drive: the backup's date, shown in the confirmation. */
    val pendingRestoreFrom: Instant? = null,
    /** D4: an encrypted backup waiting for its password or recovery key; [pendingRestoreFrom] applies to it too. */
    val lockedRestore: String? = null,
    /** The password or recovery key given didn't open [lockedRestore]. */
    val unlockFailed: Boolean = false,
    /** D4: a backup password is set on this device. */
    val hasBackupPassword: Boolean = false,
    val passwordDialog: PasswordDialog? = null,
    val passwordError: PasswordError? = null,
    /** Deriving a key (PBKDF2) takes a moment: the dialogs show progress. */
    val cryptoBusy: Boolean = false,
    /** D4: the recovery key, shown once right after the password is set. */
    val recoveryKey: String? = null,
    /** D5: the old plain backup file the app offers to delete after an encrypted Drive backup. */
    val oldDriveFile: CloudBackupInfo? = null,
    /** Waiting for Google, or uploading/downloading. */
    val driveBusy: Boolean = false,
    /**
     * Issue #166: Google said this build has no OAuth client. Shown in a dialog (not a snackbar) with [DriveSetup.app],
     * the package and signing certificate to register, so it can be copied.
     */
    val driveSetup: DriveSetup? = null,
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
    private val encryptBackup: EncryptBackupUseCase,
    private val openBackup: OpenBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val adoptBackupKey: AdoptBackupKeyUseCase,
    observeBackupProtection: ObserveBackupProtectionUseCase,
    private val setBackupPassword: SetBackupPasswordUseCase,
    private val changeBackupPassword: ChangeBackupPasswordUseCase,
    private val backupToDrive: BackupToDriveUseCase,
    private val fetchDriveBackup: FetchDriveBackupUseCase,
    private val trashOldDriveBackup: TrashOldDriveBackupUseCase,
    private val keepOldDriveBackup: KeepOldDriveBackupUseCase,
    private val setAutoBackupFrequency: SetAutoBackupFrequencyUseCase,
    private val forgetDriveToken: ForgetDriveTokenUseCase,
    private val checkForUpdate: CheckForUpdateUseCase,
    observeMyPhoto: ObserveMyPhotoUseCase,
    private val setMyPhoto: SetMyPhotoUseCase,
    private val removeMyPhoto: RemoveMyPhotoUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState())

    /** The photo just removed, for the snackbar's Undo. */
    private var removedPhoto: ContactPhoto? = null

    /** The backup the user asked for before a password was set (D4). */
    private var afterPassword: AfterPassword? = null

    /** The key of the encrypted backup waiting in [SettingsUiState.pendingRestore], kept by a device without one. */
    private var pendingRestoreKey: BackupKey? = null

    /** The Drive token of the backup that found the old file, to move it to the bin if the user agrees (D5). */
    private var oldFileToken: String? = null

    val state: StateFlow<SettingsUiState> = combine(
        observeSettings(),
        hasSampleData(),
        observeMyPhoto(),
        observeBackupProtection(),
        local,
    ) { settings, sample, photo, protection, ui ->
        ui.copy(
            settings = settings,
            myPhoto = photo,
            hasBackupPassword = protection.hasPassword,
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

    fun onShowWarmthChange(enabled: Boolean) = update { it.copy(showWarmth = enabled) }

    /** Called after the device lock confirmed (or refused) the user; turning the lock off needs no check. */
    fun onAppLockChange(enabled: Boolean, verified: Boolean) {
        if (enabled && !verified) {
            local.update { it.copy(message = SettingsMessage.LockFailed) }
            return
        }
        update { it.copy(appLock = enabled) }
    }

    // --- Backup password (D4) ------------------------------------------------------------------------------------

    fun onSetBackupPassword() = local.update { it.copy(passwordDialog = PasswordDialog.SET, passwordError = null) }

    fun onChangeBackupPassword() = local.update { it.copy(passwordDialog = PasswordDialog.CHANGE, passwordError = null) }

    fun onDismissPasswordDialog() {
        if (local.value.cryptoBusy) return
        afterPassword = null
        local.update { it.copy(passwordDialog = null, passwordError = null) }
    }

    /** "Save" in the set-password dialog. */
    fun onSavePassword(password: String, confirm: String) {
        if (local.value.cryptoBusy) return
        if (password != confirm) return local.update { it.copy(passwordError = PasswordError.MISMATCH) }
        local.update { it.copy(cryptoBusy = true, passwordError = null) }
        viewModelScope.launch {
            val result = setBackupPassword(password)
            local.update {
                when (result) {
                    is SetBackupPasswordResult.Done -> it.copy(cryptoBusy = false, passwordDialog = null, recoveryKey = result.recoveryKey)
                    SetBackupPasswordResult.TooShort -> it.copy(cryptoBusy = false, passwordError = PasswordError.TOO_SHORT)
                    // Set meanwhile (another screen): nothing to do but close.
                    SetBackupPasswordResult.AlreadySet -> it.copy(cryptoBusy = false, passwordDialog = null)
                }
            }
        }
    }

    /** "Save" in the change-password dialog: [current] is the password or the recovery key. */
    fun onSaveChangedPassword(current: String, password: String, confirm: String) {
        if (local.value.cryptoBusy) return
        if (password != confirm) return local.update { it.copy(passwordError = PasswordError.MISMATCH) }
        local.update { it.copy(cryptoBusy = true, passwordError = null) }
        viewModelScope.launch {
            val result = changeBackupPassword(current, password)
            local.update {
                when (result) {
                    ChangeBackupPasswordResult.CHANGED ->
                        it.copy(cryptoBusy = false, passwordDialog = null, message = SettingsMessage.BackupPasswordChanged)
                    ChangeBackupPasswordResult.TOO_SHORT -> it.copy(cryptoBusy = false, passwordError = PasswordError.TOO_SHORT)
                    ChangeBackupPasswordResult.WRONG_SECRET -> it.copy(cryptoBusy = false, passwordError = PasswordError.WRONG_SECRET)
                    ChangeBackupPasswordResult.NO_PASSWORD -> it.copy(cryptoBusy = false, passwordDialog = PasswordDialog.SET)
                }
            }
        }
    }

    /**
     * "I've saved it" under the recovery key. Carries on with the backup the user asked for before the password was
     * set: an export right away; a Drive backup is returned for the screen to start, since Google's sign-in has to
     * open on the tap itself.
     */
    fun onRecoveryKeySaved(): AfterPassword? {
        val next = afterPassword
        afterPassword = null
        local.update { it.copy(recoveryKey = null) }
        if (next == AfterPassword.EXPORT) onExportBackup()
        return next.takeIf { it == AfterPassword.DRIVE_BACKUP }
    }

    // --- Backup --------------------------------------------------------------------------------------------------

    /** Encrypted with this device's key (D4); without a password, asks for one first. */
    fun onExportBackup() {
        viewModelScope.launch {
            val content = encryptBackup()
            if (content == null) {
                afterPassword = AfterPassword.EXPORT
                local.update { it.copy(passwordDialog = PasswordDialog.SET, passwordError = null) }
            } else {
                local.update { it.copy(export = ExportFile(encryptBackup.fileName(), content)) }
            }
        }
    }

    /** The platform save UI took the file (or the user cancelled). */
    fun onExportHandled(saved: Boolean) = local.update { it.copy(export = null, message = if (saved) SettingsMessage.BackupSaved else it.message) }

    fun onBackupPicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch { offerRestore(text, from = null) }
    }

    /** A backup file from a picker or from Drive: plain ones go to the confirmation, encrypted ones ask for the password. */
    private suspend fun offerRestore(raw: String, from: Instant?) {
        when (val opened = openBackup(raw)) {
            is OpenedBackup.Plain -> local.update { it.copy(pendingRestore = opened.json, pendingRestoreFrom = from) }
            else -> local.update { it.copy(lockedRestore = raw, pendingRestoreFrom = from, unlockFailed = false) }
        }
    }

    /** The password or recovery key typed for [SettingsUiState.lockedRestore]. */
    fun onUnlockBackup(secret: String) {
        val raw = local.value.lockedRestore ?: return
        if (local.value.cryptoBusy) return
        local.update { it.copy(cryptoBusy = true, unlockFailed = false) }
        viewModelScope.launch {
            val opened = openBackup(raw, secret)
            local.update {
                when (opened) {
                    is OpenedBackup.Opened -> {
                        pendingRestoreKey = opened.key
                        it.copy(cryptoBusy = false, lockedRestore = null, pendingRestore = opened.json)
                    }
                    is OpenedBackup.Plain -> it.copy(cryptoBusy = false, lockedRestore = null, pendingRestore = opened.json)
                    OpenedBackup.NeedsSecret, OpenedBackup.WrongSecret -> it.copy(cryptoBusy = false, unlockFailed = true)
                }
            }
        }
    }

    fun onCancelRestore() {
        if (local.value.cryptoBusy) return
        pendingRestoreKey = null
        local.update { it.copy(pendingRestore = null, pendingRestoreFrom = null, lockedRestore = null, unlockFailed = false) }
    }

    // --- Google Drive --------------------------------------------------------------------------------------------

    /**
     * "Back up" to Drive was tapped. True when the screen should ask Google for a token now; false when a password has
     * to be set first (D4: nothing is uploaded in the clear), in which case the set-password dialog opens.
     */
    fun onDriveBackupTapped(): Boolean {
        if (!state.value.hasBackupPassword) {
            afterPassword = AfterPassword.DRIVE_BACKUP
            local.update { it.copy(passwordDialog = PasswordDialog.SET, passwordError = null) }
            return false
        }
        onDriveRequested(DriveAction.BACKUP)
        return true
    }

    /** Called right before the screen asks Google for a token. */
    fun onDriveRequested(action: DriveAction) = local.update { it.copy(driveAction = action, driveBusy = true) }

    fun onDriveAuthResult(result: DriveAuthResult) {
        val action = local.value.driveAction
        when (result) {
            is DriveAuthResult.Token -> when (action) {
                DriveAction.BACKUP -> viewModelScope.launch {
                    val outcome = backupToDrive(result.accessToken)
                    forgetIfRefused(outcome, result.accessToken)
                    val old = outcome.getOrNull()?.oldVisibleFile
                    oldFileToken = old?.let { result.accessToken }
                    local.update {
                        it.copy(
                            driveBusy = false,
                            driveAction = null,
                            oldDriveFile = old,
                            message = outcome.fold({ SettingsMessage.DriveBackedUp }, ::driveFailure),
                        )
                    }
                }
                DriveAction.RESTORE -> viewModelScope.launch {
                    val outcome = fetchDriveBackup(result.accessToken)
                    forgetIfRefused(outcome, result.accessToken)
                    val backup = outcome.getOrNull()
                    local.update {
                        it.copy(
                            driveBusy = false,
                            driveAction = null,
                            message = outcome.fold({ found -> if (found == null) SettingsMessage.DriveNoBackup else null }, ::driveFailure),
                        )
                    }
                    if (backup != null) offerRestore(backup.content, backup.info.modifiedAt)
                }
                null -> local.update { it.copy(driveBusy = false) }
            }
            DriveAuthResult.Cancelled -> local.update { it.copy(driveBusy = false, driveAction = null) }
            is DriveAuthResult.NotConfigured -> local.update {
                it.copy(driveBusy = false, driveAction = null, driveSetup = DriveSetup(result.app))
            }
            is DriveAuthResult.Failed -> local.update {
                it.copy(
                    driveBusy = false,
                    driveAction = null,
                    message = if (result.network) {
                        SettingsMessage.DriveFailed(CloudBackupFailure.Reason.NETWORK)
                    } else {
                        SettingsMessage.DriveSignInFailed(result.detail)
                    },
                )
            }
        }
    }

    fun onDriveSetupShown() = local.update { it.copy(driveSetup = null) }

    /** Drive refused the token (revoked or expired): drop it, so "Try again" gets a fresh one instead of the same. */
    private suspend fun forgetIfRefused(outcome: Result<*>, token: String) {
        if ((outcome.exceptionOrNull() as? CloudBackupFailure)?.reason == CloudBackupFailure.Reason.UNAUTHORIZED) forgetDriveToken(token)
    }

    /** Issue #166: Settings → Google Drive → "Automatic backup" (Android only). */
    fun onAutoBackupFrequencyChange(frequency: AutoBackupFrequency) {
        viewModelScope.launch { setAutoBackupFrequency(frequency) }
    }

    private fun driveFailure(e: Throwable): SettingsMessage =
        SettingsMessage.DriveFailed((e as? CloudBackupFailure)?.reason ?: CloudBackupFailure.Reason.OTHER)

    /** D5: "Delete" on the old plain backup file: it goes to the Drive bin. */
    fun onTrashOldDriveFile() {
        val file = local.value.oldDriveFile ?: return
        val token = oldFileToken ?: return local.update { it.copy(oldDriveFile = null) }
        oldFileToken = null
        local.update { it.copy(oldDriveFile = null, driveBusy = true) }
        viewModelScope.launch {
            val outcome = trashOldDriveBackup(token, file.id)
            local.update { it.copy(driveBusy = false, message = outcome.fold({ SettingsMessage.OldDriveFileTrashed }, ::driveFailure)) }
        }
    }

    /** D5: "Keep it": the old file stays and the app doesn't ask again. */
    fun onKeepOldDriveFile() {
        oldFileToken = null
        local.update { it.copy(oldDriveFile = null) }
        viewModelScope.launch { keepOldDriveBackup() }
    }

    fun onConfirmRestore() {
        val raw = local.value.pendingRestore ?: return
        val key = pendingRestoreKey
        pendingRestoreKey = null
        viewModelScope.launch {
            val result = restoreBackup(raw)
            // A device without a password keeps the restored backup's key, so its next backups open the same way.
            if (result is RestoreResult.Restored && key != null) adoptBackupKey(key)
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
