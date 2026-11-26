package org.goodcompany.app.core.domain.usecase

import org.goodcompany.app.core.domain.repository.BackupKeyRepository
import org.goodcompany.app.core.domain.repository.CloudBackupFailure
import org.goodcompany.app.core.domain.repository.CloudBackupInfo
import org.goodcompany.app.core.domain.repository.CloudBackupStore
import org.goodcompany.app.core.domain.repository.CloudSpace
import org.goodcompany.app.core.domain.repository.SettingsRepository
import org.goodcompany.app.core.domain.util.AppClock
import kotlin.coroutines.cancellation.CancellationException

/**
 * The old visible, plain backup file in My Drive (2026-11-07). D5 moved backups to the app's private folder; this one
 * is only read (to restore an old backup) and, if the user agrees, moved to the bin.
 */
const val DRIVE_BACKUP_FILE_NAME = "Good Company backup.json"

/** The encrypted backup in the app's private Drive folder (`drive.appdata`, D5). */
const val DRIVE_ENCRYPTED_BACKUP_FILE_NAME = "good-company-backup.json"

/** OAuth scope for the app's private folder in the user's Drive, where backups go (D5). */
const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/** OAuth scope: only files this app created, never the rest of their Drive. Needed for the old visible file. */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** Every scope the Drive backup asks for, in one consent. */
val DRIVE_SCOPES = listOf(DRIVE_APPDATA_SCOPE, DRIVE_FILE_SCOPE)

/** A backup can't be made before a password is set (D4): nothing is ever uploaded in the clear. */
class BackupPasswordMissing : Exception("No backup password")

/** A Drive backup was saved; [oldVisibleFile] is the old plain file the app now offers to delete (D5), if any. */
data class DriveBackupSaved(val info: CloudBackupInfo, val oldVisibleFile: CloudBackupInfo? = null)

/** Saves the encrypted backup (never the AI key) to the app's private Drive folder, replacing the last copy. */
class BackupToDriveUseCase(
    private val encrypt: EncryptBackupUseCase,
    private val store: CloudBackupStore,
    private val keys: BackupKeyRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(accessToken: String): Result<DriveBackupSaved> {
        val content = encrypt() ?: return Result.failure(BackupPasswordMissing())
        return cloudCall {
            val info = store.save(accessToken, DRIVE_ENCRYPTED_BACKUP_FILE_NAME, content, CloudSpace.APP_DATA)
            settings.update { it.copy(lastDriveBackupAt = clock.now()) }
            // Looking for the old file must never fail a backup that was saved.
            val old = if (keys.current().keepOldDriveFile) {
                null
            } else {
                runCatching { store.latest(accessToken, DRIVE_BACKUP_FILE_NAME, CloudSpace.VISIBLE) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            }
            DriveBackupSaved(info, old)
        }
    }
}

/** A Drive backup downloaded and ready to open; [plain] when it is the old visible, unencrypted file. */
data class DriveBackup(val content: String, val info: CloudBackupInfo, val plain: Boolean = false)

/**
 * Fetches the newest Drive backup: the encrypted one in the app's folder, or else the old plain visible file (old
 * backups stay restorable). `null` inside the result means there's none.
 */
class FetchDriveBackupUseCase(private val store: CloudBackupStore) {
    suspend operator fun invoke(accessToken: String): Result<DriveBackup?> = cloudCall {
        store.latest(accessToken, DRIVE_ENCRYPTED_BACKUP_FILE_NAME, CloudSpace.APP_DATA)
            ?.let { DriveBackup(store.download(accessToken, it.id), it) }
            ?: store.latest(accessToken, DRIVE_BACKUP_FILE_NAME, CloudSpace.VISIBLE)
                ?.let { DriveBackup(store.download(accessToken, it.id), it, plain = true) }
    }
}

/** D5: the user agreed to delete the old plain backup file; it goes to the Drive bin. */
class TrashOldDriveBackupUseCase(private val store: CloudBackupStore) {
    suspend operator fun invoke(accessToken: String, fileId: String): Result<Unit> = cloudCall { store.trash(accessToken, fileId) }
}

/** D5: the user chose to keep the old plain backup file; the app doesn't ask again. */
class KeepOldDriveBackupUseCase(private val keys: BackupKeyRepository) {
    suspend operator fun invoke() = keys.update { it.copy(keepOldDriveFile = true) }
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
