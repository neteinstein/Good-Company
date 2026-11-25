package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.model.AutoBackupFrequency
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.repository.CloudSpace
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_BACKUP_FILE_NAME
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_ENCRYPTED_BACKUP_FILE_NAME
import com.neteinstein.goodcompany.core.testing.FakeCloudBackupStore
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.core.ui.platform.DriveAuthResult
import com.neteinstein.goodcompany.core.ui.platform.UpdateInstallResult
import com.neteinstein.goodcompany.feature.settings.ui.screens.AfterPassword
import com.neteinstein.goodcompany.feature.settings.ui.screens.DriveAction
import com.neteinstein.goodcompany.feature.settings.ui.screens.DriveSetup
import com.neteinstein.goodcompany.feature.settings.ui.screens.PasswordDialog
import com.neteinstein.goodcompany.feature.settings.ui.screens.PasswordError
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.UpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    private val g = TestGraph()
    private val repo get() = g.settingsRepo

    private fun vm() = SettingsViewModel(
        g.observeSettings,
        g.updateSettings,
        g.hasSampleData,
        g.loadSampleData,
        g.removeSampleData,
        g.importContactsFile,
        g.encryptBackup,
        g.openBackup,
        g.restoreBackup,
        g.adoptBackupKey,
        g.observeBackupProtection,
        g.setBackupPassword,
        g.changeBackupPassword,
        g.backupToDrive,
        g.fetchDriveBackup,
        g.trashOldDriveBackup,
        g.keepOldDriveBackup,
        g.setAutoBackupFrequency,
        g.forgetDriveToken,
        g.checkForUpdate,
        g.observeMyPhoto,
        g.setMyPhoto,
        g.removeMyPhoto,
        g.clock,
    )

    @Test
    fun yourPhotoCanBeSetChangedRemovedAndPutBack() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertNull(state.value.myPhoto)
        vm.onRemoveMyPhoto() // nothing to remove
        assertNull(state.value.message)

        vm.onMyPhotoPicked(byteArrayOf(1, 2, 3))
        assertEquals(ContactPhoto.of(byteArrayOf(1, 2, 3)), state.value.myPhoto)
        vm.onMyPhotoPicked(byteArrayOf(4, 5))
        assertEquals(ContactPhoto.of(byteArrayOf(4, 5)), state.value.myPhoto)

        vm.onMyPhotoPicked(null)
        assertEquals(SettingsMessage.MyPhotoUnreadable, state.value.message)
        assertEquals(ContactPhoto.of(byteArrayOf(4, 5)), state.value.myPhoto)
        vm.onMessageShown()

        vm.onRemoveMyPhoto()
        assertNull(state.value.myPhoto)
        assertNull(g.myPhotoRepo.photo.value)
        assertEquals(SettingsMessage.MyPhotoRemoved, state.value.message)
        vm.onUndoRemoveMyPhoto()
        assertEquals(ContactPhoto.of(byteArrayOf(4, 5)), state.value.myPhoto)
        vm.onUndoRemoveMyPhoto() // only once
        assertEquals(ContactPhoto.of(byteArrayOf(4, 5)), g.myPhotoRepo.photo.value)
    }

    @Test
    fun apiKeyIsTrimmedMaskedAndRemovable() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onSaveApiKey("   ")
        assertNull(state.value.maskedKey)
        vm.onSaveApiKey("  sk-ant-api03-abcdefghij1234  ")
        assertEquals("sk-ant-api03-abcdefghij1234", repo.settings.value.aiApiKey)
        assertEquals("sk-ant-…1234", state.value.maskedKey)
        assertEquals(SettingsMessage.KeySaved, state.value.message)
        vm.onClearApiKey()
        assertNull(state.value.maskedKey)
        assertEquals(SettingsMessage.KeyRemoved, state.value.message)
    }

    @Test
    fun aiIsOffByDefault_andTheSwitchKeepsTheKey() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.settings.aiEnabled)
        vm.onAiEnabledChange(true)
        vm.onSaveApiKey("sk-ant-api03-abcdefghij1234")
        assertTrue(state.value.settings.aiEnabled)
        assertEquals("sk-ant-api03-abcdefghij1234", state.value.settings.usableAiKey)
        vm.onAiEnabledChange(false)
        assertFalse(state.value.settings.aiEnabled)
        assertNull(state.value.settings.usableAiKey)
        assertEquals("sk-ant-…1234", state.value.maskedKey) // kept for when it's turned back on
    }

    @Test
    fun preferencesUpdate() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onThemeChange(ThemeMode.DARK)
        vm.onDynamicColorChange(false)
        vm.onWeeklyIdeasChange(false)
        vm.onHomeAreaChange("Porto")
        vm.onClosenessDaysChange(Closeness.INNER, 14)
        vm.onClosenessDaysChange(Closeness.CLOSE, 60)
        vm.onClosenessDaysChange(Closeness.GOOD, 0)
        vm.onClosenessDaysChange(Closeness.INNER, null) // only Acquaintances can be off
        vm.onClosenessDaysChange(Closeness.ACQUAINTANCE, 180)
        vm.onNotificationPermissionResult(false)
        val s = state.value.settings
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(false, s.dynamicColor)
        assertEquals(false, s.weeklyIdeasEnabled)
        assertEquals("Porto", s.homeArea)
        assertEquals(14, s.innerCircleDays)
        assertEquals(60, s.closeCircleDays)
        assertEquals(1, s.goodFriendsDays)
        assertEquals(180, s.acquaintanceDays)
        vm.onClosenessDaysChange(Closeness.ACQUAINTANCE, null)
        assertEquals(null, state.value.settings.acquaintanceDays)
        assertEquals(SettingsMessage.Notifications(false), state.value.message)
        assertEquals("••••", SettingsViewModel.mask("short"))
    }

    @Test
    fun sampleDataToggleAndReplayIntro() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onLoadSampleData()
        assertEquals(true, state.value.hasSampleData)
        assertEquals(SettingsMessage.SampleAdded, state.value.message)
        vm.onRemoveSampleData()
        assertEquals(false, state.value.hasSampleData)
        assertEquals(SettingsMessage.SampleRemoved, state.value.message)
        repo.update { it.copy(onboardingDone = true) }
        vm.onReplayIntro()
        assertEquals(false, repo.settings.value.onboardingDone)
    }

    @Test
    fun importsContactsFile() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onContactsFilePicked("")
        assertNull(state.value.message)
        vm.onContactsFilePicked("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Marta Reis\r\nBDAY:--0504\r\nEND:VCARD\r\n")
        assertEquals(SettingsMessage.FileImported(ContactsFileImportResult(1, 0, 0)), state.value.message)
        assertEquals("Marta Reis", g.friendRepo.friends.value.single().name)
    }

    @Test
    fun warmthAndLockSettings() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onShowWarmthChange(false)
        val s = state.value.settings
        assertFalse(s.showWarmth)

        vm.onAppLockChange(enabled = true, verified = false)
        assertFalse(state.value.settings.appLock)
        assertEquals(SettingsMessage.LockFailed, state.value.message)
        vm.onAppLockChange(enabled = true, verified = true)
        assertTrue(state.value.settings.appLock)
        vm.onAppLockChange(enabled = false, verified = true)
        assertFalse(state.value.settings.appLock)
    }

    @Test
    fun exportAsksForAPasswordFirstThenShowsTheRecoveryKeyThenExports() = runViewModelTest {
        g.addFriend("Ana")
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.hasBackupPassword)
        vm.onExportBackup()
        assertNull(state.value.export)
        assertEquals(PasswordDialog.SET, state.value.passwordDialog)

        vm.onSavePassword("long enough", "not the same")
        assertEquals(PasswordError.MISMATCH, state.value.passwordError)
        vm.onSavePassword("short", "short")
        assertEquals(PasswordError.TOO_SHORT, state.value.passwordError)
        vm.onSavePassword("long enough", "long enough")
        assertNull(state.value.passwordDialog)
        assertTrue(state.value.hasBackupPassword)
        assertEquals("RECOVERY-1", state.value.recoveryKey)
        assertNull(state.value.export) // not before the recovery key was acknowledged

        assertNull(vm.onRecoveryKeySaved())
        assertNull(state.value.recoveryKey)
        val file = state.value.export!!
        assertEquals("good-company-2026-03-10.json", file.name)
        assertTrue(g.backupCrypto.isEncrypted(file.content))
        assertFalse(file.content.contains("Ana"))
        vm.onExportHandled(saved = true)
        assertNull(state.value.export)
        assertEquals(SettingsMessage.BackupSaved, state.value.message)
    }

    @Test
    fun cancellingThePasswordDialogForgetsTheExport() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onExportBackup()
        vm.onDismissPasswordDialog()
        assertNull(state.value.passwordDialog)
        vm.onSetBackupPassword()
        vm.onSavePassword("long enough", "long enough")
        assertNull(vm.onRecoveryKeySaved())
        assertNull(state.value.export)
    }

    @Test
    fun anEncryptedBackupAsksForThePasswordBeforeReplacingEverything() = runViewModelTest {
        g.addFriend("Ana")
        g.setBackupPassword("long enough")
        val file = g.encryptBackup()!!
        g.backupKeyRepo.state.value = g.backupKeyRepo.state.value.copy(key = null) // a new phone
        val vm = vm()
        val state = collecting(vm.state)

        vm.onBackupPicked(file)
        assertEquals(file, state.value.lockedRestore)
        assertNull(state.value.pendingRestore)
        vm.onUnlockBackup("wrong one")
        assertTrue(state.value.unlockFailed)
        vm.onUnlockBackup("long enough")
        assertNull(state.value.lockedRestore)
        assertTrue(state.value.pendingRestore!!.contains("Ana"))
        vm.onConfirmRestore()
        assertEquals(SettingsMessage.BackupRestored(1), state.value.message)
        assertTrue(state.value.hasBackupPassword) // the restored backup's key is kept for the next backups

        vm.onBackupPicked(file)
        vm.onCancelRestore()
        assertNull(state.value.lockedRestore)
    }

    @Test
    fun plainOldBackupsStillRestore() = runViewModelTest {
        g.addFriend("Ana")
        val plain = g.exportBackup()
        val vm = vm()
        val state = collecting(vm.state)
        vm.onBackupPicked("nope")
        assertEquals("nope", state.value.pendingRestore)
        vm.onConfirmRestore()
        assertNull(state.value.pendingRestore)
        assertEquals(SettingsMessage.BackupInvalid, state.value.message)

        vm.onBackupPicked(plain)
        vm.onCancelRestore()
        assertNull(state.value.pendingRestore)
        vm.onBackupPicked(plain)
        vm.onConfirmRestore()
        assertEquals(SettingsMessage.BackupRestored(1), state.value.message)
        assertFalse(state.value.hasBackupPassword)
    }

    @Test
    fun changingThePasswordChecksTheCurrentOne() = runViewModelTest {
        g.setBackupPassword("first password")
        val vm = vm()
        val state = collecting(vm.state)
        vm.onChangeBackupPassword()
        assertEquals(PasswordDialog.CHANGE, state.value.passwordDialog)
        vm.onSaveChangedPassword("nope", "second password", "second password")
        assertEquals(PasswordError.WRONG_SECRET, state.value.passwordError)
        vm.onSaveChangedPassword("first password", "second password", "typo")
        assertEquals(PasswordError.MISMATCH, state.value.passwordError)
        vm.onSaveChangedPassword("first password", "short", "short")
        assertEquals(PasswordError.TOO_SHORT, state.value.passwordError)
        vm.onSaveChangedPassword("RECOVERY-1", "second password", "second password")
        assertNull(state.value.passwordDialog)
        assertEquals(SettingsMessage.BackupPasswordChanged, state.value.message)
        assertTrue(g.backupCrypto.unlocks(g.backupKeyRepo.state.value.key!!, "second password"))
    }

    @Test
    fun driveBackupWithoutAPasswordAsksForOneThenGoesOnAfterTheRecoveryKey() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(vm.onDriveBackupTapped())
        assertEquals(PasswordDialog.SET, state.value.passwordDialog)
        assertFalse(state.value.driveBusy)
        vm.onSavePassword("long enough", "long enough")
        // The screen starts Google's sign-in on this tap.
        assertEquals(AfterPassword.DRIVE_BACKUP, vm.onRecoveryKeySaved())
        assertTrue(vm.onDriveBackupTapped())
        assertTrue(state.value.driveBusy)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveBackedUp, state.value.message)
        assertEquals(DRIVE_ENCRYPTED_BACKUP_FILE_NAME, g.cloudStore.files.single().name)
    }

    @Test
    fun driveBackupAndRestoreAfterTheTokenArrives() = runViewModelTest {
        g.friendRepo.upsert(friend("ana"))
        g.setBackupPassword("long enough")
        g.cloudStore.now = g.clock.now()
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(vm.onDriveBackupTapped())
        assertTrue(state.value.driveBusy)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveBackedUp, state.value.message)
        assertEquals(g.clock.now(), state.value.settings.lastDriveBackupAt)
        assertFalse(state.value.driveBusy)
        assertNull(state.value.oldDriveFile)

        g.friendRepo.delete(FriendId("ana"))
        vm.onDriveRequested(DriveAction.RESTORE)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(g.clock.now(), state.value.pendingRestoreFrom)
        assertTrue(state.value.lockedRestore != null)
        vm.onUnlockBackup("long enough")
        vm.onConfirmRestore()
        assertEquals(SettingsMessage.BackupRestored(1), state.value.message)
        assertNull(state.value.pendingRestoreFrom)
        assertEquals(listOf("Ana"), g.friendRepo.friends.value.map { it.name })
    }

    @Test
    fun afterTheFirstEncryptedDriveBackupTheOldFileIsOfferedForDeletion() = runViewModelTest {
        g.setBackupPassword("long enough")
        g.cloudStore.files += FakeCloudBackupStore.File("old", DRIVE_BACKUP_FILE_NAME, "{}", null, CloudSpace.VISIBLE)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDriveBackupTapped()
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals("old", state.value.oldDriveFile?.id)
        vm.onTrashOldDriveFile()
        assertNull(state.value.oldDriveFile)
        assertEquals(SettingsMessage.OldDriveFileTrashed, state.value.message)
        assertTrue(g.cloudStore.files.first { it.id == "old" }.trashed)
    }

    @Test
    fun keepingTheOldFileStopsTheQuestion() = runViewModelTest {
        g.setBackupPassword("long enough")
        g.cloudStore.files += FakeCloudBackupStore.File("old", DRIVE_BACKUP_FILE_NAME, "{}", null, CloudSpace.VISIBLE)
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDriveBackupTapped()
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        vm.onKeepOldDriveFile()
        assertNull(state.value.oldDriveFile)
        vm.onDriveBackupTapped()
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertNull(state.value.oldDriveFile)
        assertFalse(g.cloudStore.files.first { it.id == "old" }.trashed)
    }

    @Test
    fun driveOutcomesWithoutData() = runViewModelTest {
        g.setBackupPassword("long enough")
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDriveRequested(DriveAction.RESTORE)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveNoBackup, state.value.message)

        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Cancelled)
        assertFalse(state.value.driveBusy)
        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.NotConfigured("com.example · SHA-1 AA"))
        assertEquals(DriveSetup("com.example · SHA-1 AA"), state.value.driveSetup)
        assertFalse(state.value.driveBusy)
        vm.onDriveSetupShown()
        assertNull(state.value.driveSetup)

        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Failed("INTERNAL_ERROR (8)"))
        assertEquals(SettingsMessage.DriveSignInFailed("INTERNAL_ERROR (8)"), state.value.message)
        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Failed("offline", network = true))
        assertEquals(SettingsMessage.DriveFailed(CloudBackupFailure.Reason.NETWORK), state.value.message)

        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.STORAGE_FULL)
        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveFailed(CloudBackupFailure.Reason.STORAGE_FULL), state.value.message)
        assertNull(state.value.settings.lastDriveBackupAt)
    }

    @Test
    fun aRefusedTokenIsForgottenSoTheNextTryGetsAFreshOne() = runViewModelTest {
        g.setBackupPassword("long enough")
        val vm = vm()
        val state = collecting(vm.state)
        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.UNAUTHORIZED)
        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Token("stale"))
        assertEquals(SettingsMessage.DriveFailed(CloudBackupFailure.Reason.UNAUTHORIZED), state.value.message)
        assertEquals(listOf("stale"), g.driveTokens.forgotten)
        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.STORAGE_FULL)
        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Token("fine"))
        assertEquals(listOf("stale"), g.driveTokens.forgotten)
    }

    @Test
    fun automaticBackupFrequency() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(AutoBackupFrequency.DAILY, state.value.settings.autoDriveBackup)
        vm.onAutoBackupFrequencyChange(AutoBackupFrequency.WEEKLY)
        assertEquals(AutoBackupFrequency.WEEKLY, state.value.settings.autoDriveBackup)
        vm.onAutoBackupFrequencyChange(AutoBackupFrequency.OFF)
        assertEquals(AutoBackupFrequency.OFF, g.settingsRepo.settings.value.autoDriveBackup)
    }

    private val newBuild = AppUpdate(42, "1.0.0-test.42", "https://example.com/good-company.apk", sizeBytes = 1000)

    @Test
    fun updateCheckFindsANewerBuildOrSaysYoureCurrent() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(UpdateState.Idle, state.value.update)

        g.updateSource.newest = newBuild
        vm.onCheckForUpdate(installedVersionCode = 41)
        assertEquals(UpdateState.Available(newBuild), state.value.update)

        vm.onCheckForUpdate(installedVersionCode = 42)
        assertEquals(UpdateState.UpToDate, state.value.update)
        assertEquals(2, g.updateSource.calls)
    }

    @Test
    fun updateCheckFailureIsShownAndCanBeRetried() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        g.updateSource.failure = IllegalStateException("HTTP 503")
        vm.onCheckForUpdate(1)
        assertEquals(UpdateState.CheckFailed, state.value.update)

        g.updateSource.failure = null
        g.updateSource.newest = newBuild
        vm.onCheckForUpdate(1)
        assertEquals(UpdateState.Available(newBuild), state.value.update)
    }

    @Test
    fun downloadingTracksProgressAndIgnoresRepeatChecks() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        g.updateSource.newest = newBuild
        vm.onCheckForUpdate(1)

        vm.onUpdateStarted(newBuild)
        vm.onUpdateProgress(0.5f)
        assertEquals(UpdateState.Downloading(newBuild, 0.5f), state.value.update)
        vm.onUpdateProgress(7f)
        assertEquals(UpdateState.Downloading(newBuild, 1f), state.value.update)

        vm.onCheckForUpdate(1) // a download is running: no new check
        assertEquals(1, g.updateSource.calls)
        assertEquals(UpdateState.Downloading(newBuild, 1f), state.value.update)
    }

    @Test
    fun updateResultsGoBackToAvailableWithTheRightMessage() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)

        vm.onUpdateStarted(newBuild)
        vm.onUpdateResult(UpdateInstallResult.HANDED_OVER)
        assertEquals(UpdateState.Available(newBuild), state.value.update)
        assertNull(state.value.message)

        vm.onUpdateStarted(newBuild)
        vm.onUpdateResult(UpdateInstallResult.NEEDS_INSTALL_PERMISSION)
        assertEquals(SettingsMessage.UpdateNeedsPermission, state.value.message)
        vm.onMessageShown()

        vm.onUpdateStarted(newBuild)
        vm.onUpdateResult(UpdateInstallResult.FAILED)
        assertEquals(SettingsMessage.UpdateFailed, state.value.message)
        assertEquals(UpdateState.Available(newBuild), state.value.update)

        // A stray result when nothing is downloading changes nothing.
        vm.onUpdateResult(UpdateInstallResult.FAILED)
        assertEquals(UpdateState.Available(newBuild), state.value.update)
    }
}
