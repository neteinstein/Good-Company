package org.goodcompany.app.core.domain.usecase

import kotlinx.coroutines.flow.first
import org.goodcompany.app.core.domain.model.AutoBackupFrequency
import org.goodcompany.app.core.domain.model.AutoBackupProblem
import org.goodcompany.app.core.domain.model.AutoBackupRun
import org.goodcompany.app.core.domain.repository.BackupKeyRepository
import org.goodcompany.app.core.domain.repository.CloudBackupFailure
import org.goodcompany.app.core.domain.repository.DriveTokenSource
import org.goodcompany.app.core.domain.repository.SettingsRepository
import org.goodcompany.app.core.domain.repository.SilentDriveToken
import org.goodcompany.app.core.domain.util.AppClock
import kotlin.time.Duration.Companion.hours

/** What one run of the automatic Drive backup did (issue #166). */
sealed interface AutoBackupOutcome {
    /** Off, not due yet, or Drive was never used on this device: nothing was tried and nothing recorded. */
    data object Skipped : AutoBackupOutcome

    data object Saved : AutoBackupOutcome

    /** Recorded in [org.goodcompany.app.core.domain.model.AppSettings.lastAutoBackup]; [retrySoon] for a lost connection. */
    data class Failed(val problem: AutoBackupProblem) : AutoBackupOutcome {
        val retrySoon: Boolean get() = problem == AutoBackupProblem.NETWORK
    }
}

/**
 * The automatic Drive backup (issue #166, Android's daily job): when one is due, saves the encrypted backup to the
 * app's private Drive folder with the access the user already granted, and records how it went so Settings can show
 * it. It starts only after a first "Back up now" on this device (the one place Google can ask the user), and without
 * a backup password it can't run (D4): that is recorded too, never skipped silently.
 */
class RunAutoBackupUseCase(
    private val backupToDrive: BackupToDriveUseCase,
    private val tokens: DriveTokenSource,
    private val keys: BackupKeyRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(): AutoBackupOutcome {
        val current = settings.settings.first()
        val frequency = current.autoDriveBackup
        val last = current.lastDriveBackupAt
        if (frequency == AutoBackupFrequency.OFF || last == null) return AutoBackupOutcome.Skipped
        // The job runs about once a day; a few hours' leeway keeps a daily backup from slipping to every other day.
        if (clock.now() - last < frequency.every - LEEWAY) return AutoBackupOutcome.Skipped
        if (!keys.current().hasPassword) return record(AutoBackupProblem.NO_PASSWORD)
        val first = when (val token = tokens.tokenWithoutUi()) {
            is SilentDriveToken.Token -> token.accessToken
            else -> return record(token.problem())
        }
        var result = backupToDrive(first)
        if (result.reason() == CloudBackupFailure.Reason.UNAUTHORIZED) {
            // A cached token Drive no longer accepts (revoked or expired): drop it and try once with a fresh one.
            tokens.forget(first)
            val again = tokens.tokenWithoutUi()
            if (again !is SilentDriveToken.Token) return record(again.problem())
            result = backupToDrive(again.accessToken)
        }
        return record(result.exceptionOrNull()?.problem())
    }

    private suspend fun record(problem: AutoBackupProblem?): AutoBackupOutcome {
        settings.update { it.copy(lastAutoBackup = AutoBackupRun(clock.now(), problem)) }
        return problem?.let(AutoBackupOutcome::Failed) ?: AutoBackupOutcome.Saved
    }

    private companion object {
        val LEEWAY = 4.hours
    }
}

/** Settings → Google Drive → "Automatic backup": Daily, Weekly or Off (issue #166). */
class SetAutoBackupFrequencyUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(frequency: AutoBackupFrequency) = settings.update { it.copy(autoDriveBackup = frequency) }
}

/** A Drive token the app got on screen was refused: drop it so the next try asks Google for a fresh one. */
class ForgetDriveTokenUseCase(private val tokens: DriveTokenSource) {
    suspend operator fun invoke(accessToken: String) = tokens.forget(accessToken)
}

private fun Result<*>.reason(): CloudBackupFailure.Reason? = (exceptionOrNull() as? CloudBackupFailure)?.reason

private fun Throwable.problem(): AutoBackupProblem = when (this) {
    is BackupPasswordMissing -> AutoBackupProblem.NO_PASSWORD
    is CloudBackupFailure -> when (reason) {
        CloudBackupFailure.Reason.UNAUTHORIZED -> AutoBackupProblem.UNAUTHORIZED
        CloudBackupFailure.Reason.FORBIDDEN -> AutoBackupProblem.FORBIDDEN
        CloudBackupFailure.Reason.STORAGE_FULL -> AutoBackupProblem.STORAGE_FULL
        CloudBackupFailure.Reason.NETWORK -> AutoBackupProblem.NETWORK
        CloudBackupFailure.Reason.OTHER -> AutoBackupProblem.OTHER
    }
    else -> AutoBackupProblem.OTHER
}

private fun SilentDriveToken.problem(): AutoBackupProblem = when (this) {
    is SilentDriveToken.Token -> AutoBackupProblem.OTHER
    SilentDriveToken.SignInNeeded -> AutoBackupProblem.SIGN_IN_NEEDED
    SilentDriveToken.NotConfigured -> AutoBackupProblem.NOT_CONFIGURED
    is SilentDriveToken.Failed -> if (network) AutoBackupProblem.NETWORK else AutoBackupProblem.OTHER
}
