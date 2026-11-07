package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupInfo
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupStore
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlin.coroutines.cancellation.CancellationException

/** The one file the app keeps in the user's Google Drive. Plain JSON, same as "Export backup" (owner, 2026-11-07). */
const val DRIVE_BACKUP_FILE_NAME = "Good Company backup.json"

/** OAuth scope: only files this app created (or the user opened with it), never the rest of their Drive. */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** Saves the same JSON as "Export backup" (never the AI key) to the user's Google Drive, replacing the last copy. */
class BackupToDriveUseCase(
    private val export: ExportBackupUseCase,
    private val store: CloudBackupStore,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(accessToken: String): Result<CloudBackupInfo> = cloudCall {
        val info = store.save(accessToken, DRIVE_BACKUP_FILE_NAME, export())
        settings.update { it.copy(lastDriveBackupAt = clock.now()) }
        info
    }
}

/** A Drive backup downloaded and ready for the "Replace everything?" confirmation. */
data class DriveBackup(val content: String, val info: CloudBackupInfo)

/** Fetches the newest Drive backup; `null` inside the result means there's none yet. */
class FetchDriveBackupUseCase(private val store: CloudBackupStore) {
    suspend operator fun invoke(accessToken: String): Result<DriveBackup?> = cloudCall {
        store.latest(accessToken, DRIVE_BACKUP_FILE_NAME)?.let { DriveBackup(store.download(accessToken, it.id), it) }
    }
}

private suspend fun <T> cloudCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: CloudBackupFailure) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(CloudBackupFailure(CloudBackupFailure.Reason.OTHER, e.message))
}
