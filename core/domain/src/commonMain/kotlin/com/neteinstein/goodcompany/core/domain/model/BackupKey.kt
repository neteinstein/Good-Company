package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable

/**
 * D4 (issue #165): the key every backup is encrypted with, as this device keeps it (owner, 2026-11-22: "keep the
 * derived key on the device", so automatic backups run unattended).
 *
 * [dataKey] is a random key that encrypts the backups. It is stored here and inside each backup file, wrapped
 * (encrypted) once by a key derived from the password and once by a key derived from the recovery key ([slots]);
 * either one opens a backup. Changing the password re-wraps the same [dataKey], so older backups still open with
 * the old password or the recovery key. All values are Base64; the format lives in `:core:data`.
 *
 * Device-only: never in a backup, kept out of Android's cloud backup with the rest of the key-value settings.
 */
@Serializable
data class BackupKey(val dataKey: String, val slots: List<BackupKeySlot>) {
    val password: BackupKeySlot? get() = slots.firstOrNull { it.kind == BackupKeySlot.Kind.PASSWORD }
    val recovery: BackupKeySlot? get() = slots.firstOrNull { it.kind == BackupKeySlot.Kind.RECOVERY }
}

/** [dataKey][BackupKey.dataKey] wrapped by a key derived (PBKDF2) from a password or the recovery key. */
@Serializable
data class BackupKeySlot(
    val kind: Kind,
    val iterations: Int,
    val salt: String,
    /** The data key encrypted with the derived key, its random IV first. */
    val wrapped: String,
    val mac: String,
) {
    @Serializable
    enum class Kind { PASSWORD, RECOVERY }
}

/**
 * What this device keeps about backup encryption: the [key] (null until a password is set), and whether the user
 * chose to keep the old plain "Good Company backup.json" in their Drive (D5), so the app stops offering to delete it.
 */
@Serializable
data class BackupProtection(val key: BackupKey? = null, val keepOldDriveFile: Boolean = false) {
    val hasPassword: Boolean get() = key != null
}

/** A key made with a new password, plus the recovery key, shown once and never stored. */
data class NewBackupKey(val key: BackupKey, val recoveryKey: String)
