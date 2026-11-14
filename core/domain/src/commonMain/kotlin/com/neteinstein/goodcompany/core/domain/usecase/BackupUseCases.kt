package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.repository.BackupRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.serialization.json.Json

/** Serialises everything (minus the AI key) to a JSON document the user can save anywhere. */
class ExportBackupUseCase(private val backups: BackupRepository, private val clock: AppClock) {
    suspend operator fun invoke(): String {
        val snapshot = backups.snapshot()
        val clean = snapshot.copy(
            format = Backup.FORMAT,
            version = Backup.VERSION,
            exportedAt = clock.now(),
            settings = snapshot.settings.withoutDeviceOnly(),
        )
        return BackupJson.encodeToString(Backup.serializer(), clean)
    }

    /** Suggested file name, e.g. `good-company-2026-11-07.json`. */
    fun fileName(): String = "good-company-${clock.today()}.json"
}

sealed interface RestoreResult {
    data class Restored(val friends: Int, val hangouts: Int) : RestoreResult

    /** Not a Good Company backup, or from a newer app version. */
    data object NotABackup : RestoreResult
}

/**
 * Replaces all app data with a backup. Device-specific settings that a backup shouldn't move (the AI key and the AI
 * switch that goes with it, the app lock, onboarding state) stay as they are on this device.
 */
class RestoreBackupUseCase(private val backups: BackupRepository) {
    suspend operator fun invoke(raw: String): RestoreResult {
        val backup = runCatching { BackupJson.decodeFromString(Backup.serializer(), raw) }.getOrNull()
            ?.takeIf { it.format == Backup.FORMAT && it.version <= Backup.VERSION }
            ?: return RestoreResult.NotABackup
        val current = backups.snapshot().settings
        // An old backup's kids come back as people (F9).
        backups.restore(
            KidsAsPeople.convert(backup).copy(
                settings = backup.settings.copy(
                    aiApiKey = current.aiApiKey,
                    aiEnabled = current.aiEnabled,
                    appLock = current.appLock,
                    onboardingDone = true,
                    lastOpenedAt = current.lastOpenedAt,
                    lastDriveBackupAt = current.lastDriveBackupAt,
                    calendarSuggestions = current.calendarSuggestions,
                    calendarIds = current.calendarIds,
                    calendarOnSince = current.calendarOnSince,
                ),
            ),
        )
        return RestoreResult.Restored(backup.friends.size, backup.hangouts.size)
    }
}

/** Settings as a backup carries them: no AI key and nothing about this device's calendars (D3). */
internal fun AppSettings.withoutDeviceOnly() = copy(
    aiApiKey = null,
    calendarSuggestions = false,
    calendarIds = emptyList(),
    calendarOnSince = null,
)

internal val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = true
}
