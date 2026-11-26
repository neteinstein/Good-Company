package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.usecase.ChangeBackupPasswordResult
import org.goodcompany.app.core.domain.usecase.OpenedBackup
import org.goodcompany.app.core.domain.usecase.SetBackupPasswordResult
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupEncryptionUseCasesTest {
    private val g = TestGraph(listOf(friend("ana")))

    @Test
    fun nothingIsExportedBeforeAPasswordIsSet() = runTest {
        assertNull(g.encryptBackup())
        assertFalse(g.observeBackupProtection().first().hasPassword)
    }

    @Test
    fun settingAPasswordKeepsTheKeyAndShowsTheRecoveryKeyOnce() = runTest {
        assertEquals(SetBackupPasswordResult.TooShort, g.setBackupPassword("short"))
        val done = assertIs<SetBackupPasswordResult.Done>(g.setBackupPassword("long enough"))
        assertTrue(done.recoveryKey.isNotBlank())
        assertTrue(g.observeBackupProtection().first().hasPassword)
        assertEquals(SetBackupPasswordResult.AlreadySet, g.setBackupPassword("another one"))
        val file = assertNotNull(g.encryptBackup())
        assertTrue(g.backupCrypto.isEncrypted(file))
        assertFalse(file.contains("Ana"))
    }

    @Test
    fun theFileOpensWithThePasswordOrTheRecoveryKeyOnly() = runTest {
        val recovery = (g.setBackupPassword("long enough") as SetBackupPasswordResult.Done).recoveryKey
        val file = g.encryptBackup()!!
        assertIs<OpenedBackup.NeedsSecret>(g.openBackup(file, "  "))
        assertIs<OpenedBackup.WrongSecret>(g.openBackup(file, "wrong password"))
        assertTrue(assertIs<OpenedBackup.Opened>(g.openBackup(file, "long enough")).json.contains("Ana"))
        assertTrue(assertIs<OpenedBackup.Opened>(g.openBackup(file, recovery)).json.contains("Ana"))
    }

    @Test
    fun changingThePasswordNeedsTheCurrentOneOrTheRecoveryKey() = runTest {
        assertEquals(ChangeBackupPasswordResult.NO_PASSWORD, g.changeBackupPassword("x", "new password"))
        val recovery = (g.setBackupPassword("first password") as SetBackupPasswordResult.Done).recoveryKey
        val before = g.encryptBackup()!!
        assertEquals(ChangeBackupPasswordResult.TOO_SHORT, g.changeBackupPassword("first password", "short"))
        assertEquals(ChangeBackupPasswordResult.WRONG_SECRET, g.changeBackupPassword("nope", "second password"))
        assertEquals(ChangeBackupPasswordResult.CHANGED, g.changeBackupPassword("first password", "second password"))
        assertEquals(ChangeBackupPasswordResult.CHANGED, g.changeBackupPassword(recovery, "third password"))
        val after = g.encryptBackup()!!
        assertIs<OpenedBackup.Opened>(g.openBackup(after, "third password"))
        assertIs<OpenedBackup.WrongSecret>(g.openBackup(after, "first password"))
        assertIs<OpenedBackup.Opened>(g.openBackup(after, recovery))
        // Older backups keep opening with the password they were made with.
        assertIs<OpenedBackup.Opened>(g.openBackup(before, "first password"))
    }

    @Test
    fun aPlainOldBackupNeedsNoPassword() = runTest {
        val plain = g.exportBackup()
        assertEquals(OpenedBackup.Plain(plain), g.openBackup(plain, null))
    }

    @Test
    fun aDeviceWithoutAKeyAdoptsTheRestoredOneAndAnotherKeepsItsOwn() = runTest {
        val other = TestGraph(listOf(friend("rui")))
        other.setBackupPassword("other device")
        val opened = other.openBackup(other.encryptBackup()!!, "other device") as OpenedBackup.Opened

        g.adoptBackupKey(opened.key)
        assertEquals(opened.key, g.backupKeyRepo.state.value.key)
        assertIs<OpenedBackup.Opened>(g.openBackup(g.encryptBackup()!!, "other device"))

        val mine = TestGraph()
        mine.setBackupPassword("my own password")
        val own = mine.backupKeyRepo.state.value.key
        mine.adoptBackupKey(opened.key)
        assertEquals(own, mine.backupKeyRepo.state.value.key)
    }
}
