package org.goodcompany.app.core.domain

import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.AutoBackupFrequency
import org.goodcompany.app.core.domain.model.AutoBackupProblem
import org.goodcompany.app.core.domain.model.AutoBackupRun
import org.goodcompany.app.core.domain.repository.CloudBackupFailure
import org.goodcompany.app.core.domain.repository.SilentDriveToken
import org.goodcompany.app.core.domain.usecase.AutoBackupOutcome
import org.goodcompany.app.core.domain.usecase.RestoreResult
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class AutoBackupUseCasesTest {
    private val g = TestGraph(listOf(friend("ana")))
    private val settings get() = g.settingsRepo.settings.value

    /** A device where "Back up now" already worked [ago] (so Google granted access). */
    private suspend fun connected(ago: kotlin.time.Duration = 2.days, password: Boolean = true) {
        if (password) g.setBackupPassword("correct horse")
        g.settingsRepo.update { it.copy(lastDriveBackupAt = g.clock.now() - ago) }
    }

    @Test
    fun dailyIsTheDefault() {
        assertEquals(AutoBackupFrequency.DAILY, AppSettings().autoDriveBackup)
    }

    @Test
    fun dueBackupIsSavedWithASilentTokenAndRecorded() = runTest {
        connected()
        assertEquals(AutoBackupOutcome.Saved, g.runAutoBackup())
        assertEquals(listOf("silent-1"), g.cloudStore.tokens.distinct())
        assertTrue(g.backupCrypto.isEncrypted(g.cloudStore.files.single().content))
        assertEquals(AutoBackupRun(g.clock.now()), settings.lastAutoBackup)
        assertEquals(g.clock.now(), settings.lastDriveBackupAt)
    }

    @Test
    fun neverRunsBeforeTheFirstBackupOnThisDevice() = runTest {
        g.setBackupPassword("correct horse")
        assertEquals(AutoBackupOutcome.Skipped, g.runAutoBackup())
        assertTrue(g.cloudStore.tokens.isEmpty())
        assertNull(settings.lastAutoBackup)
    }

    @Test
    fun offNeverRuns() = runTest {
        connected(ago = 30.days)
        g.setAutoBackupFrequency(AutoBackupFrequency.OFF)
        assertEquals(AutoBackupOutcome.Skipped, g.runAutoBackup())
        assertTrue(g.cloudStore.tokens.isEmpty())
    }

    @Test
    fun dailyWithLeewayAndWeekly() = runTest {
        connected(ago = 21.hours)
        assertEquals(AutoBackupOutcome.Saved, g.runAutoBackup()) // a day minus a few hours still counts as a day
        connected(ago = 3.hours, password = false)
        assertEquals(AutoBackupOutcome.Skipped, g.runAutoBackup())
        g.setAutoBackupFrequency(AutoBackupFrequency.WEEKLY)
        connected(ago = 3.days, password = false)
        assertEquals(AutoBackupOutcome.Skipped, g.runAutoBackup())
        connected(ago = 7.days, password = false)
        assertEquals(AutoBackupOutcome.Saved, g.runAutoBackup())
    }

    @Test
    fun withoutAPasswordItSaysSoInsteadOfFailingSilently() = runTest {
        connected(password = false)
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.NO_PASSWORD), g.runAutoBackup())
        assertEquals(AutoBackupProblem.NO_PASSWORD, settings.lastAutoBackup?.problem)
        assertTrue(g.cloudStore.tokens.isEmpty())
    }

    @Test
    fun googleWantingTheUserIsRecorded() = runTest {
        connected()
        g.driveTokens.result = SilentDriveToken.SignInNeeded
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.SIGN_IN_NEEDED), g.runAutoBackup())
        g.driveTokens.result = SilentDriveToken.NotConfigured
        assertEquals(AutoBackupProblem.NOT_CONFIGURED, (g.runAutoBackup() as AutoBackupOutcome.Failed).problem)
        g.driveTokens.result = SilentDriveToken.Failed(network = true)
        val offline = g.runAutoBackup()
        assertIs<AutoBackupOutcome.Failed>(offline)
        assertTrue(offline.retrySoon)
        assertTrue(g.cloudStore.files.isEmpty())
    }

    @Test
    fun aRefusedTokenIsDroppedAndTriedOnceMore() = runTest {
        connected()
        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.UNAUTHORIZED)
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.UNAUTHORIZED), g.runAutoBackup())
        assertEquals(listOf("silent-1"), g.driveTokens.forgotten)
        assertEquals(listOf("silent-1", "silent-2"), g.cloudStore.tokens.distinct())
        assertFalse(settings.lastAutoBackup!!.ok)
    }

    @Test
    fun driveFailuresAreRecorded() = runTest {
        connected()
        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.STORAGE_FULL)
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.STORAGE_FULL), g.runAutoBackup())
        assertFalse((g.runAutoBackup() as AutoBackupOutcome.Failed).retrySoon)
    }

    @Test
    fun theSettingStaysOnTheDeviceAndOutOfBackups() = runTest {
        connected()
        g.runAutoBackup()
        g.setAutoBackupFrequency(AutoBackupFrequency.WEEKLY)
        val exported = g.exportBackup()
        assertFalse(exported.contains("autoDriveBackup"))
        assertFalse(exported.contains("lastAutoBackup"))
        g.setAutoBackupFrequency(AutoBackupFrequency.OFF)
        assertIs<RestoreResult.Restored>(g.restoreBackup(exported))
        assertEquals(AutoBackupFrequency.OFF, settings.autoDriveBackup)
        assertEquals(AutoBackupRun(g.clock.now()), settings.lastAutoBackup)
    }

    @Test
    fun forgettingATokenReachesTheSource() = runTest {
        g.forgetDriveToken("stale")
        assertEquals(listOf("stale"), g.driveTokens.forgotten)
    }
}
