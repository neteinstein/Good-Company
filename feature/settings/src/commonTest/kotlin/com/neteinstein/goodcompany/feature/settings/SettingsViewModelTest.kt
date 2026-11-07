package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.core.ui.platform.DriveAuthResult
import com.neteinstein.goodcompany.feature.settings.ui.screens.DriveAction
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
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
        g.exportBackup,
        g.restoreBackup,
        g.backupToDrive,
        g.fetchDriveBackup,
        g.clock,
    )

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
    fun preferencesUpdate() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onThemeChange(ThemeMode.DARK)
        vm.onDynamicColorChange(false)
        vm.onWeeklyIdeasChange(false)
        vm.onHomeAreaChange("Porto")
        vm.onReminderHourChange(42)
        vm.onCircleDaysChange(Circle.INNER, 14)
        vm.onCircleDaysChange(Circle.CLOSE, 60)
        vm.onCircleDaysChange(Circle.GOOD, 0)
        vm.onNotificationPermissionResult(false)
        val s = state.value.settings
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(false, s.dynamicColor)
        assertEquals(false, s.weeklyIdeasEnabled)
        assertEquals("Porto", s.homeArea)
        assertEquals(23, s.reminderHour)
        assertEquals(14, s.innerCircleDays)
        assertEquals(60, s.closeCircleDays)
        assertEquals(1, s.goodFriendsDays)
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
    fun nudgePrivacyAndLockSettings() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onWeeklyDigestChange(false)
        vm.onDigestDayChange(9)
        vm.onPrivateNotificationsChange(true)
        vm.onShowWarmthChange(false)
        val s = state.value.settings
        assertFalse(s.weeklyDigest)
        assertEquals(7, s.digestDay)
        assertTrue(s.privateNotifications)
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
    fun exportAndRestoreBackup() = runViewModelTest {
        g.addFriend("Ana")
        val vm = vm()
        val state = collecting(vm.state)
        vm.onExportBackup()
        val file = state.value.export!!
        assertEquals("good-company-2026-03-10.json", file.name)
        vm.onExportHandled(saved = true)
        assertNull(state.value.export)
        assertEquals(SettingsMessage.BackupSaved, state.value.message)

        vm.onBackupPicked("nope")
        assertEquals("nope", state.value.pendingRestore)
        vm.onConfirmRestore()
        assertNull(state.value.pendingRestore)
        assertEquals(SettingsMessage.BackupInvalid, state.value.message)

        vm.onBackupPicked(file.content)
        vm.onCancelRestore()
        assertNull(state.value.pendingRestore)
        vm.onBackupPicked(file.content)
        vm.onConfirmRestore()
        assertEquals(SettingsMessage.BackupRestored(1), state.value.message)
    }

    @Test
    fun driveBackupAndRestoreAfterTheTokenArrives() = runViewModelTest {
        g.friendRepo.upsert(friend("ana"))
        g.cloudStore.now = g.clock.now()
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDriveRequested(DriveAction.BACKUP)
        assertTrue(state.value.driveBusy)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveBackedUp, state.value.message)
        assertEquals(g.clock.now(), state.value.settings.lastDriveBackupAt)
        assertFalse(state.value.driveBusy)

        g.friendRepo.delete(FriendId("ana"))
        vm.onDriveRequested(DriveAction.RESTORE)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(g.clock.now(), state.value.pendingRestoreFrom)
        vm.onConfirmRestore()
        assertEquals(SettingsMessage.BackupRestored(1), state.value.message)
        assertNull(state.value.pendingRestoreFrom)
        assertEquals(listOf("Ana"), g.friendRepo.friends.value.map { it.name })
    }

    @Test
    fun driveOutcomesWithoutData() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        vm.onDriveRequested(DriveAction.RESTORE)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveNoBackup, state.value.message)

        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Cancelled)
        assertFalse(state.value.driveBusy)
        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.NotConfigured)
        assertEquals(SettingsMessage.DriveNotConfigured, state.value.message)

        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.STORAGE_FULL)
        vm.onDriveRequested(DriveAction.BACKUP)
        vm.onDriveAuthResult(DriveAuthResult.Token("tok"))
        assertEquals(SettingsMessage.DriveFailed(CloudBackupFailure.Reason.STORAGE_FULL), state.value.message)
        assertNull(state.value.settings.lastDriveBackupAt)
    }
}
