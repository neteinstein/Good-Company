package org.goodcompany.app.core.domain.usecase

import kotlinx.serialization.json.Json
import org.goodcompany.app.core.domain.model.AgendaView
import org.goodcompany.app.core.domain.model.AppLanguage
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.AutoBackupFrequency
import org.goodcompany.app.core.domain.model.Backup
import org.goodcompany.app.core.domain.repository.BackupRepository
import org.goodcompany.app.core.domain.util.AppClock

/**
 * Serialises everything (minus the AI key) to the backup JSON document. Nothing writes it out as it is: files and
 * Google Drive always get it through [EncryptBackupUseCase] (D4).
 */
class ExportBackupUseCase(private val backups: BackupRepository, private val clock: AppClock) {
    suspend operator fun invoke(): String {
        val snapshot = backups.snapshot()
        val clean = snapshot.copy(
            format = Backup.FORMAT,
            version = Backup.VERSION,
            exportedAt = clock.now(),
            // Issue #103: this build keeps notes as journal entries, so a restore never converts them again.
            notesInJournal = true,
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
class RestoreBackupUseCase(private val backups: BackupRepository, private val clock: AppClock = AppClock.System) {
    suspend operator fun invoke(raw: String): RestoreResult {
        val backup = runCatching { BackupJson.decodeFromString(Backup.serializer(), raw) }.getOrNull()
            ?.takeIf { it.format == Backup.FORMAT && it.version <= Backup.VERSION }
            ?: return RestoreResult.NotABackup
        val current = backups.snapshot().settings
        // An old backup's kids come back as people (F9), and its free-text notes as dated notes (issue #103).
        backups.restore(
            NotesToJournal.convert(KidsAsPeople.convert(backup), clock.now()).copy(
                settings = backup.settings.copy(
                    aiApiKey = current.aiApiKey,
                    aiEnabled = current.aiEnabled,
                    appLock = current.appLock,
                    onboardingDone = true,
                    lastOpenedAt = current.lastOpenedAt,
                    lastSeenCardHiddenOn = current.lastSeenCardHiddenOn,
                    notificationPrimerDismissed = current.notificationPrimerDismissed,
                    mediaExplained = current.mediaExplained,
                    peopleLegendSeen = current.peopleLegendSeen,
                    agendaView = current.agendaView,
                    language = current.language,
                    lastDriveBackupAt = current.lastDriveBackupAt,
                    autoDriveBackup = current.autoDriveBackup,
                    lastAutoBackup = current.lastAutoBackup,
                    calendarSuggestions = current.calendarSuggestions,
                    calendarIds = current.calendarIds,
                    calendarOnSince = current.calendarOnSince,
                ),
            ),
        )
        return RestoreResult.Restored(backup.friends.size, backup.hangouts.size)
    }
}

/** Settings as a backup carries them: no AI key, nothing about this device's calendars (D3) or its Today card. */
internal fun AppSettings.withoutDeviceOnly() = copy(
    aiApiKey = null,
    lastSeenCardHiddenOn = null,
    notificationPrimerDismissed = false,
    mediaExplained = false,
    peopleLegendSeen = false,
    agendaView = AgendaView.CALENDAR,
    language = AppLanguage.SYSTEM,
    calendarSuggestions = false,
    calendarIds = emptyList(),
    calendarOnSince = null,
    lastDriveBackupAt = null,
    autoDriveBackup = AutoBackupFrequency.DAILY,
    lastAutoBackup = null,
)

internal val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = true
}
