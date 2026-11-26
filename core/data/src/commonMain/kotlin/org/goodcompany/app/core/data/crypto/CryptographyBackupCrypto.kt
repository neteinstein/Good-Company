package org.goodcompany.app.core.data.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.goodcompany.app.core.domain.model.BackupKey
import org.goodcompany.app.core.domain.model.BackupKeySlot
import org.goodcompany.app.core.domain.model.NewBackupKey
import org.goodcompany.app.core.domain.repository.BackupCrypto
import org.goodcompany.app.core.domain.repository.DecryptedBackup
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/** The platform's own crypto library: JDK (Android), CommonCrypto (iOS), WebCrypto (web). */
internal expect fun backupCryptoProvider(): CryptographyProvider

/**
 * D4 (issue #165): encrypted backup files, with cryptography-kotlin over each platform's own crypto.
 *
 * - A random 256-bit **data key** encrypts the backup document.
 * - It is stored in the file twice, wrapped by a key derived with **PBKDF2-HMAC-SHA256** from the password
 *   ([passwordIterations], 600 000 by default as OWASP recommends; 16-byte random salt) and from the recovery key
 *   (160 random bits, so [RECOVERY_ITERATIONS] is enough).
 * - Every encryption is **AES-256-CBC** (random IV, PKCS#7) followed by **HMAC-SHA256** over a label and the IV and
 *   ciphertext (encrypt-then-MAC). The AES and HMAC keys are separate keys made from one secret with HMAC-SHA256.
 *   AES-GCM would be one step, but iOS's CommonCrypto has no GCM and CryptoKit needs Swift linking.
 *
 * The file is JSON (`format` = [FORMAT]), so a restore can tell it from an old plain backup.
 */
class CryptographyBackupCrypto(
    private val passwordIterations: Int = PASSWORD_ITERATIONS,
    private val provider: CryptographyProvider = backupCryptoProvider(),
    private val random: CryptographyRandom = CryptographyRandom,
) : BackupCrypto {
    override suspend fun newKey(password: String): NewBackupKey {
        val dataKey = random.nextBytes(KEY_BYTES)
        val recovery = RecoveryKey.generate(random)
        val key = BackupKey(
            dataKey = dataKey.b64(),
            slots = listOf(
                slot(BackupKeySlot.Kind.PASSWORD, password, passwordIterations, dataKey),
                slot(BackupKeySlot.Kind.RECOVERY, RecoveryKey.normalize(recovery)!!, RECOVERY_ITERATIONS, dataKey),
            ),
        )
        return NewBackupKey(key, recovery)
    }

    override suspend fun changePassword(key: BackupKey, newPassword: String): BackupKey {
        val dataKey = key.dataKey.unb64()
        val slot = slot(BackupKeySlot.Kind.PASSWORD, newPassword, passwordIterations, dataKey)
        return key.copy(slots = listOf(slot) + key.slots.filter { it.kind != BackupKeySlot.Kind.PASSWORD })
    }

    override suspend fun unlocks(key: BackupKey, secret: String): Boolean =
        unwrap(key.slots, secret)?.contentEquals(key.dataKey.unb64()) == true

    override suspend fun encrypt(key: BackupKey, json: String): String {
        val box = seal(key.dataKey.unb64(), DATA_LABEL, json.encodeToByteArray())
        val file = EncryptedBackupFile(slots = key.slots, data = box.data, mac = box.mac)
        return FileJson.encodeToString(EncryptedBackupFile.serializer(), file)
    }

    override fun isEncrypted(raw: String): Boolean = runCatching {
        Json.parseToJsonElement(raw).jsonObject["format"]?.jsonPrimitive?.contentOrNull == FORMAT
    }.getOrDefault(false)

    override suspend fun decrypt(raw: String, secret: String): DecryptedBackup? {
        val file = runCatching { FileJson.decodeFromString(EncryptedBackupFile.serializer(), raw) }.getOrNull()
            ?.takeIf { it.format == FORMAT && it.version <= VERSION }
            ?: return null
        val dataKey = unwrap(file.slots, secret) ?: return null
        val json = open(dataKey, DATA_LABEL, Box(file.data, file.mac))?.decodeToString() ?: return null
        return DecryptedBackup(json, BackupKey(dataKey.b64(), file.slots))
    }

    // --- Slots -----------------------------------------------------------------------------------------------------

    private suspend fun slot(kind: BackupKeySlot.Kind, secret: String, iterations: Int, dataKey: ByteArray): BackupKeySlot {
        val salt = random.nextBytes(SALT_BYTES)
        val box = seal(derive(secret, salt, iterations), slotLabel(kind), dataKey)
        return BackupKeySlot(kind, iterations, salt.b64(), box.data, box.mac)
    }

    /** The data key, from the first slot [secret] opens: the password as typed, or the recovery key in any spacing. */
    private suspend fun unwrap(slots: List<BackupKeySlot>, secret: String): ByteArray? {
        for (slot in slots) {
            val input = when (slot.kind) {
                BackupKeySlot.Kind.PASSWORD -> secret
                BackupKeySlot.Kind.RECOVERY -> RecoveryKey.normalize(secret) ?: continue
            }
            if (slot.iterations !in 1..MAX_ITERATIONS) continue
            val salt = slot.salt.unb64OrNull() ?: continue
            open(derive(input, salt, slot.iterations), slotLabel(slot.kind), Box(slot.wrapped, slot.mac))
                ?.takeIf { it.size == KEY_BYTES }
                ?.let { return it }
        }
        return null
    }

    private suspend fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray = withContext(Dispatchers.Default) {
        provider.get(PBKDF2)
            .secretDerivation(SHA256, iterations, KEY_BYTES.bytes, salt)
            .deriveSecretToByteArray(secret.encodeToByteArray())
    }

    // --- Encrypt-then-MAC --------------------------------------------------------------------------------------------

    private class Box(val data: String, val mac: String)

    private suspend fun seal(secret: ByteArray, label: String, plain: ByteArray): Box {
        val (encKey, macKey) = subkeys(secret)
        val cipher = provider.get(AES.CBC).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, encKey).cipher(padding = true)
        val data = cipher.encrypt(plain) // random IV first
        return Box(data.b64(), mac(macKey, label, data).b64())
    }

    /** The plain bytes, or null when anything doesn't check out (wrong key, edited file). */
    private suspend fun open(secret: ByteArray, label: String, box: Box): ByteArray? = try {
        val data = box.data.unb64()
        val (encKey, macKey) = subkeys(secret)
        if (!constantTimeEquals(mac(macKey, label, data), box.mac.unb64())) {
            null
        } else {
            provider.get(AES.CBC).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, encKey).cipher(padding = true).decrypt(data)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun subkeys(secret: ByteArray): Pair<ByteArray, ByteArray> =
        hmac(secret, ENC_INFO.encodeToByteArray()) to hmac(secret, MAC_INFO.encodeToByteArray())

    private suspend fun mac(macKey: ByteArray, label: String, data: ByteArray): ByteArray = hmac(macKey, label.encodeToByteArray() + data)

    private suspend fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        provider.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, key).signatureGenerator().generateSignature(data)

    @Serializable
    internal data class EncryptedBackupFile(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val kdf: String = KDF,
        val cipher: String = CIPHER,
        val slots: List<BackupKeySlot>,
        /** The backup document, AES-256-CBC, IV first. */
        val data: String,
        val mac: String,
    )

    companion object {
        const val FORMAT = "good-company-encrypted-backup"
        const val VERSION = 1
        const val KDF = "PBKDF2-HMAC-SHA256"
        const val CIPHER = "AES-256-CBC+HMAC-SHA256"
        const val PASSWORD_ITERATIONS = 600_000
        const val RECOVERY_ITERATIONS = 10_000

        /** A file asking for more than this is refused rather than freezing the phone. */
        private const val MAX_ITERATIONS = 10_000_000
        private const val KEY_BYTES = 32
        private const val SALT_BYTES = 16
        private const val ENC_INFO = "good-company/enc"
        private const val MAC_INFO = "good-company/mac"
        private const val DATA_LABEL = "good-company/data/v1"

        private fun slotLabel(kind: BackupKeySlot.Kind) = "good-company/slot/${kind.name}"

        private val FileJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private fun ByteArray.b64() = Base64.encode(this)

        private fun String.unb64() = Base64.decode(this)

        private fun String.unb64OrNull() = runCatching { unb64() }.getOrNull()

        private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
            return diff == 0
        }
    }
}

/**
 * The recovery key: 160 random bits written as 32 Crockford Base32 characters in groups of four
 * (`7K3M-…`). Typing it back ignores case, spaces and dashes, and reads O as 0 and I/L as 1.
 */
object RecoveryKey {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val BYTES = 20
    private const val LENGTH = 32

    fun generate(random: CryptographyRandom = CryptographyRandom): String = format(encode(random.nextBytes(BYTES)))

    /** The 32 characters of a recovery key as typed in any spacing, or null when [text] can't be one. */
    fun normalize(text: String): String? {
        val chars = text.uppercase().filterNot { it == '-' || it.isWhitespace() }.map {
            when (it) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> it
            }
        }
        if (chars.size != LENGTH || chars.any { it !in ALPHABET }) return null
        return chars.joinToString("")
    }

    private fun format(chars: String) = chars.chunked(4).joinToString("-")

    private fun encode(bytes: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 31])
    }
}
