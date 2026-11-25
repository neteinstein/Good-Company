package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.repository.CloudSpace
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_ENCRYPTED_BACKUP_FILE_NAME
import com.neteinstein.goodcompany.core.testing.FakeCloudBackupStore
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.core.ui.platform.DriveAuthResult
import com.neteinstein.goodcompany.feature.settings.ui.screens.DriveSetup
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.restore.RestoreBackupViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoreBackupViewModelTest {
    /** The old phone: two friends, a password, an encrypted backup. */
    private val old = TestGraph(listOf(friend("ana"), friend("rui")))

    /** The new phone, fresh from the intro guide. */
    private val g = TestGraph(emptyList())

    private fun vm() = RestoreBackupViewModel(g.openBackup, g.restoreBackup, g.adoptBackupKey, g.fetchDriveBackup, g.forgetDriveToken, g.clock)

    private suspend fun encryptedBackup(): String {
        old.setBackupPassword("correct horse")
        return old.encryptBackup()!!
    }

    @Test
    fun anEncryptedFileAsksForThePasswordThenRestoresAndEndsTheIntro() = runViewModelTest {
        val raw = encryptedBackup()
        val vm = vm()
        val state = collecting(vm.state)
        vm.onFilePicked(raw)
        assertNotNull(state.value.lockedRestore)
        vm.onUnlock("wrong password")
        assertTrue(state.value.unlockFailed)
        vm.onUnlock("correct horse")
        assertNull(state.value.lockedRestore)
        assertNotNull(state.value.pendingRestore)
        vm.onConfirm()
        assertTrue(state.value.restored)
        assertEquals(SettingsMessage.BackupRestored(2), state.value.message)
        assertEquals(2, g.friendRepo.friends.value.size)
        assertTrue(g.settingsRepo.settings.value.onboardingDone)
        // The new phone keeps the backup's key: its own backups open with the same password.
        assertTrue(g.backupKeyRepo.current().hasPassword)
    }

    @Test
    fun fromGoogleDrive() = runViewModelTest {
        g.cloudStore.files += FakeCloudBackupStore.File("f", DRIVE_ENCRYPTED_BACKUP_FILE_NAME, encryptedBackup(), g.clock.now(), CloudSpace.APP_DATA)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDriveRequested()
        assertTrue(state.value.driveBusy)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertFalse(state.value.driveBusy)
        assertEquals(g.clock.now(), state.value.pendingRestoreFrom)
        assertNotNull(state.value.lockedRestore)
        vm.onCancel()
        assertNull(state.value.lockedRestore)
        assertNull(state.value.pendingRestoreFrom)
        assertTrue(g.friendRepo.friends.value.isEmpty())
    }

    @Test
    fun driveProblemsAreShown() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDriveRequested()
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveNoBackup, state.value.message)
        vm.onMessageShown()

        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.UNAUTHORIZED)
        vm.onDriveRequested()
        vm.onDriveAuthResult(DriveAuthResult.Token("stale"))
        assertEquals(SettingsMessage.DriveFailed(CloudBackupFailure.Reason.UNAUTHORIZED), state.value.message)
        assertEquals(listOf("stale"), g.driveTokens.forgotten)

        vm.onDriveRequested()
        vm.onDriveAuthResult(DriveAuthResult.NotConfigured("pkg · SHA-1 AA"))
        assertEquals(DriveSetup("pkg · SHA-1 AA"), state.value.driveSetup)
        vm.onDriveSetupShown()
        assertNull(state.value.driveSetup)

        vm.onDriveRequested()
        vm.onDriveAuthResult(DriveAuthResult.Failed("SIGN_IN_FAILED (12500)"))
        assertEquals(SettingsMessage.DriveSignInFailed("SIGN_IN_FAILED (12500)"), state.value.message)
        vm.onDriveRequested()
        vm.onDriveAuthResult(DriveAuthResult.Failed(null, network = true))
        assertEquals(SettingsMessage.DriveFailed(CloudBackupFailure.Reason.NETWORK), state.value.message)
        vm.onDriveRequested()
        vm.onDriveAuthResult(DriveAuthResult.Cancelled)
        assertFalse(state.value.driveBusy)
    }

    @Test
    fun somethingThatIsntABackupIsRefused() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onFilePicked(null)
        vm.onFilePicked("   ")
        assertNull(state.value.pendingRestore)
        vm.onFilePicked("{\"hello\":1}")
        vm.onConfirm()
        assertEquals(SettingsMessage.BackupInvalid, state.value.message)
        assertFalse(state.value.restored)
    }
}
