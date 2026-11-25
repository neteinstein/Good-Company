package com.neteinstein.goodcompany.feature.settings.ui.screens.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.BackupKey
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.usecase.AdoptBackupKeyUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FetchDriveBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ForgetDriveTokenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.OpenBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.OpenedBackup
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.platform.DriveAuthResult
import com.neteinstein.goodcompany.feature.settings.ui.screens.DriveSetup
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

data class RestoreBackupUiState(
    /** Asking Google or downloading from Drive. */
    val driveBusy: Boolean = false,
    /** Google said this build has no OAuth client (shown with what to register). */
    val driveSetup: DriveSetup? = null,
    /** An opened backup waiting for "Replace everything?". */
    val pendingRestore: String? = null,
    /** When [pendingRestore] (or [lockedRestore]) came from Google Drive: the backup's date. */
    val pendingRestoreFrom: Instant? = null,
    /** D4: an encrypted backup waiting for its password or recovery key. */
    val lockedRestore: String? = null,
    val unlockFailed: Boolean = false,
    val cryptoBusy: Boolean = false,
    /** Restored: the app leaves the intro by itself (restoring marks onboarding done). */
    val restored: Boolean = false,
    /** Shown in a snackbar, with the same texts as Settings. */
    val message: SettingsMessage? = null,
    val now: Instant = Instant.fromEpochMilliseconds(0),
    val zone: TimeZone = TimeZone.UTC,
)

/**
 * Issue #166: "Restore a backup" from the intro guide, so a new phone is set up straight from a backup, from Google
 * Drive or from a file. The same steps as Settings → Your data: open (password or recovery key for an encrypted one),
 * confirm, restore, and keep the backup's key on a device that has none yet.
 */
class RestoreBackupViewModel(
    private val openBackup: OpenBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val adoptBackupKey: AdoptBackupKeyUseCase,
    private val fetchDriveBackup: FetchDriveBackupUseCase,
    private val forgetDriveToken: ForgetDriveTokenUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(RestoreBackupUiState(now = clock.now(), zone = clock.timeZone))
    val state: StateFlow<RestoreBackupUiState> = local.asStateFlow()

    /** The key of the encrypted backup waiting in [RestoreBackupUiState.pendingRestore]. */
    private var pendingKey: BackupKey? = null

    fun onFilePicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch { offer(text, from = null) }
    }

    /** Called right before the screen asks Google for a token. */
    fun onDriveRequested() = local.update { it.copy(driveBusy = true) }

    fun onDriveAuthResult(result: DriveAuthResult) {
        when (result) {
            is DriveAuthResult.Token -> viewModelScope.launch {
                val outcome = fetchDriveBackup(result.accessToken)
                val failure = outcome.exceptionOrNull() as? CloudBackupFailure
                if (failure?.reason == CloudBackupFailure.Reason.UNAUTHORIZED) forgetDriveToken(result.accessToken)
                val backup = outcome.getOrNull()
                local.update {
                    it.copy(
                        driveBusy = false,
                        message = when {
                            outcome.isFailure -> SettingsMessage.DriveFailed(failure?.reason ?: CloudBackupFailure.Reason.OTHER)
                            backup == null -> SettingsMessage.DriveNoBackup
                            else -> null
                        },
                    )
                }
                if (backup != null) offer(backup.content, backup.info.modifiedAt)
            }
            DriveAuthResult.Cancelled -> local.update { it.copy(driveBusy = false) }
            is DriveAuthResult.NotConfigured -> local.update { it.copy(driveBusy = false, driveSetup = DriveSetup(result.app)) }
            is DriveAuthResult.Failed -> local.update {
                it.copy(
                    driveBusy = false,
                    message = if (result.network) {
                        SettingsMessage.DriveFailed(CloudBackupFailure.Reason.NETWORK)
                    } else {
                        SettingsMessage.DriveSignInFailed(result.detail)
                    },
                )
            }
        }
    }

    private suspend fun offer(raw: String, from: Instant?) {
        when (val opened = openBackup(raw)) {
            is OpenedBackup.Plain -> local.update { it.copy(pendingRestore = opened.json, pendingRestoreFrom = from) }
            else -> local.update { it.copy(lockedRestore = raw, pendingRestoreFrom = from, unlockFailed = false) }
        }
    }

    /** The password or recovery key typed for [RestoreBackupUiState.lockedRestore]. */
    fun onUnlock(secret: String) {
        val raw = local.value.lockedRestore ?: return
        if (local.value.cryptoBusy) return
        local.update { it.copy(cryptoBusy = true, unlockFailed = false) }
        viewModelScope.launch {
            val opened = openBackup(raw, secret)
            local.update {
                when (opened) {
                    is OpenedBackup.Opened -> {
                        pendingKey = opened.key
                        it.copy(cryptoBusy = false, lockedRestore = null, pendingRestore = opened.json)
                    }
                    is OpenedBackup.Plain -> it.copy(cryptoBusy = false, lockedRestore = null, pendingRestore = opened.json)
                    OpenedBackup.NeedsSecret, OpenedBackup.WrongSecret -> it.copy(cryptoBusy = false, unlockFailed = true)
                }
            }
        }
    }

    fun onCancel() {
        if (local.value.cryptoBusy) return
        pendingKey = null
        local.update { it.copy(pendingRestore = null, pendingRestoreFrom = null, lockedRestore = null, unlockFailed = false) }
    }

    fun onConfirm() {
        val raw = local.value.pendingRestore ?: return
        val key = pendingKey
        pendingKey = null
        viewModelScope.launch {
            val result = restoreBackup(raw)
            // This device has no password yet: it keeps the backup's key, so its next backups open the same way.
            if (result is RestoreResult.Restored && key != null) adoptBackupKey(key)
            local.update {
                it.copy(
                    pendingRestore = null,
                    pendingRestoreFrom = null,
                    restored = result is RestoreResult.Restored,
                    message = when (result) {
                        is RestoreResult.Restored -> SettingsMessage.BackupRestored(result.friends)
                        RestoreResult.NotABackup -> SettingsMessage.BackupInvalid
                    },
                )
            }
        }
    }

    fun onDriveSetupShown() = local.update { it.copy(driveSetup = null) }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
