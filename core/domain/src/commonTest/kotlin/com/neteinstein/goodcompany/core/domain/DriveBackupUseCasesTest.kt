package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupFailure
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_BACKUP_FILE_NAME
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriveBackupUseCasesTest {
    private val g = TestGraph(listOf(friend("ana"), friend("rui")), settings = AppSettings(aiApiKey = "sk-secret"))

    @Test
    fun backupGoesToOneDriveFileWithoutTheAiKeyAndIsRemembered() = runTest {
        val info = g.backupToDrive("token-1").getOrThrow()
        g.backupToDrive("token-2").getOrThrow()
        val file = g.cloudStore.files.single()
        assertEquals(DRIVE_BACKUP_FILE_NAME, file.name)
        assertEquals(info.id, file.id)
        assertFalse(file.content.contains("sk-secret"))
        assertTrue(file.content.contains("Ana"))
        assertEquals(listOf("token-1", "token-2"), g.cloudStore.tokens)
        assertEquals(g.clock.now(), g.settingsRepo.settings.value.lastDriveBackupAt)
    }

    @Test
    fun fetchReturnsTheLatestBackupWhichRestoresEverything() = runTest {
        assertNull(g.fetchDriveBackup("t").getOrThrow())
        g.backupToDrive("t").getOrThrow()
        g.friendRepo.delete(FriendId("rui"))
        val backup = g.fetchDriveBackup("t").getOrThrow()!!
        val result = g.restoreBackup(backup.content)
        assertIs<RestoreResult.Restored>(result)
        assertEquals(2, g.friendRepo.friends.value.size)
        assertEquals("sk-secret", g.settingsRepo.settings.value.aiApiKey) // device key kept
    }

    @Test
    fun failuresAreTypedAndNothingIsRecorded() = runTest {
        g.cloudStore.failure = CloudBackupFailure(CloudBackupFailure.Reason.UNAUTHORIZED)
        val error = g.backupToDrive("t").exceptionOrNull()
        assertEquals(CloudBackupFailure.Reason.UNAUTHORIZED, (error as CloudBackupFailure).reason)
        assertNull(g.settingsRepo.settings.value.lastDriveBackupAt)
        assertEquals(CloudBackupFailure.Reason.UNAUTHORIZED, (g.fetchDriveBackup("t").exceptionOrNull() as CloudBackupFailure).reason)
    }
}
