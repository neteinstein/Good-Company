package org.goodcompany.app.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.goodcompany.app.core.domain.model.BackupKey
import org.goodcompany.app.core.domain.model.BackupProtection
import org.goodcompany.app.core.domain.repository.BackupCrypto
import org.goodcompany.app.core.domain.repository.BackupKeyRepository

/** D4: the shortest backup password the app accepts. */
const val BACKUP_PASSWORD_MIN_LENGTH = 8

/** Whether a password is set on this device, and the old-Drive-file answer (D4, D5). */
class ObserveBackupProtectionUseCase(private val keys: BackupKeyRepository) {
    operator fun invoke(): Flow<BackupProtection> = keys.protection
}

sealed interface SetBackupPasswordResult {
    /** Show [recoveryKey] once: it is never stored. */
    data class Done(val recoveryKey: String) : SetBackupPasswordResult

    data object TooShort : SetBackupPasswordResult

    /** A password is set already: change it instead. */
    data object AlreadySet : SetBackupPasswordResult
}

/** D4: turns backup encryption on with a password the user sets; makes the recovery key. */
class SetBackupPasswordUseCase(private val crypto: BackupCrypto, private val keys: BackupKeyRepository) {
    suspend operator fun invoke(password: String): SetBackupPasswordResult {
        if (password.length < BACKUP_PASSWORD_MIN_LENGTH) return SetBackupPasswordResult.TooShort
        if (keys.current().hasPassword) return SetBackupPasswordResult.AlreadySet
        val made = crypto.newKey(password)
        keys.update { it.copy(key = made.key) }
        return SetBackupPasswordResult.Done(made.recoveryKey)
    }
}

enum class ChangeBackupPasswordResult { CHANGED, TOO_SHORT, WRONG_SECRET, NO_PASSWORD }

/**
 * D4 as built: changing the password asks for the current one (or the recovery key). The backup key stays the same, so
 * backups made before still open with the old password or the recovery key.
 */
class ChangeBackupPasswordUseCase(private val crypto: BackupCrypto, private val keys: BackupKeyRepository) {
    suspend operator fun invoke(current: String, newPassword: String): ChangeBackupPasswordResult {
        val key = keys.current().key ?: return ChangeBackupPasswordResult.NO_PASSWORD
        if (newPassword.length < BACKUP_PASSWORD_MIN_LENGTH) return ChangeBackupPasswordResult.TOO_SHORT
        if (!crypto.unlocks(key, current)) return ChangeBackupPasswordResult.WRONG_SECRET
        val changed = crypto.changePassword(key, newPassword)
        keys.update { it.copy(key = changed) }
        return ChangeBackupPasswordResult.CHANGED
    }
}

/**
 * D4: the backup as it leaves the device, in a file or in Google Drive: the [ExportBackupUseCase] document, encrypted
 * with this device's key. Null while no password is set (nothing is ever written in the clear).
 */
class EncryptBackupUseCase(
    private val export: ExportBackupUseCase,
    private val crypto: BackupCrypto,
    private val keys: BackupKeyRepository,
) {
    suspend operator fun invoke(): String? {
        val key = keys.current().key ?: return null
        return crypto.encrypt(key, export())
    }

    /** Suggested file name, e.g. `good-company-2026-11-07.json`. */
    fun fileName(): String = export.fileName()
}

/** What a picked or downloaded backup file turned out to be. */
sealed interface OpenedBackup {
    /** An old, unencrypted backup (or something that isn't a backup: the restore says so). */
    data class Plain(val json: String) : OpenedBackup

    /** Encrypted: ask for the password or the recovery key. */
    data object NeedsSecret : OpenedBackup

    /** The password or recovery key given doesn't open it. */
    data object WrongSecret : OpenedBackup

    data class Opened(val json: String, val key: BackupKey) : OpenedBackup
}

/** Opens a backup file for [RestoreBackupUseCase]: encrypted ones with the password or the recovery key (D4). */
class OpenBackupUseCase(private val crypto: BackupCrypto) {
    suspend operator fun invoke(raw: String, secret: String? = null): OpenedBackup {
        if (!crypto.isEncrypted(raw)) return OpenedBackup.Plain(raw)
        val given = secret?.takeIf { it.isNotBlank() } ?: return OpenedBackup.NeedsSecret
        val opened = crypto.decrypt(raw, given) ?: return OpenedBackup.WrongSecret
        return OpenedBackup.Opened(opened.json, opened.key)
    }
}

/**
 * After an encrypted backup was restored on a device with no password yet, keeps its key, so the next backups use the
 * same password (and recovery key). A device that has its own key keeps it.
 */
class AdoptBackupKeyUseCase(private val keys: BackupKeyRepository) {
    suspend operator fun invoke(key: BackupKey) = keys.update { if (it.hasPassword) it else it.copy(key = key) }
}
