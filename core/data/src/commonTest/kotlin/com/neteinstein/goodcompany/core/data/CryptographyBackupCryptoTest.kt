package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.crypto.CryptographyBackupCrypto
import com.neteinstein.goodcompany.core.data.crypto.RecoveryKey
import com.neteinstein.goodcompany.core.data.repository.KeyValueBackupKeyRepository
import com.neteinstein.goodcompany.core.domain.model.BackupKeySlot
import com.neteinstein.goodcompany.core.domain.model.BackupProtection
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D4 with the real cipher (JDK provider on the JVM); few PBKDF2 rounds so the tests stay fast. */
class CryptographyBackupCryptoTest {
    private val crypto = CryptographyBackupCrypto(passwordIterations = 1_000)
    private val document = """{"format":"good-company","friends":[{"name":"Ana Sousa"}]}"""

    @Test
    fun aFileOpensWithThePasswordOrTheRecoveryKeyAndHidesTheContent() = runTest {
        val made = crypto.newKey("correct horse")
        val file = crypto.encrypt(made.key, document)
        assertTrue(crypto.isEncrypted(file))
        assertFalse(crypto.isEncrypted(document))
        assertFalse(crypto.isEncrypted("not json"))
        assertFalse(file.contains("Ana"))
        assertEquals(document, crypto.decrypt(file, "correct horse")?.json)
        assertEquals(document, crypto.decrypt(file, made.recoveryKey)?.json)
        // The recovery key typed in lower case, without dashes, and with O for 0 still works.
        val sloppy = made.recoveryKey.lowercase().replace("-", " ").replace('0', 'o')
        assertEquals(document, crypto.decrypt(file, sloppy)?.json)
        assertNull(crypto.decrypt(file, "wrong"))
        assertNull(crypto.decrypt(document, "correct horse"))
    }

    @Test
    fun theOpenedKeyIsTheOneTheFileWasMadeWith() = runTest {
        val made = crypto.newKey("correct horse")
        val opened = assertNotNull(crypto.decrypt(crypto.encrypt(made.key, document), made.recoveryKey))
        assertEquals(made.key, opened.key)
        assertTrue(crypto.unlocks(opened.key, "correct horse"))
    }

    @Test
    fun eachBackupHasItsOwnIvAndTheParametersAreInTheFile() = runTest {
        val key = crypto.newKey("correct horse").key
        val a = crypto.encrypt(key, document)
        val b = crypto.encrypt(key, document)
        assertNotEquals(a, b)
        val json = Json.parseToJsonElement(a).jsonObject
        assertEquals(CryptographyBackupCrypto.FORMAT, json["format"]?.jsonPrimitive?.content)
        assertEquals("PBKDF2-HMAC-SHA256", json["kdf"]?.jsonPrimitive?.content)
        assertEquals("AES-256-CBC+HMAC-SHA256", json["cipher"]?.jsonPrimitive?.content)
        assertEquals(1_000, key.password?.iterations)
        assertEquals(CryptographyBackupCrypto.RECOVERY_ITERATIONS, key.recovery?.iterations)
        assertEquals(16, Base64.decode(key.password!!.salt).size)
    }

    @Test
    fun anEditedFileDoesNotOpen() = runTest {
        val key = crypto.newKey("correct horse").key
        val json = Json.parseToJsonElement(crypto.encrypt(key, document)).jsonObject
        val data = Base64.decode(json["data"]!!.jsonPrimitive.content)
        data[data.size - 1] = (data.last().toInt() xor 1).toByte()
        val edited = JsonObject(json + ("data" to JsonPrimitive(Base64.encode(data))))
        assertNull(crypto.decrypt(edited.toString(), "correct horse"))
    }

    @Test
    fun changingThePasswordKeepsTheDataKeyAndTheRecoveryKey() = runTest {
        val made = crypto.newKey("first password")
        val before = crypto.encrypt(made.key, document)
        val changed = crypto.changePassword(made.key, "second password")
        assertEquals(made.key.dataKey, changed.dataKey)
        assertEquals(made.key.recovery, changed.recovery)
        assertEquals(listOf(BackupKeySlot.Kind.PASSWORD, BackupKeySlot.Kind.RECOVERY), changed.slots.map { it.kind })
        assertTrue(crypto.unlocks(changed, "second password"))
        assertFalse(crypto.unlocks(changed, "first password"))
        assertTrue(crypto.unlocks(changed, made.recoveryKey))
        val after = crypto.encrypt(changed, document)
        assertEquals(document, crypto.decrypt(after, "second password")?.json)
        assertNull(crypto.decrypt(after, "first password"))
        assertEquals(document, crypto.decrypt(before, "first password")?.json) // older files keep their old password
    }

    @Test
    fun recoveryKeysAre32CrockfordCharactersInGroupsOfFour() {
        val key = RecoveryKey.generate()
        assertEquals(8, key.split("-").size)
        assertTrue(key.split("-").all { it.length == 4 })
        assertEquals(key.replace("-", ""), RecoveryKey.normalize(key))
        assertNull(RecoveryKey.normalize("too short"))
        assertNull(RecoveryKey.normalize("UUUU-UUUU-UUUU-UUUU-UUUU-UUUU-UUUU-UUUU")) // U isn't in the alphabet
        assertNotEquals(key, RecoveryKey.generate())
    }

    @Test
    fun theKeyIsKeptInKeyValueStorageOutsideTheDatabase() = runTest {
        val settings = MapSettings()
        val store = KeyValueBackupKeyRepository(settings)
        assertEquals(BackupProtection(), store.current())
        val key = crypto.newKey("correct horse").key
        store.update { it.copy(key = key, keepOldDriveFile = true) }
        assertTrue(settings.hasKey(KeyValueBackupKeyRepository.KEY))
        assertEquals(BackupProtection(key, keepOldDriveFile = true), KeyValueBackupKeyRepository(settings).current())
    }
}
