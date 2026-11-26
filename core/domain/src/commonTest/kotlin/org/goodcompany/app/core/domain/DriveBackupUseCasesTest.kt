package org.goodcompany.app.core.domain

import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.repository.CloudBackupFailure
import org.goodcompany.app.core.domain.repository.CloudSpace
import org.goodcompany.app.core.domain.usecase.BackupPasswordMissing
import org.goodcompany.app.core.domain.usecase.DRIVE_BACKUP_FILE_NAME
import org.goodcompany.app.core.domain.usecase.DRIVE_ENCRYPTED_BACKUP_FILE_NAME
import org.goodcompany.app.core.domain.usecase.OpenedBackup
import org.goodcompany.app.core.domain.usecase.RestoreResult
import org.goodcompany.app.core.testing.FakeCloudBackupStore
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveBackupUseCasesTest {
    private val g = TestGraph(listOf(friend("ana"), friend("rui")), settings = AppSettings(aiApiKey = "sk-secret"))

    private suspend fun withPassword() = g.setBackupPassword("correct horse")

    @Test
    fun noPasswordNoUpload() = runTest {
        assertIs<BackupPasswordMissing>(g.backupToDrive("t").exceptionOrNull())
        assertTrue(g.cloudStore.files.isEmpty())
        assertTrue(g.cloudStore.tokens.isEmpty())
    }

    @Test
    fun backupGoesEncryptedToOnePrivateFileWithoutTheAiKeyAndIsRemembered() = runTest {
        withPassword()
        val saved = g.backupToDrive("token-1").getOrThrow()
        g.backupToDrive("token-2").getOrThrow()
        val file = g.cloudStore.files.single()
        assertEquals(DRIVE_ENCRYPTED_BACKUP_FILE_NAME, file.name)
        assertEquals(CloudSpace.APP_DATA, file.space)
        assertEquals(saved.info.id, file.id)
        assertTrue(g.backupCrypto.isEncrypted(file.content))
        assertFalse(file.content.contains("sk-secret"))
        assertFalse(file.content.contains("Ana"))
        assertNull(saved.oldVisibleFile)
        assertEquals(g.clock.now(), g.settingsRepo.settings.value.lastDriveBackupAt)
    }

    @Test
    fun theOldVisibleFileIsOfferedUntilTheUserKeepsIt() = runTest {
        withPassword()
        g.cloudStore.files += FakeCloudBackupStore.File("old", DRIVE_BACKUP_FILE_NAME, "{}", null, CloudSpace.VISIBLE)
        assertEquals("old", g.backupToDrive("t").getOrThrow().oldVisibleFile?.id)
        g.keepOldDriveBackup()
        assertNull(g.backupToDrive("t").getOrThrow().oldVisibleFile)
        assertFalse(g.cloudStore.files.first { it.id == "old" }.trashed) // never deleted without asking
    }

    @Test
    fun trashingTheOldFileMovesItToTheBin() = runTest {
        withPassword()
        g.cloudStore.files += FakeCloudBackupStore.File("old", DRIVE_BACKUP_FILE_NAME, "{}", null, CloudSpace.VISIBLE)
        g.trashOldDriveBackup("t", "old").getOrThrow()
        assertTrue(g.cloudStore.files.first { it.id == "old" }.trashed)
        assertNull(g.backupToDrive("t").getOrThrow().oldVisibleFile)
    }

    @Test
    fun fetchReturnsTheEncryptedBackupWhichRestoresEverything() = runTest {
        assertNull(g.fetchDriveBackup("t").getOrThrow())
        withPassword()
        g.backupToDrive("t").getOrThrow()
        g.friendRepo.delete(FriendId("rui"))
        val backup = g.fetchDriveBackup("t").getOrThrow()!!
        assertFalse(backup.plain)
        assertIs<OpenedBackup.NeedsSecret>(g.openBackup(backup.content))
        val opened = assertIs<OpenedBackup.Opened>(g.openBackup(backup.content, "correct horse"))
        assertIs<RestoreResult.Restored>(g.restoreBackup(opened.json))
        assertEquals(2, g.friendRepo.friends.value.size)
        assertEquals("sk-secret", g.settingsRepo.settings.value.aiApiKey) // device key kept
    }

    @Test
    fun theOldPlainFileStaysRestorableWhenThereIsNoEncryptedOne() = runTest {
        val plain = g.exportBackup()
        g.cloudStore.files += FakeCloudBackupStore.File("old", DRIVE_BACKUP_FILE_NAME, plain, null, CloudSpace.VISIBLE)
        val backup = g.fetchDriveBackup("t").getOrThrow()!!
        assertTrue(backup.plain)
        val opened = assertIs<OpenedBackup.Plain>(g.openBackup(backup.content))
        assertIs<RestoreResult.Restored>(g.restoreBackup(opened.json))
    }

    @Test
    fun failuresAreTypedAndNothingIsRecorded() = runTest {
        withPassword()
        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.UNAUTHORIZED)
        val error = g.backupToDrive("t").exceptionOrNull()
        assertEquals(CloudBackupFailure.Reason.UNAUTHORIZED, (error as CloudBackupFailure).reason)
        assertNull(g.settingsRepo.settings.value.lastDriveBackupAt)
        assertEquals(CloudBackupFailure.Reason.UNAUTHORIZED, (g.fetchDriveBackup("t").exceptionOrNull() as CloudBackupFailure).reason)
        assertEquals(CloudBackupFailure.Reason.UNAUTHORIZED, (g.trashOldDriveBackup("t", "x").exceptionOrNull() as CloudBackupFailure).reason)
    }
}
